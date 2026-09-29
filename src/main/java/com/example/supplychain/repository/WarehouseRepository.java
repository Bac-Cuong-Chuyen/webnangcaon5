package com.example.supplychain.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.supplychain.entity.Warehouse;

public interface WarehouseRepository
        extends JpaRepository<Warehouse, Long> {

}
