package com.example.supplychain.service;
import java.time.LocalDate;
import com.example.supplychain.dto.ImportRequest;
import com.example.supplychain.entity.ImportReceipt;
import com.example.supplychain.entity.ImportDetail;
import com.example.supplychain.exception.ResourceNotFoundException;
import com.example.supplychain.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
@Service
@RequiredArgsConstructor
public class ImportService {
    private final ImportReceiptRepository receipts;
    private final ImportDetailRepository details;
    private final ProductRepository products;
    private final WarehouseRepository warehouses;
    private final com.example.supplychain.repository.SupplierRepository suppliers;
    @Transactional
    public String createImport(ImportRequest request) {
        ReceiptAccess.requireCreate();
        if (request.getQuantity() == null || request.getQuantity() <= 0)
            throw new IllegalArgumentException("So luong phai lon hon 0");
        if (request.getProductId() == null || request.getWarehouseId() == null)
            throw new IllegalArgumentException("productId va warehouseId la bat buoc");
        if (!products.existsById(request.getProductId())) throw new ResourceNotFoundException("Khong tim thay san pham");
        if (!warehouses.existsById(request.getWarehouseId())) throw new ResourceNotFoundException("Khong tim thay kho");
        if (request.getSupplierId() != null && !suppliers.existsById(request.getSupplierId()))
            throw new ResourceNotFoundException("Khong tim thay nha cung cap");
        ImportReceipt receipt = new ImportReceipt();
        receipt.setReceiptCode(request.getReceiptCode());
        receipt.setReceiptDate(LocalDate.now());
        receipt.setWarehouseId(request.getWarehouseId());
        receipt.setCreatedBy(ReceiptAccess.actor());
        receipt.setStatus("DRAFT");
        receipts.save(receipt);
        ImportDetail detail = new ImportDetail();
        detail.setReceiptId(receipt.getId()); detail.setProductId(request.getProductId());
        detail.setQuantity(request.getQuantity()); details.save(detail);
        return "Da lap phieu DRAFT. id=" + receipt.getId();
    }
    @Transactional
    public String processImport(ImportRequest request) { return createImport(request); }
}
