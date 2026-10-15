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

    public ImportController(ImportService importService) {
        this.importService = importService;
    }

    @PostMapping
    public ResponseEntity<String> createImport(
            @Valid @RequestBody ImportRequest request) {

        return ResponseEntity.ok(importService.createImport(request));
    }
}