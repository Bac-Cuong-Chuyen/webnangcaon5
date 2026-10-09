package com.example.supplychain.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.example.supplychain.entity.ExportDetail;
import com.example.supplychain.service.ExportDetailService;

@RestController
@RequestMapping("/api/export-details")
public class ExportDetailController {

    private final ExportDetailService exportDetailService;

    public ExportDetailController(
            ExportDetailService exportDetailService) {
        this.exportDetailService = exportDetailService;
    }

    // CREATE
    @PostMapping
    public ResponseEntity<ExportDetail> create(
            @RequestBody ExportDetail detail) {

        return ResponseEntity.ok(
                exportDetailService.create(detail));
    }

    // READ ALL
    @GetMapping
    public ResponseEntity<List<ExportDetail>> getAll() {

        return ResponseEntity.ok(
                exportDetailService.getAll());
    }

    // READ BY ID
    @GetMapping("/{id}")
    public ResponseEntity<ExportDetail> getById(
            @PathVariable Long id) {

        return ResponseEntity.ok(
                exportDetailService.getById(id));
    }

    // UPDATE
    @PutMapping("/{id}")
    public ResponseEntity<ExportDetail> update(
            @PathVariable Long id,
            @RequestBody ExportDetail detail) {

        return ResponseEntity.ok(
                exportDetailService.update(id, detail));
    }

    // DELETE
    @DeleteMapping("/{id}")
    public ResponseEntity<String> delete(
            @PathVariable Long id) {

        exportDetailService.delete(id);

        return ResponseEntity.ok(
                "ExportDetail deleted successfully");
    }
}