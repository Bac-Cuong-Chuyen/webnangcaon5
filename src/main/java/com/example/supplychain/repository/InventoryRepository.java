package com.example.supplychain.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.supplychain.entity.Inventory;

public interface InventoryRepository
        extends JpaRepository<Inventory, Long> {

    Optional<Inventory> findByWarehouseIdAndProductId(
            Long warehouseId,
            Long productId
    );
}