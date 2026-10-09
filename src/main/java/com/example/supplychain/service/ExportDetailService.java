package com.example.supplychain.service;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.example.supplychain.entity.ExportDetail;
import com.example.supplychain.repository.ExportDetailRepository;

@Service
public class ExportDetailService {

    private final ExportDetailRepository exportDetailRepository;

    public ExportDetailService(
            ExportDetailRepository exportDetailRepository) {
        this.exportDetailRepository = exportDetailRepository;
    }

    // CREATE
    public ExportDetail create(ExportDetail detail) {
        return exportDetailRepository.save(detail);
    }

    // READ ALL
    public List<ExportDetail> getAll() {
        return exportDetailRepository.findAll();
    }

    // READ BY ID
    public ExportDetail getById(Long id) {
        return exportDetailRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Không tìm thấy chi tiết phiếu xuất với id: " + id));
    }

    // UPDATE
    public ExportDetail update(Long id, ExportDetail detail) {
        ExportDetail existing = getById(id);

        existing.setReceiptId(detail.getReceiptId());
        existing.setProductId(detail.getProductId());
        existing.setQuantity(detail.getQuantity());

        return exportDetailRepository.save(existing);
    }

    // DELETE
    public void delete(Long id) {
        ExportDetail existing = getById(id);
        exportDetailRepository.delete(existing);
    }
}