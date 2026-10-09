package com.example.supplychain.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.supplychain.entity.TransferDetail;

public interface TransferDetailRepository
        extends JpaRepository<TransferDetail, Long> {

    List<TransferDetail> findByTransferIdOrderByProductIdAsc(
            Long transferId
    );
}