package com.example.supplychain.repository;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import com.example.supplychain.entity.ExportDetail;
public interface ExportDetailRepository extends JpaRepository<ExportDetail,Long> {
    List<ExportDetail> findByReceiptIdOrderByProductIdAsc(Long receiptId);
    void deleteByReceiptId(Long receiptId);
}
