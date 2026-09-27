package com.example.supplychain.service;

import com.example.supplychain.dto.ImportRequest;
import com.example.supplychain.exception.ResourceNotFoundException;
import com.example.supplychain.repository.ProductRepository;
import com.example.supplychain.repository.WarehouseRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ImportService {

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private WarehouseRepository warehouseRepository;

    @Transactional
    public String processImport(ImportRequest request) {
        if (!productRepository.existsById(request.getProductId())) {
            throw new ResourceNotFoundException("Sản phẩm không tồn tại với ID: " + request.getProductId());
        }

        // Gọi đúng tên biến chữ 'w' viết thường ở đây:
        if (!warehouseRepository.existsById(request.getWarehouseId())) {
            throw new ResourceNotFoundException("Kho hàng không tồn tại với ID: " + request.getWarehouseId());
        }

        return "Nhập kho thành công cho sản phẩm ID " + request.getProductId() + " tại kho ID " + request.getWarehouseId();
    }
}