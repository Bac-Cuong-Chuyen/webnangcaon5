package com.example.supplychain.service;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.example.supplychain.entity.ImportDetail;
import com.example.supplychain.repository.ImportDetailRepository;

@Service
public class ImportDetailService {

    private final ImportDetailRepository importDetailRepository;

    public ImportDetailService(
            ImportDetailRepository importDetailRepository) {
        this.importDetailRepository = importDetailRepository;
    }

    // CREATE
    public ImportDetail create(ImportDetail detail) {
        return importDetailRepository.save(detail);
    }

    // READ ALL
    public List<ImportDetail> getAll() {
        return importDetailRepository.findAll();
    }

    // READ BY ID
    public ImportDetail getById(Long id) {
        return importDetailRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Không tìm thấy chi tiết phiếu nhập với id: " + id));
    }

    // UPDATE
    public ImportDetail update(Long id, ImportDetail detail) {
        ImportDetail existing = getById(id);

        existing.setReceiptId(detail.getReceiptId());
        existing.setProductId(detail.getProductId());
        existing.setQuantity(detail.getQuantity());

        return importDetailRepository.save(existing);
    }

    // DELETE
    public void delete(Long id) {
        ImportDetail existing = getById(id);
        importDetailRepository.delete(existing);
    }
}