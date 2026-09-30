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
import com.example.supplychain.repository.WarehouseRepository;

@Service
public class ImportService {

    private final ImportReceiptRepository importReceiptRepository;
    private final ImportDetailRepository importDetailRepository;
    private final InventoryRepository inventoryRepository;
    private final ProductRepository productRepository;
    private final WarehouseRepository warehouseRepository;

    public ImportService(
            ImportReceiptRepository importReceiptRepository,
            ImportDetailRepository importDetailRepository,
            InventoryRepository inventoryRepository,
            ProductRepository productRepository,
            WarehouseRepository warehouseRepository) {

        this.importReceiptRepository = importReceiptRepository;
        this.importDetailRepository = importDetailRepository;
        this.inventoryRepository = inventoryRepository;
        this.productRepository = productRepository;
        this.warehouseRepository = warehouseRepository;
    }

    @Transactional
    public String createImport(ImportRequest request) {

        // 1. Kiểm tra số lượng
        if (request.getQuantity() == null ||
                request.getQuantity() <= 0) {

            throw new IllegalArgumentException(
                    "Số lượng nhập phải lớn hơn 0");
        }

        // 2. Kiểm tra sản phẩm
        productRepository.findById(request.getProductId())
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Không tìm thấy sản phẩm với id: "
                                        + request.getProductId()));

        // 2b. Kiểm tra kho (BR-03)
        warehouseRepository.findById(request.getWarehouseId())
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Không tìm thấy kho với id: "
                                        + request.getWarehouseId()));

        // 3. Tạo phiếu nhập
        ImportReceipt receipt = new ImportReceipt();

        receipt.setReceiptCode(request.getReceiptCode());
        receipt.setReceiptDate(LocalDate.now());
        receipt.setStatus("COMPLETED");

        ImportReceipt savedReceipt =
                importReceiptRepository.save(receipt);

        // 4. Tạo chi tiết nhập
        ImportDetail detail = new ImportDetail();

        detail.setReceiptId(savedReceipt.getId());
        detail.setProductId(request.getProductId());
        detail.setQuantity(request.getQuantity());

        importDetailRepository.save(detail);

        // 5. Tìm tồn kho theo kho + sản phẩm
        Inventory inventory =
                inventoryRepository
                        .findByWarehouseIdAndProductId(
                                request.getWarehouseId(),
                                request.getProductId())
                        .orElse(null);

        // 6. Nếu chưa có tồn kho → tạo mới
        if (inventory == null) {

            inventory = new Inventory();

            inventory.setWarehouseId(
                    request.getWarehouseId());

            inventory.setProductId(
                    request.getProductId());

            inventory.setQuantity(
                    request.getQuantity());

            inventory.setMinimumQuantity(0);

        } else {

            // 7. Nếu đã có → cộng số lượng
            inventory.setQuantity(
                    inventory.getQuantity()
                            + request.getQuantity());
        }

        inventoryRepository.save(inventory);

        return "Nhập kho thành công";
    }
}