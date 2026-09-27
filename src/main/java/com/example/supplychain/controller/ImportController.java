package com.example.supplychain.controller;

import com.example.supplychain.dto.ImportRequest;
import com.example.supplychain.service.ImportService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/import")
public class ImportController {

    @Autowired
    private ImportService importService;

    @PostMapping
    public ResponseEntity<?> createImport(@Valid @RequestBody ImportRequest request) {
        Object result = importService.processImport(request);
        return ResponseEntity.ok(result);
    }
}