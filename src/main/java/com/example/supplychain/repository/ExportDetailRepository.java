package com.example.supplychain.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.supplychain.entity.ExportDetail;

public interface ExportDetailRepository
        extends JpaRepository<ExportDetail, Long> {

}
