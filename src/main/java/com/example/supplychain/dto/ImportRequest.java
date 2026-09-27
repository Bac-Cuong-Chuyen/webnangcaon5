package com.example.supplychain.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public class ImportRequest {

    @NotNull(message = "productId không được để trống")
    @Positive(message = "productId phải là số nguyên dương")
    private Long productId;

    @NotNull(message = "warehouseId không được để trống")
    @Positive(message = "warehouseId phải là số nguyên dương")
    private Long warehouseId;

    @NotNull(message = "quantity không được để trống")
    @Positive(message = "quantity phải lớn hơn 0")
    private Integer quantity;

    public ImportRequest() {
    }

    public ImportRequest(Long productId, Long warehouseId, Integer quantity) {
        this.productId = productId;
        this.warehouseId = warehouseId;
        this.quantity = quantity;
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

    public Integer getQuantity() {
        return quantity;
    }

    public void setQuantity(Integer quantity) {
        this.quantity = quantity;
    }
}