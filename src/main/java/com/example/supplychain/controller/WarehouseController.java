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

import com.example.supplychain.entity.Warehouse;
import com.example.supplychain.service.WarehouseService;

@RestController
@RequestMapping("/api/warehouses")
public class WarehouseController {

    private final WarehouseService warehouseService;

    public WarehouseController(WarehouseService warehouseService) {
        this.warehouseService = warehouseService;
    }

    // CREATE
    @PostMapping
    public ResponseEntity<Warehouse> create(
            @RequestBody Warehouse warehouse) {

        return ResponseEntity.ok(
                warehouseService.create(warehouse));
    }

    // READ ALL
    @GetMapping
    public ResponseEntity<List<Warehouse>> getAll() {

        return ResponseEntity.ok(
                warehouseService.getAll());
    }

    // READ BY ID
    @GetMapping("/{id}")
    public ResponseEntity<Warehouse> getById(
            @PathVariable Long id) {

        return ResponseEntity.ok(
                warehouseService.getById(id));
    }

    // UPDATE
    @PutMapping("/{id}")
    public ResponseEntity<Warehouse> update(
            @PathVariable Long id,
            @RequestBody Warehouse warehouse) {

        return ResponseEntity.ok(
                warehouseService.update(id, warehouse));
    }

    // DELETE
    @DeleteMapping("/{id}")
    public ResponseEntity<String> delete(
            @PathVariable Long id) {

        warehouseService.delete(id);

        return ResponseEntity.ok(
                "Warehouse deleted successfully");
    }
}