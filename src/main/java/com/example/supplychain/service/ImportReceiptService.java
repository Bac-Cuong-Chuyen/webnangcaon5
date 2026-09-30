package com.example.supplychain.service;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.example.supplychain.entity.ImportReceipt;
import com.example.supplychain.repository.ImportReceiptRepository;

@Service
public class ImportReceiptService {

    private final ImportReceiptRepository importReceiptRepository;

    public ImportReceiptService(
            ImportReceiptRepository importReceiptRepository) {
        this.importReceiptRepository = importReceiptRepository;
    }

    // CREATE
    public ImportReceipt create(ImportReceipt receipt) {
        return importReceiptRepository.save(receipt);
    }

    // READ ALL
    public List<ImportReceipt> getAll() {
        return importReceiptRepository.findAll();
    }

    // READ BY ID
    public ImportReceipt getById(Long id) {
        return importReceiptRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Không tìm thấy phiếu nhập với id: " + id));
    }

    // UPDATE
    public ImportReceipt update(
            Long id,
            ImportReceipt receipt) {

        ImportReceipt existing = getById(id);

        existing.setReceiptCode(receipt.getReceiptCode());
        existing.setReceiptDate(receipt.getReceiptDate());
        existing.setStatus(receipt.getStatus());

        return importReceiptRepository.save(existing);
    }

    // DELETE
    public void delete(Long id) {
        ImportReceipt existing = getById(id);
        importReceiptRepository.delete(existing);
    }
}