package com.example.supplychain.repository;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import com.example.supplychain.entity.ImportDetail;

public interface ImportDetailRepository extends JpaRepository<ImportDetail, Long> {
    List<ImportDetail> findByReceiptIdOrderByProductIdAsc(Long receiptId);
    void deleteByReceiptId(Long receiptId);

    @Query("""
        SELECT d FROM ImportDetail d, ImportReceipt r
        WHERE d.receiptId = r.id
          AND (:readAll = true OR r.createdBy = :username)
        ORDER BY d.id
        """)
    List<ImportDetail> findReadable(
        @Param("readAll") boolean readAll,
        @Param("username") String username);

    @Query("""
        SELECT d FROM ImportDetail d, ImportReceipt r
        WHERE d.receiptId = r.id AND (:readAll = true OR r.createdBy = :username)
        """)
    Slice<ImportDetail> findReadablePage(@Param("readAll") boolean readAll,
            @Param("username") String username, Pageable pageable);
}
