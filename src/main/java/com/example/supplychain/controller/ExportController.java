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

    private final com.example.supplychain.service.ReceiptApprovalService approvalService;
    public ExportController(ExportService exportService,
            com.example.supplychain.service.ReceiptApprovalService approvalService) {
        this.exportService = exportService;
        this.approvalService = approvalService;
    }

    // Lỗi validation/nghiệp vụ do GlobalExceptionHandler xử lý tập trung
    @PostMapping
    public ResponseEntity<String> createExport(
            @Valid @RequestBody ExportRequest request) {

        return ResponseEntity.ok(exportService.createExport(request));
    }

    @PostMapping("/{id}/approve")
    public ResponseEntity<com.example.supplychain.entity.ExportReceipt> approve(
            @org.springframework.web.bind.annotation.PathVariable("id") Long id) {
        return ResponseEntity.ok(approvalService.approveExport(id));
    }
}
