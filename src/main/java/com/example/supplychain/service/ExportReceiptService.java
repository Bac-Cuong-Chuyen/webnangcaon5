package com.example.supplychain.service;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.example.supplychain.entity.ExportReceipt;
import com.example.supplychain.repository.ExportReceiptRepository;

@Service
public class ExportReceiptService {

    private final ExportReceiptRepository exportReceiptRepository;

    public ExportReceiptService(
            ExportReceiptRepository exportReceiptRepository) {
        this.exportReceiptRepository = exportReceiptRepository;
    }

    // CREATE
    public ExportReceipt create(ExportReceipt receipt) {
        return exportReceiptRepository.save(receipt);
    }

    // READ ALL
    public List<ExportReceipt> getAll() {
        return exportReceiptRepository.findAll();
    }

    // READ BY ID
    public ExportReceipt getById(Long id) {
        return exportReceiptRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Không tìm thấy phiếu xuất với id: " + id));
    }

    // UPDATE
    public ExportReceipt update(
            Long id,
            ExportReceipt receipt) {

        ExportReceipt existing = getById(id);

        existing.setReceiptCode(receipt.getReceiptCode());
        existing.setReceiptDate(receipt.getReceiptDate());
        existing.setStatus(receipt.getStatus());

        return exportReceiptRepository.save(existing);
    }

    // DELETE
    public void delete(Long id) {
        ExportReceipt existing = getById(id);
        exportReceiptRepository.delete(existing);
    }
}