package com.example.supplychain.service;

import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.example.supplychain.entity.ExportDetail;
import com.example.supplychain.entity.ExportReceipt;
import com.example.supplychain.repository.ExportDetailRepository;
import com.example.supplychain.repository.ExportReceiptRepository;
import com.example.supplychain.exception.ResourceNotFoundException;

@Service
@Transactional(readOnly = true)
public class ExportDetailService {
    private final ExportDetailRepository repository;
    private final ExportReceiptRepository receipts;
    public ExportDetailService(ExportDetailRepository repository, ExportReceiptRepository receipts) {
        this.repository = repository;
        this.receipts = receipts;
    }
    @Transactional
    public ExportDetail create(ExportDetail detail) {
        writable(detail.getReceiptId());
        detail.setId(null);
        return repository.save(detail);
    }
    public List<ExportDetail> getAll() {
        return repository.findAll().stream()
            .filter(row -> ReceiptAccess.canRead(parent(row.getReceiptId()).getCreatedBy())).toList();
    }
    public ExportDetail getById(Long id) {
        ExportDetail row = load(id);
        ReceiptAccess.requireRead(parent(row.getReceiptId()).getCreatedBy());
        return row;
    }
    @Transactional
    public ExportDetail update(Long id, ExportDetail request) {
        ExportDetail row = load(id);
        writable(row.getReceiptId());
        writable(request.getReceiptId());
        row.setReceiptId(request.getReceiptId());
        row.setProductId(request.getProductId());
        row.setQuantity(request.getQuantity());
        return repository.save(row);
    }
    @Transactional
    public void delete(Long id) {
        ExportDetail row = load(id);
        writable(row.getReceiptId());
        repository.delete(row);
    }
    private ExportDetail load(Long id) {
        return repository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Khong tim thay chi tiet phieu"));
    }
    private ExportReceipt parent(Long id) {
        if (id == null) throw new IllegalArgumentException("receiptId la bat buoc");
        return receipts.findById(id).orElseThrow(() -> new ResourceNotFoundException("Khong tim thay phieu"));
    }
    private void writable(Long id) {
        if (id == null) throw new IllegalArgumentException("receiptId la bat buoc");
        ExportReceipt row = receipts.findByIdForUpdate(id)
            .orElseThrow(() -> new ResourceNotFoundException("Khong tim thay phieu"));
        ReceiptAccess.requireEdit(row.getCreatedBy(), row.getStatus());
    }
}
