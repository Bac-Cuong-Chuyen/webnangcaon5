package com.example.supplychain.service;

import java.util.List;

import org.springframework.stereotype.Service;

import com.example.supplychain.entity.Warehouse;
import com.example.supplychain.repository.WarehouseRepository;

@Service
public class WarehouseService {

    private final WarehouseRepository warehouseRepository;

    public WarehouseService(WarehouseRepository warehouseRepository) {
        this.warehouseRepository = warehouseRepository;
    }

    // CREATE
    public Warehouse create(Warehouse warehouse) {
        return warehouseRepository.save(warehouse);
    }

    // READ ALL
    public List<Warehouse> getAll() {
        return warehouseRepository.findAll();
    }

    // READ BY ID
    public Warehouse getById(Long id) {
        return warehouseRepository.findById(id)
                .orElseThrow(() -> new RuntimeException(
                        "Không tìm thấy kho với id: " + id));
    }

    // UPDATE
    public Warehouse update(Long id, Warehouse warehouse) {
        Warehouse existing = getById(id);

        existing.setCode(warehouse.getCode());
        existing.setName(warehouse.getName());
        existing.setAddress(warehouse.getAddress());

        return warehouseRepository.save(existing);
    }

    // DELETE
    public void delete(Long id) {
        Warehouse existing = getById(id);
        warehouseRepository.delete(existing);
    }
}
