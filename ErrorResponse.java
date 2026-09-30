package com.example.supplychain.exception;

import java.time.LocalDateTime;

// Cấu trúc lỗi thống nhất đã chốt: {code, message, timestamp, path}
public record ErrorResponse(
        String code,
        String message,
        LocalDateTime timestamp,
        String path) {
}
