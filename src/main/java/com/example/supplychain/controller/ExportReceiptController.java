package com.example.supplychain.controller;
import org.springframework.web.bind.annotation.RequestParam;
import com.example.supplychain.util.PageLimits;

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

import com.example.supplychain.entity.ExportReceipt;
import com.example.supplychain.service.ExportReceiptService;

@RestController
@RequestMapping("/api/export-receipts")
public class ExportReceiptController {

    private final ExportReceiptService exportReceiptService;

    public ExportReceiptController(
            ExportReceiptService exportReceiptService) {
        this.exportReceiptService = exportReceiptService;
    }

    // CREATE
    @PostMapping
    public ResponseEntity<ExportReceipt> create(
            @RequestBody ExportReceipt receipt) {

        return ResponseEntity.ok(
                exportReceiptService.create(receipt));
    }

    // READ ALL
    @GetMapping
    public ResponseEntity<List<ExportReceipt>> getAll(
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "20") int size) {
        return PageLimits.response(exportReceiptService.getPage(page, size));
    }

    // READ BY ID
    @GetMapping("/{id}")
    public ResponseEntity<ExportReceipt> getById(
            @PathVariable Long id) {

        return ResponseEntity.ok(
                exportReceiptService.getById(id));
    }

    // UPDATE
    @PutMapping("/{id}")
    public ResponseEntity<ExportReceipt> update(
            @PathVariable Long id,
            @RequestBody ExportReceipt receipt) {

        return ResponseEntity.ok(
                exportReceiptService.update(id, receipt));
    }

    // DELETE
    @DeleteMapping("/{id}")
    public ResponseEntity<String> delete(
            @PathVariable Long id) {

        exportReceiptService.delete(id);

        return ResponseEntity.ok(
                "ExportReceipt deleted successfully");
    }
}