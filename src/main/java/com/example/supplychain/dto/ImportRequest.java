package com.example.supplychain.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class ImportRequest {

    private String receiptCode;

    private Long warehouseId;

    private Long productId;

    private Integer quantity;
}