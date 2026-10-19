package com.example.supplychain.controller;

import com.example.supplychain.dto.ImportRequest;
import com.example.supplychain.service.ImportService;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping({"/api/imports", "/api/import"})
public class ImportController {

    private final ImportService importService;

    private final com.example.supplychain.service.ReceiptApprovalService approvalService;
    public ImportController(ImportService importService,
            com.example.supplychain.service.ReceiptApprovalService approvalService) {
        this.importService = importService;
        this.approvalService = approvalService;
    }

    @PostMapping
    public ResponseEntity<String> createImport(
            @Valid @RequestBody ImportRequest request) {

        return ResponseEntity.ok(importService.createImport(request));
    }

    @PostMapping("/{id}/approve")
    public ResponseEntity<com.example.supplychain.entity.ImportReceipt> approve(
            @org.springframework.web.bind.annotation.PathVariable("id") Long id) {
        return ResponseEntity.ok(approvalService.approveImport(id));
    }
}
