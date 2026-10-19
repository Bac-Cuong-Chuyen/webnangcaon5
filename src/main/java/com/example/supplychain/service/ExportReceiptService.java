package com.example.supplychain.service;

import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.example.supplychain.entity.ExportReceipt;
import com.example.supplychain.repository.ExportReceiptRepository;
import com.example.supplychain.exception.ResourceNotFoundException;

@Service
@Transactional(readOnly = true)
public class ExportReceiptService {
    private final ExportReceiptRepository repository;
    public ExportReceiptService(ExportReceiptRepository repository) { this.repository = repository; }

    @Transactional
    public ExportReceipt create(ExportReceipt receipt) {
        ReceiptAccess.requireCreate();
        receipt.setId(null);
        receipt.setCreatedBy(ReceiptAccess.actor());
        receipt.setStatus("DRAFT");
        return repository.save(receipt);
    }
    public List<ExportReceipt> getAll() {
        return repository.findAll().stream().filter(row -> ReceiptAccess.canRead(row.getCreatedBy())).toList();
    }
    public ExportReceipt getById(Long id) {
        ExportReceipt row = load(id);
        ReceiptAccess.requireRead(row.getCreatedBy());
        return row;
    }
    @Transactional
    public ExportReceipt update(Long id, ExportReceipt request) {
        ExportReceipt row = load(id);
        ReceiptAccess.requireEdit(row.getCreatedBy(), row.getStatus());
        row.setReceiptCode(request.getReceiptCode());
        row.setReceiptDate(request.getReceiptDate());
        return repository.save(row);
    }
    @Transactional
    public void delete(Long id) {
        ExportReceipt row = load(id);
        ReceiptAccess.requireDelete(row.getCreatedBy(), row.getStatus());
        repository.delete(row);
    }
    private ExportReceipt load(Long id) {
        return repository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Khong tim thay phieu"));
    }
}
