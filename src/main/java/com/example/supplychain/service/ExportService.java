package com.example.supplychain.service;

import java.time.LocalDate;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.supplychain.dto.ExportRequest;
import com.example.supplychain.entity.ExportDetail;
import com.example.supplychain.entity.ExportReceipt;
import com.example.supplychain.entity.Inventory;
import com.example.supplychain.exception.BusinessConflictException;
import com.example.supplychain.exception.ResourceNotFoundException;
import com.example.supplychain.repository.ExportDetailRepository;
import com.example.supplychain.repository.ExportReceiptRepository;
import com.example.supplychain.repository.InventoryRepository;
import com.example.supplychain.repository.ProductRepository;
import com.example.supplychain.repository.WarehouseRepository;

@Service
public class ExportService {

    private final ExportReceiptRepository exportReceiptRepository;
    private final ExportDetailRepository exportDetailRepository;
    private final InventoryRepository inventoryRepository;
    private final ProductRepository productRepository;
    private final WarehouseRepository warehouseRepository;

    public ExportService(
            ExportReceiptRepository exportReceiptRepository,
            ExportDetailRepository exportDetailRepository,
            InventoryRepository inventoryRepository,
            ProductRepository productRepository,
            WarehouseRepository warehouseRepository) {

        this.exportReceiptRepository = exportReceiptRepository;
        this.exportDetailRepository = exportDetailRepository;
        this.inventoryRepository = inventoryRepository;
        this.productRepository = productRepository;
        this.warehouseRepository = warehouseRepository;
    }

    @Transactional
    public String createExport(ExportRequest request) {

        // BR-01: Số lượng xuất phải > 0
        if (request.getQuantity() == null ||
                request.getQuantity() <= 0) {

            throw new IllegalArgumentException(
                    "Số lượng xuất phải lớn hơn 0");
        }

        // BR-02: Sản phẩm phải tồn tại
        productRepository
                .findById(request.getProductId())
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Không tìm thấy sản phẩm với id: "
                                        + request.getProductId()));

        // BR-03: Kho phải tồn tại
        warehouseRepository
                .findById(request.getWarehouseId())
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Không tìm thấy kho với id: "
                                        + request.getWarehouseId()));

        // BR-04: Kiểm tra tồn kho đủ để xuất
        Inventory inventory = inventoryRepository
                .findByWarehouseIdAndProductId(
                        request.getWarehouseId(),
                        request.getProductId())
                .orElseThrow(() ->
                        new BusinessConflictException(
                                "Sản phẩm chưa có trong kho này, "
                                        + "không thể xuất kho"));

        if (inventory.getQuantity() < request.getQuantity()) {
            throw new BusinessConflictException(
                    "Tồn kho không đủ. "
                            + "Hiện có: " + inventory.getQuantity()
                            + ", yêu cầu xuất: " + request.getQuantity());
        }

        // Bước 1: Tạo phiếu xuất
        ExportReceipt receipt = new ExportReceipt();
        receipt.setReceiptCode(request.getReceiptCode());
        receipt.setReceiptDate(LocalDate.now());
        receipt.setStatus("COMPLETED");

        ExportReceipt savedReceipt =
                exportReceiptRepository.save(receipt);

        // Bước 2: Tạo chi tiết xuất
        ExportDetail detail = new ExportDetail();
        detail.setReceiptId(savedReceipt.getId());
        detail.setProductId(request.getProductId());
        detail.setQuantity(request.getQuantity());

        exportDetailRepository.save(detail);

        // BR-05: Trừ tồn kho sau khi xuất thành công
        inventory.setQuantity(
                inventory.getQuantity() - request.getQuantity());

        inventoryRepository.save(inventory);

        return "Xuất kho thành công. "
                + "Tồn kho còn lại: " + inventory.getQuantity();
    }
}

