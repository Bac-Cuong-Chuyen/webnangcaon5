package com.example.supplychain.repository;

<<<<<<< HEAD
import com.example.supplychain.entity.Warehouse;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface WarehouseRepository extends JpaRepository<Warehouse, Long> {
=======
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.supplychain.entity.Warehouse;

import jakarta.persistence.LockModeType;

public interface WarehouseRepository
        extends JpaRepository<Warehouse, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT w FROM Warehouse w
            WHERE w.id = :id
            """)
    Optional<Warehouse> findByIdForUpdate(
            @Param("id") Long id
    );
>>>>>>> upstream/master
}