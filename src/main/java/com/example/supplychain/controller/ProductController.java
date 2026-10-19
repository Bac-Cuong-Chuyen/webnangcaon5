package com.example.supplychain.controller;
import org.springframework.web.bind.annotation.RequestParam;
import com.example.supplychain.util.PageLimits;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.supplychain.entity.Product;
import com.example.supplychain.service.ProductService;
import com.example.supplychain.service.AuditLogService;

@RestController
@RequestMapping("/api/products")
public class ProductController {

    private final ProductService productService;

    private final AuditLogService auditLogService;

    public ProductController(
            ProductService productService,
            AuditLogService auditLogService) {
        this.productService = productService;
        this.auditLogService = auditLogService;
    }

    // CREATE
    @PostMapping
    public ResponseEntity<Product> createProduct(
            @RequestBody Product product) {

        return ResponseEntity.ok(
                productService.createProduct(product)
        );
    }

    // READ ALL
    @GetMapping
    public ResponseEntity<List<Product>> getAllProducts(
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "20") int size) {
        return PageLimits.response(productService.getPage(page, size));
    }

    // READ ONE
    @GetMapping("/{id}")
    public ResponseEntity<Product> getProduct(
            @PathVariable Long id) {

        return ResponseEntity.ok(
                productService.getProductById(id)
        );
    }

    // UPDATE
    @PutMapping("/{id}")
    public ResponseEntity<Product> updateProduct(
            @PathVariable Long id,
            @RequestBody Product product) {

        return ResponseEntity.ok(
                productService.updateProduct(id, product)
        );
    }

    // DELETE
    @DeleteMapping("/{id}")
    public ResponseEntity<String> deleteProduct(
            @PathVariable Long id) {

        productService.deleteProduct(id);

        String actor = SecurityContextHolder
                .getContext()
                .getAuthentication()
                .getName();

        auditLogService.record(
                actor,
                "DELETE_PRODUCT",
                "Product:" + id);

        return ResponseEntity.ok(
                "Product deleted successfully"
        );
    }
}