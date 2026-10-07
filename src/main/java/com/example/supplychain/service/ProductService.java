package com.example.supplychain.service;

import java.util.List;

import org.springframework.stereotype.Service;

import com.example.supplychain.entity.Product;
import com.example.supplychain.repository.ProductRepository;

@Service
public class ProductService {

    private final ProductRepository productRepository;

    public ProductService(ProductRepository productRepository) {
        this.productRepository = productRepository;
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
    public void deleteProduct(Long id) {

        Product existingProduct = productRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Product not found"));

        productRepository.delete(existingProduct);
    }
}