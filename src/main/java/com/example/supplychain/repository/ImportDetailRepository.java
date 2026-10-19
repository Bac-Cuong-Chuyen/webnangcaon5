package com.example.supplychain.repository;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import com.example.supplychain.entity.ImportDetail;
public interface ImportDetailRepository extends JpaRepository<ImportDetail,Long> {
    List<ImportDetail> findByReceiptIdOrderByProductIdAsc(Long receiptId);
    void deleteByReceiptId(Long receiptId);
}
