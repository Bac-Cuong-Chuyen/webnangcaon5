package com.example.supplychain.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.supplychain.dto.ImportRequest;
import com.example.supplychain.service.ImportService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/imports")
public class ImportController {

    private final ImportService importService;

    public ImportController(ImportService importService) {
        this.importService = importService;
    }

    // Lỗi validation/nghiệp vụ do GlobalExceptionHandler xử lý tập trung
    @PostMapping
    public ResponseEntity<String> createImport(
            @Valid @RequestBody ImportRequest request) {

        return ResponseEntity.ok(importService.createImport(request));
    }
}
