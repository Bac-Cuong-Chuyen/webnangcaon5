package com.example.supplychain.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.example.supplychain.entity.Inventory;
import com.example.supplychain.service.InventoryService;

@RestController
@RequestMapping("/api/inventories")
public class InventoryController {

    private final InventoryService inventoryService;

    public InventoryController(InventoryService inventoryService) {
        this.inventoryService = inventoryService;
    }

    // CREATE
    @PostMapping
    public ResponseEntity<Inventory> create(
            @RequestBody Inventory inventory) {

        return ResponseEntity.ok(
                inventoryService.create(inventory));
    }

    // READ ALL
    @GetMapping
    public ResponseEntity<List<Inventory>> getAll() {

        return ResponseEntity.ok(
                inventoryService.getAll());
    }

    // READ BY ID
    @GetMapping("/{id}")
    public ResponseEntity<Inventory> getById(
            @PathVariable Long id) {

        return ResponseEntity.ok(
                inventoryService.getById(id));
    }

    // UPDATE
    @PutMapping("/{id}")
    public ResponseEntity<Inventory> update(
            @PathVariable Long id,
            @RequestBody Inventory inventory) {

        return ResponseEntity.ok(
                inventoryService.update(id, inventory));
    }

    // DELETE
    @DeleteMapping("/{id}")
    public ResponseEntity<String> delete(
            @PathVariable Long id) {

        inventoryService.delete(id);

        return ResponseEntity.ok(
                "Inventory deleted successfully");
    }
}