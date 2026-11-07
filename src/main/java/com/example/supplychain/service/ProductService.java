package com.example.supplychain.service;
import org.springframework.transaction.annotation.Transactional;
import com.example.supplychain.util.PageLimits;
import org.springframework.data.domain.Slice;

import java.util.List;

import org.springframework.stereotype.Service;

import com.example.supplychain.entity.Product;
import com.example.supplychain.repository.ProductRepository;

@Service
public class ProductService {

    private final ProductRepository productRepository;

    private final com.example.supplychain.repository.InventoryRepository inventory;
    private final com.example.supplychain.repository.ImportDetailRepository imports;
    private final com.example.supplychain.repository.ExportDetailRepository exports;
    private final com.example.supplychain.repository.TransferDetailRepository transfers;

    public ProductService(ProductRepository productRepository,
            com.example.supplychain.repository.InventoryRepository inventory,
            com.example.supplychain.repository.ImportDetailRepository imports,
            com.example.supplychain.repository.ExportDetailRepository exports,
            com.example.supplychain.repository.TransferDetailRepository transfers) {
        this.productRepository = productRepository;
        this.inventory = inventory;
        this.imports = imports;
        this.exports = exports;
        this.transfers = transfers;
    }

    // CREATE
    public Product createProduct(Product product) {
        return productRepository.save(product);
    }

    // READ ALL
    public List<Product> getAllProducts() {
        return productRepository.findAll();
    }

    // READ ONE
    public Product getProductById(Long id) {
        return productRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Product not found"));
    }

    // UPDATE
    public Product updateProduct(Long id, Product product) {

        Product existingProduct = productRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Product not found"));

        existingProduct.setSku(product.getSku());
        existingProduct.setName(product.getName());
        existingProduct.setUnit(product.getUnit());
        existingProduct.setPrice(product.getPrice());

        return productRepository.save(existingProduct);
    }

    // DELETE
    @Transactional
    public void deleteProduct(Long id) {

        Product existingProduct = productRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Product not found"));

        if (inventory.existsByProductId(id) || imports.existsByProductId(id)
                || exports.existsByProductId(id) || transfers.existsByProductId(id)) {
            throw new com.example.supplychain.exception.BusinessConflictException(
                    "Khong the xoa san pham da co ton kho hoac chung tu");
        }
        productRepository.delete(existingProduct);
    }

    @Transactional(readOnly = true)
    public Slice<Product> getPage(int page, int size) {
        return productRepository.findAll(PageLimits.of(page, size));
    }
}