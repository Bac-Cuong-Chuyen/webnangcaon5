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
    private final com.example.supplychain.repository.ImportDetailRepository details;
    public ImportReceiptService(ImportReceiptRepository repository, com.example.supplychain.repository.ImportDetailRepository details) { this.repository = repository; this.details = details; }

    @Transactional
    public ImportReceipt create(ImportReceipt receipt) {
        ReceiptAccess.requireCreate();
        receipt.setId(null);
        receipt.setCreatedBy(ReceiptAccess.actor());
        receipt.setStatus("DRAFT");
        receipt.setApprovedBy(null);
        if (receipt.getReceiptDate() == null) receipt.setReceiptDate(java.time.LocalDate.now());
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
        ImportReceipt row = loadForUpdate(id);
        ReceiptAccess.requireEdit(row.getCreatedBy(), row.getStatus());
        row.setReceiptCode(request.getReceiptCode());
        if (request.getReceiptDate() != null) row.setReceiptDate(request.getReceiptDate());
        if (request.getWarehouseId() != null) row.setWarehouseId(request.getWarehouseId());
        return repository.save(row);
    }
    @Transactional
    public void delete(Long id) {
        ImportReceipt row = loadForUpdate(id);
        ReceiptAccess.requireDelete(row.getCreatedBy(), row.getStatus());
        details.deleteByReceiptId(id);
        repository.delete(row);
    }
    private ImportReceipt loadForUpdate(Long id) {
        return repository.findByIdForUpdate(id).orElseThrow(() -> new ResourceNotFoundException("Khong tim thay phieu"));
    }
    private ImportReceipt load(Long id) {
        return repository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Khong tim thay phieu"));
    }
}
