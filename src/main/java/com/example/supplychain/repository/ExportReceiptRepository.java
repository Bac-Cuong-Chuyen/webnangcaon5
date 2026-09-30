package com.example.supplychain.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.supplychain.entity.ExportReceipt;

public interface ExportReceiptRepository
        extends JpaRepository<ExportReceipt, Long> {

}
