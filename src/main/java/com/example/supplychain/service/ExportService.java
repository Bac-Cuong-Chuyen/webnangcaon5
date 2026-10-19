package com.example.supplychain.service;
import java.time.LocalDate;
import com.example.supplychain.dto.ExportRequest;
import com.example.supplychain.entity.ExportReceipt;
import com.example.supplychain.entity.ExportDetail;
import com.example.supplychain.exception.ResourceNotFoundException;
import com.example.supplychain.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
@Service
@RequiredArgsConstructor
public class ExportService {
    private final ExportReceiptRepository receipts;
    private final ExportDetailRepository details;
    private final ProductRepository products;
    private final WarehouseRepository warehouses;
    @Transactional
    public String createExport(ExportRequest request) {
        ReceiptAccess.requireCreate();
        if (request.getQuantity() == null || request.getQuantity() <= 0)
            throw new IllegalArgumentException("So luong phai lon hon 0");
        if (request.getProductId() == null || request.getWarehouseId() == null)
            throw new IllegalArgumentException("productId va warehouseId la bat buoc");
        if (!products.existsById(request.getProductId())) throw new ResourceNotFoundException("Khong tim thay san pham");
        if (!warehouses.existsById(request.getWarehouseId())) throw new ResourceNotFoundException("Khong tim thay kho");
        ExportReceipt receipt = new ExportReceipt();
        receipt.setReceiptCode(request.getReceiptCode());
        receipt.setReceiptDate(LocalDate.now());
        receipt.setWarehouseId(request.getWarehouseId());
        receipt.setCreatedBy(ReceiptAccess.actor());
        receipt.setStatus("DRAFT");
        receipts.save(receipt);
        ExportDetail detail = new ExportDetail();
        detail.setReceiptId(receipt.getId()); detail.setProductId(request.getProductId());
        detail.setQuantity(request.getQuantity()); details.save(detail);
        return "Da lap phieu DRAFT. id=" + receipt.getId();
    }
}
