package com.example.supplychain.repository;

import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.example.supplychain.entity.Warehouse;

@Repository
public interface WarehouseRepository
        extends JpaRepository<Warehouse, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT w FROM Warehouse w WHERE w.id = :id")
    Optional<Warehouse> findByIdForUpdate(@Param("id") Long id);
}