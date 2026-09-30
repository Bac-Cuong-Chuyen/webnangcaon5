package com.example.supplychain.service;

import java.util.List;

import org.springframework.stereotype.Service;

import com.example.supplychain.entity.Inventory;
import com.example.supplychain.repository.InventoryRepository;

@Service
public class InventoryService {

    private final InventoryRepository inventoryRepository;

    public InventoryService(InventoryRepository inventoryRepository) {
        this.inventoryRepository = inventoryRepository;
    }

    // CREATE
    public Inventory create(Inventory inventory) {
        return inventoryRepository.save(inventory);
    }

    // READ ALL
    public List<Inventory> getAll() {
        return inventoryRepository.findAll();
    }

    // READ BY ID
    public Inventory getById(Long id) {
        return inventoryRepository.findById(id)
                .orElseThrow(() -> new RuntimeException(
                        "Không tìm thấy tồn kho với id: " + id));
    }

    // UPDATE
    public Inventory update(Long id, Inventory inventory) {
        Inventory existing = getById(id);

        existing.setWarehouseId(inventory.getWarehouseId());
        existing.setProductId(inventory.getProductId());
        existing.setQuantity(inventory.getQuantity());
        existing.setMinimumQuantity(inventory.getMinimumQuantity());

        return inventoryRepository.save(existing);
    }

    // DELETE
    public void delete(Long id) {
        Inventory existing = getById(id);
        inventoryRepository.delete(existing);
    }
}