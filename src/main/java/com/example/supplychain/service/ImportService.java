package com.example.supplychain.service;

import com.example.supplychain.dto.ImportRequest;
import com.example.supplychain.exception.ResourceNotFoundException;
import com.example.supplychain.repository.ProductRepository;
import com.example.supplychain.repository.SupplierRepository;
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

    @Autowired
    private SupplierRepository supplierRepository;

    @Transactional
    public String processImport(ImportRequest request) {
        if (!productRepository.existsById(request.getProductId())) {
            throw new ResourceNotFoundException("Sản phẩm không tồn tại với ID: " + request.getProductId());
        }

        if (!warehouseRepository.existsById(request.getWarehouseId())) {
            throw new ResourceNotFoundException("Kho hàng không tồn tại với ID: " + request.getWarehouseId());
        }

        if (!supplierRepository.existsById(request.getSupplierId())) {
            throw new ResourceNotFoundException("Nhà cung cấp không tồn tại với ID: " + request.getSupplierId());
        }

        return "Nhập kho thành công cho sản phẩm ID " + request.getProductId() + " tại kho ID " + request.getWarehouseId();
    }
}