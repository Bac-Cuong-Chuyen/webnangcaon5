package com.example.supplychain.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.supplychain.entity.ImportDetail;

public interface ImportDetailRepository
        extends JpaRepository<ImportDetail, Long> {

}