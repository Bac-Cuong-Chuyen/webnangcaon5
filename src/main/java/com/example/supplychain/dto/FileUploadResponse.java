package com.example.supplychain.dto;

public record FileUploadResponse(String fileName, long sizeBytes, String fileType) {
}
