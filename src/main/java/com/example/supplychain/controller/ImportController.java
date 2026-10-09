package com.example.supplychain.controller;

import com.example.supplychain.dto.ImportRequest;
import com.example.supplychain.service.ImportService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/import")
public class ImportController {

    @Autowired
    private ImportService importService;

    // Lỗi validation/nghiệp vụ do GlobalExceptionHandler xử lý tập trung
    @PostMapping
<<<<<<< HEAD
    public ResponseEntity<?> createImport(@Valid @RequestBody ImportRequest request) {
        Object result = importService.processImport(request);
        return ResponseEntity.ok(result);
=======
    public ResponseEntity<String> createImport(
            @Valid @RequestBody ImportRequest request) {

        return ResponseEntity.ok(importService.createImport(request));
>>>>>>> upstream/master
    }
}
