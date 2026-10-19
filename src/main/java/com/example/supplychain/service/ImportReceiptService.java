package com.example.supplychain.service;

import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.example.supplychain.entity.ImportReceipt;
import com.example.supplychain.repository.ImportReceiptRepository;
import com.example.supplychain.exception.ResourceNotFoundException;

@Service
@Transactional(readOnly = true)
public class ImportReceiptService {
    private final ImportReceiptRepository repository;
    public ImportReceiptService(ImportReceiptRepository repository) { this.repository = repository; }

    @Transactional
    public ImportReceipt create(ImportReceipt receipt) {
        ReceiptAccess.requireCreate();
        receipt.setId(null);
        receipt.setCreatedBy(ReceiptAccess.actor());
        receipt.setStatus("DRAFT");
        return repository.save(receipt);
    }
    public List<ImportReceipt> getAll() {
        return repository.findAll().stream().filter(row -> ReceiptAccess.canRead(row.getCreatedBy())).toList();
    }
    public ImportReceipt getById(Long id) {
        ImportReceipt row = load(id);
        ReceiptAccess.requireRead(row.getCreatedBy());
        return row;
    }
    @Transactional
    public ImportReceipt update(Long id, ImportReceipt request) {
        ImportReceipt row = load(id);
        ReceiptAccess.requireEdit(row.getCreatedBy(), row.getStatus());
        row.setReceiptCode(request.getReceiptCode());
        row.setReceiptDate(request.getReceiptDate());
        return repository.save(row);
    }
    @Transactional
    public void delete(Long id) {
        ImportReceipt row = load(id);
        ReceiptAccess.requireDelete(row.getCreatedBy(), row.getStatus());
        repository.delete(row);
    }
    private ImportReceipt load(Long id) {
        return repository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Khong tim thay phieu"));
    }
}
