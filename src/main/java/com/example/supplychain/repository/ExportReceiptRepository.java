package com.example.supplychain.repository;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import com.example.supplychain.entity.ExportReceipt;
public interface ExportReceiptRepository extends JpaRepository<ExportReceipt,Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM ExportReceipt r WHERE r.id = :id")
    Optional<ExportReceipt> findByIdForUpdate(@Param("id") Long id);

    @Query("""
        SELECT r FROM ExportReceipt r WHERE (:readAll = true OR r.createdBy = :username)
        """)
    Slice<ExportReceipt> findReadablePage(@Param("readAll") boolean readAll,
            @Param("username") String username, Pageable pageable);
}
