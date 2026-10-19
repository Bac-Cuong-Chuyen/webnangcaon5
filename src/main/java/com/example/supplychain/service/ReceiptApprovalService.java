package com.example.supplychain.service;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import com.example.supplychain.entity.*;
import com.example.supplychain.repository.*;
import com.example.supplychain.exception.BusinessConflictException;
import com.example.supplychain.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ReceiptApprovalService {
    private final ImportReceiptRepository imports;
    private final ExportReceiptRepository exports;
    private final ImportDetailRepository importDetails;
    private final ExportDetailRepository exportDetails;
    private final ProductRepository products;
    private final WarehouseRepository warehouses;
    private final InventoryRepository inventory;
    private final AuditLogService audit;

    @Transactional
    public ImportReceipt approveImport(Long id) {
        ReceiptAccess.requireManager();
        ImportReceipt receipt = imports.findByIdForUpdate(id)
            .orElseThrow(() -> new ResourceNotFoundException("Khong tim thay phieu nhap"));
        requireDraft(receipt.getStatus());
        ReceiptAccess.requireDifferentApprover(receipt.getCreatedBy());
        List<Line> lines = importDetails.findByReceiptIdOrderByProductIdAsc(id).stream()
            .map(row -> new Line(row.getProductId(), row.getQuantity())).toList();
        applyStock(receipt.getWarehouseId(), lines, true);
        receipt.setStatus("APPROVED");
        receipt.setApprovedBy(ReceiptAccess.actor());
        imports.save(receipt);
        audit.record(ReceiptAccess.actor(), "APPROVE_IMPORT", "ImportReceipt:"+id);
        return receipt;
    }

    @Transactional
    public ExportReceipt approveExport(Long id) {
        ReceiptAccess.requireManager();
        ExportReceipt receipt = exports.findByIdForUpdate(id)
            .orElseThrow(() -> new ResourceNotFoundException("Khong tim thay phieu xuat"));
        requireDraft(receipt.getStatus());
        ReceiptAccess.requireDifferentApprover(receipt.getCreatedBy());
        List<Line> lines = exportDetails.findByReceiptIdOrderByProductIdAsc(id).stream()
            .map(row -> new Line(row.getProductId(), row.getQuantity())).toList();
        applyStock(receipt.getWarehouseId(), lines, false);
        receipt.setStatus("APPROVED");
        receipt.setApprovedBy(ReceiptAccess.actor());
        exports.save(receipt);
        audit.record(ReceiptAccess.actor(), "APPROVE_EXPORT", "ExportReceipt:"+id);
        return receipt;
    }

    private void requireDraft(String status) {
        if (!"DRAFT".equals(status)) throw new BusinessConflictException("Chi duoc duyet phieu DRAFT mot lan");
    }

    private void applyStock(Long warehouseId, List<Line> lines, boolean incoming) {
        if (warehouseId == null) throw new IllegalArgumentException("Phieu chua co warehouseId");
        if (lines.isEmpty()) throw new IllegalArgumentException("Phieu chua co chi tiet");
        warehouses.findByIdForUpdate(warehouseId)
            .orElseThrow(() -> new ResourceNotFoundException("Khong tim thay kho"));
        Map<Long,Integer> quantities = new TreeMap<>();
        for (Line line : lines) {
            if (line.productId() == null || line.quantity() == null || line.quantity() <= 0)
                throw new IllegalArgumentException("Chi tiet phieu khong hop le");
            if (!products.existsById(line.productId()))
                throw new ResourceNotFoundException("Khong tim thay san pham");
            try { quantities.merge(line.productId(), line.quantity(), Math::addExact); }
            catch (ArithmeticException ex) { throw new BusinessConflictException("Tong so luong vuot gioi han"); }
        }
        for (var entry : quantities.entrySet()) {
            Inventory row = inventory.findForUpdate(warehouseId, entry.getKey()).orElse(null);
            if (row == null) {
                if (!incoming) throw new BusinessConflictException("San pham chua co trong kho");
                row = new Inventory(); row.setWarehouseId(warehouseId); row.setProductId(entry.getKey());
                row.setQuantity(0); row.setMinimumQuantity(0);
            }
            if (row.getQuantity() == null || row.getQuantity() < 0)
                throw new BusinessConflictException("Ton kho khong hop le");
            if (!incoming && row.getQuantity() < entry.getValue())
                throw new BusinessConflictException("Ton kho khong du de xuat");
            int updated;
            try { updated = incoming ? Math.addExact(row.getQuantity(), entry.getValue())
                : Math.subtractExact(row.getQuantity(), entry.getValue()); }
            catch (ArithmeticException ex) { throw new BusinessConflictException("Ton kho vuot gioi han"); }
            row.setQuantity(updated);
            inventory.save(row);
        }
    }
    private record Line(Long productId, Integer quantity) {}
}
