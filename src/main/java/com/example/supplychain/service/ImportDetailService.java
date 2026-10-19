package com.example.supplychain.service;
import org.springframework.data.domain.SliceImpl;
import com.example.supplychain.util.PageLimits;
import org.springframework.data.domain.Slice;

import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.example.supplychain.entity.ImportDetail;
import com.example.supplychain.entity.ImportReceipt;
import com.example.supplychain.repository.ImportDetailRepository;
import com.example.supplychain.repository.ImportReceiptRepository;
import com.example.supplychain.exception.ResourceNotFoundException;

@Service
@Transactional(readOnly = true)
public class ImportDetailService {
    private final ImportDetailRepository repository;
    private final ImportReceiptRepository receipts;
    public ImportDetailService(ImportDetailRepository repository, ImportReceiptRepository receipts) {
        this.repository = repository;
        this.receipts = receipts;
    }
    @Transactional
    public ImportDetail create(ImportDetail detail) {
        writable(detail.getReceiptId());
        detail.setId(null);
        return repository.save(detail);
    }
    public List<ImportDetail> getAll() {
        String username = ReceiptAccess.actor();
        boolean readAll = ReceiptAccess.role("ADMIN")
            || ReceiptAccess.role("WAREHOUSE_MANAGER") || ReceiptAccess.role("DIRECTOR");
        if (!readAll && !ReceiptAccess.role("WAREHOUSE_STAFF")) return List.of();
        return repository.findReadable(readAll, username);
    }
    public ImportDetail getById(Long id) {
        ImportDetail row = load(id);
        ReceiptAccess.requireRead(parent(row.getReceiptId()).getCreatedBy());
        return row;
    }
    @Transactional
    public ImportDetail update(Long id, ImportDetail request) {
        ImportDetail row = load(id);
        writable(row.getReceiptId());
        writable(request.getReceiptId());
        row.setReceiptId(request.getReceiptId());
        row.setProductId(request.getProductId());
        row.setQuantity(request.getQuantity());
        return repository.save(row);
    }
    @Transactional
    public void delete(Long id) {
        ImportDetail row = load(id);
        writable(row.getReceiptId());
        repository.delete(row);
    }
    private ImportDetail load(Long id) {
        return repository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Khong tim thay chi tiet phieu"));
    }
    private ImportReceipt parent(Long id) {
        if (id == null) throw new IllegalArgumentException("receiptId la bat buoc");
        return receipts.findById(id).orElseThrow(() -> new ResourceNotFoundException("Khong tim thay phieu"));
    }
    private void writable(Long id) {
        if (id == null) throw new IllegalArgumentException("receiptId la bat buoc");
        ImportReceipt row = receipts.findByIdForUpdate(id)
            .orElseThrow(() -> new ResourceNotFoundException("Khong tim thay phieu"));
        ReceiptAccess.requireEdit(row.getCreatedBy(), row.getStatus());
    }

    @Transactional(readOnly = true)
    public Slice<ImportDetail> getPage(int page, int size) {
        var paging = PageLimits.of(page, size);
        String username = ReceiptAccess.actor();
        boolean readAll = ReceiptAccess.role("ADMIN") || ReceiptAccess.role("WAREHOUSE_MANAGER")
                || ReceiptAccess.role("DIRECTOR");
        if (!readAll && !ReceiptAccess.role("WAREHOUSE_STAFF")) {
            return new SliceImpl<>(List.of(), paging, false);
        }
        return repository.findReadablePage(readAll, username, paging);
    }
}
