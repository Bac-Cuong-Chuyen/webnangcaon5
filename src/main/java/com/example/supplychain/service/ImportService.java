package com.example.supplychain.service;

import java.time.LocalDate;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.supplychain.dto.ImportRequest;
import com.example.supplychain.entity.ImportDetail;
import com.example.supplychain.entity.ImportReceipt;
import com.example.supplychain.entity.Inventory;
import com.example.supplychain.exception.ResourceNotFoundException;
import com.example.supplychain.repository.ImportDetailRepository;
import com.example.supplychain.repository.ImportReceiptRepository;
import com.example.supplychain.repository.InventoryRepository;
import com.example.supplychain.repository.ProductRepository;
import com.example.supplychain.repository.SupplierRepository;
import com.example.supplychain.repository.WarehouseRepository;

@Service
public class ImportService {

    private final ImportReceiptRepository importReceiptRepository;
    private final ImportDetailRepository importDetailRepository;
    private final InventoryRepository inventoryRepository;
    private final ProductRepository productRepository;
    private final WarehouseRepository warehouseRepository;
    private final SupplierRepository supplierRepository;

    public ImportService(
            ImportReceiptRepository importReceiptRepository,
            ImportDetailRepository importDetailRepository,
            InventoryRepository inventoryRepository,
            ProductRepository productRepository,
            WarehouseRepository warehouseRepository,
            SupplierRepository supplierRepository) {

        this.importReceiptRepository = importReceiptRepository;
        this.importDetailRepository = importDetailRepository;
        this.inventoryRepository = inventoryRepository;
        this.productRepository = productRepository;
        this.warehouseRepository = warehouseRepository;
        this.supplierRepository = supplierRepository;
    }

    @Transactional
    public String createImport(ImportRequest request) {
        return processImport(request);
    }

    @Transactional
    public String processImport(ImportRequest request) {

        if (request.getQuantity() == null || request.getQuantity() <= 0) {
            throw new IllegalArgumentException(
                    "So luong nhap phai lon hon 0");
        }

        if (request.getProductId() == null
                || request.getWarehouseId() == null) {
            throw new IllegalArgumentException(
                    "productId va warehouseId la bat buoc");
        }

        productRepository.findById(request.getProductId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Khong tim thay san pham: "
                                + request.getProductId()));

        warehouseRepository.findById(request.getWarehouseId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Khong tim thay kho: "
                                + request.getWarehouseId()));

        if (request.getSupplierId() != null
                && !supplierRepository.existsById(request.getSupplierId())) {
            throw new ResourceNotFoundException(
                    "Khong tim thay nha cung cap: "
                            + request.getSupplierId());
        }

        ImportReceipt receipt = new ImportReceipt();
        receipt.setReceiptCode(request.getReceiptCode());
        receipt.setReceiptDate(LocalDate.now());
        receipt.setStatus("COMPLETED");

        ImportReceipt savedReceipt = importReceiptRepository.save(receipt);

        ImportDetail detail = new ImportDetail();
        detail.setReceiptId(savedReceipt.getId());
        detail.setProductId(request.getProductId());
        detail.setQuantity(request.getQuantity());
        importDetailRepository.save(detail);

        Inventory inventory = inventoryRepository
                .findByWarehouseIdAndProductId(
                        request.getWarehouseId(),
                        request.getProductId())
                .orElse(null);

        if (inventory == null) {
            inventory = new Inventory();
            inventory.setWarehouseId(request.getWarehouseId());
            inventory.setProductId(request.getProductId());
            inventory.setQuantity(0);
            inventory.setMinimumQuantity(0);
        }

        if (inventory.getQuantity() == null || inventory.getQuantity() < 0) {
            throw new IllegalArgumentException(
                    "Ton kho khong hop le");
        }

        int updatedQuantity;
        try {
            updatedQuantity = Math.addExact(
                    inventory.getQuantity(),
                    request.getQuantity());
        } catch (ArithmeticException ex) {
            throw new IllegalArgumentException(
                    "So luong ton kho vuot gioi han cho phep");
        }

        inventory.setQuantity(updatedQuantity);
        inventoryRepository.save(inventory);

        return "Nhap kho thanh cong. Ton kho hien tai: "
                + updatedQuantity;
    }
}