package com.example.supplychain.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class ImportRequest {

    private String receiptCode;

    @NotNull(message = "warehouseId là bắt buộc")
    private Long warehouseId;

    @NotNull(message = "productId là bắt buộc")
    private Long productId;

    @NotNull(message = "quantity là bắt buộc")
    @Positive(message = "Số lượng nhập phải lớn hơn 0")
    private Integer quantity;
}
