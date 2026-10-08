package com.example.supplychain.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.supplychain.entity.Inventory;

import jakarta.persistence.LockModeType;

public interface InventoryRepository
        extends JpaRepository<Inventory, Long> {

    Optional<Inventory> findByWarehouseIdAndProductId(
            Long warehouseId,
            Long productId
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT i FROM Inventory i
            WHERE i.warehouseId = :warehouseId
              AND i.productId = :productId
            """)
    Optional<Inventory> findForUpdate(
            @Param("warehouseId") Long warehouseId,
            @Param("productId") Long productId
    );
}