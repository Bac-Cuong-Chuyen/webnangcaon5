package com.example.supplychain.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.supplychain.entity.StockTransfer;

import jakarta.persistence.LockModeType;

public interface StockTransferRepository
        extends JpaRepository<StockTransfer, Long> {

    boolean existsByTransferCode(String transferCode);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT t FROM StockTransfer t
            WHERE t.id = :id
            """)
    Optional<StockTransfer> findByIdForUpdate(
            @Param("id") Long id
    );
}