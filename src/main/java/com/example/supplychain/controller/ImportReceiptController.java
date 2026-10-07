package com.example.supplychain.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.supplychain.entity.ImportReceipt;
import com.example.supplychain.service.ImportReceiptService;

@RestController
@RequestMapping("/api/import-receipts")
public class ImportReceiptController {

    private final ImportReceiptService importReceiptService;

    public ImportReceiptController(
            ImportReceiptService importReceiptService) {
        this.importReceiptService = importReceiptService;
    }

    // CREATE
    @PostMapping
    public ResponseEntity<ImportReceipt> create(
            @RequestBody ImportReceipt receipt) {

        return ResponseEntity.ok(
                importReceiptService.create(receipt));
    }

    // READ ALL
    @GetMapping
    public ResponseEntity<List<ImportReceipt>> getAll() {

        return ResponseEntity.ok(
                importReceiptService.getAll());
    }

    // READ BY ID
    @GetMapping("/{id}")
    public ResponseEntity<ImportReceipt> getById(
            @PathVariable Long id) {

        return ResponseEntity.ok(
                importReceiptService.getById(id));
    }

    // UPDATE
    @PutMapping("/{id}")
    public ResponseEntity<ImportReceipt> update(
            @PathVariable Long id,
            @RequestBody ImportReceipt receipt) {

        return ResponseEntity.ok(
                importReceiptService.update(id, receipt));
    }

    // DELETE
    @DeleteMapping("/{id}")
    public ResponseEntity<String> delete(
            @PathVariable Long id) {

        importReceiptService.delete(id);

        return ResponseEntity.ok(
                "ImportReceipt deleted successfully");
    }
}