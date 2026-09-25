package com.example.supplychain.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.supplychain.entity.Product;

public interface ProductRepository extends JpaRepository<Product, Long> {
}