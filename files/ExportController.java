package com.example.supplychain.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.supplychain.dto.ExportRequest;
import com.example.supplychain.service.ExportService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/exports")
public class ExportController {

    private final ExportService exportService;

    public ExportController(ExportService exportService) {
        this.exportService = exportService;
    }

    // Lỗi validation/nghiệp vụ do GlobalExceptionHandler xử lý tập trung
    @PostMapping
    public ResponseEntity<String> createExport(
            @Valid @RequestBody ExportRequest request) {

        return ResponseEntity.ok(exportService.createExport(request));
    }
}
