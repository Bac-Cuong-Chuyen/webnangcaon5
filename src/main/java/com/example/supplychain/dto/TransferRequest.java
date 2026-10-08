package com.example.supplychain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class TransferRequest {

    @NotBlank(message = "Ma phieu chuyen kho khong duoc de trong")
    private String transferCode;

    @NotNull(message = "Kho nguon khong duoc de trong")
    @Positive(message = "ID kho nguon phai lon hon 0")
    private Long fromWarehouseId;

    @NotNull(message = "Kho dich khong duoc de trong")
    @Positive(message = "ID kho dich phai lon hon 0")
    private Long toWarehouseId;

    @NotNull(message = "San pham khong duoc de trong")
    @Positive(message = "ID san pham phai lon hon 0")
    private Long productId;

    @NotNull(message = "So luong khong duoc de trong")
    @Positive(message = "So luong chuyen phai lon hon 0")
    private Integer quantity;
}