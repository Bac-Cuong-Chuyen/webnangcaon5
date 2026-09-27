package com.example.supplychain.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public class ImportRequest {

    @NotBlank(message = "Mã phiếu nhập không được để trống")
    private String receiptCode;

    private Long productId;
    private Long warehouseId;
    private Long supplierId;
    private Long userId;

    @Min(value = 1, message = "Số lượng nhập phải lớn hơn 0")
    private Integer quantity;

    public ImportRequest() {
    }

    public ImportRequest(String receiptCode, Long productId, Long warehouseId, Long supplierId, Long userId, Integer quantity) {
        this.receiptCode = receiptCode;
        this.productId = productId;
        this.warehouseId = warehouseId;
        this.supplierId = supplierId;
        this.userId = userId;
        this.quantity = quantity;
    }

    // Getters and Setters
    public String getReceiptCode() {
        return receiptCode;
    }

    public void setReceiptCode(String receiptCode) {
        this.receiptCode = receiptCode;
    }

    public Long getProductId() {
        return productId;
    }

    public void setProductId(Long productId) {
        this.productId = productId;
    }

    public Long getWarehouseId() {
        return warehouseId;
    }

    public void setWarehouseId(Long warehouseId) {
        this.warehouseId = warehouseId;
    }

    public Long getSupplierId() {
        return supplierId;
    }

    public void setSupplierId(Long supplierId) {
        this.supplierId = supplierId;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public Integer getQuantity() {
        return quantity;
    }

    public void setQuantity(Integer quantity) {
        this.quantity = quantity;
    }
}