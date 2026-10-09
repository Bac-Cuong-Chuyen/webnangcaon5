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

import com.example.supplychain.entity.ImportDetail;
import com.example.supplychain.service.ImportDetailService;

@RestController
@RequestMapping("/api/import-details")
public class ImportDetailController {

    private final ImportDetailService importDetailService;

    public ImportDetailController(
            ImportDetailService importDetailService) {
        this.importDetailService = importDetailService;
    }

    // CREATE
    @PostMapping
    public ResponseEntity<ImportDetail> create(
            @RequestBody ImportDetail detail) {

        return ResponseEntity.ok(
                importDetailService.create(detail));
    }

    // READ ALL
    @GetMapping
    public ResponseEntity<List<ImportDetail>> getAll() {

        return ResponseEntity.ok(
                importDetailService.getAll());
    }

    // READ BY ID
    @GetMapping("/{id}")
    public ResponseEntity<ImportDetail> getById(
            @PathVariable Long id) {

        return ResponseEntity.ok(
                importDetailService.getById(id));
    }

    // UPDATE
    @PutMapping("/{id}")
    public ResponseEntity<ImportDetail> update(
            @PathVariable Long id,
            @RequestBody ImportDetail detail) {

        return ResponseEntity.ok(
                importDetailService.update(id, detail));
    }

    // DELETE
    @DeleteMapping("/{id}")
    public ResponseEntity<String> delete(
            @PathVariable Long id) {

        importDetailService.delete(id);

        return ResponseEntity.ok(
                "ImportDetail deleted successfully");
    }
}