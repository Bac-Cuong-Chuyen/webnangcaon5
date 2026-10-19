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
    private final com.example.supplychain.repository.ExportDetailRepository details;
    public ExportReceiptService(ExportReceiptRepository repository, com.example.supplychain.repository.ExportDetailRepository details) { this.repository = repository; this.details = details; }

    @Transactional
    public ExportReceipt create(ExportReceipt receipt) {
        ReceiptAccess.requireCreate();
        receipt.setId(null);
        receipt.setCreatedBy(ReceiptAccess.actor());
        receipt.setStatus("DRAFT");
        receipt.setApprovedBy(null);
        if (receipt.getReceiptDate() == null) receipt.setReceiptDate(java.time.LocalDate.now());
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
        ExportReceipt row = loadForUpdate(id);
        ReceiptAccess.requireEdit(row.getCreatedBy(), row.getStatus());
        row.setReceiptCode(request.getReceiptCode());
        if (request.getReceiptDate() != null) row.setReceiptDate(request.getReceiptDate());
        if (request.getWarehouseId() != null) row.setWarehouseId(request.getWarehouseId());
        return repository.save(row);
    }
    @Transactional
    public void delete(Long id) {
        ExportReceipt row = loadForUpdate(id);
        ReceiptAccess.requireDelete(row.getCreatedBy(), row.getStatus());
        details.deleteByReceiptId(id);
        repository.delete(row);
    }
    private ExportReceipt loadForUpdate(Long id) {
        return repository.findByIdForUpdate(id).orElseThrow(() -> new ResourceNotFoundException("Khong tim thay phieu"));
    }
    private ExportReceipt load(Long id) {
        return repository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Khong tim thay phieu"));
    }
}
