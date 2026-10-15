package com.example.supplychain.controller;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.supplychain.entity.Product;
import com.example.supplychain.service.ProductSearchService;

@RestController
@RequestMapping("/api/products")
public class ProductSearchController {

    private final ProductSearchService searchService;

    public ProductSearchController(ProductSearchService searchService) {
        this.searchService = searchService;
    }

    @GetMapping("/search")
    public SearchResponse search(
            @RequestParam(name = "keyword", required = false)
            String keyword,
            @RequestParam(name = "unit", required = false)
            String unit,
            @RequestParam(name = "minPrice", required = false)
            BigDecimal minPrice,
            @RequestParam(name = "maxPrice", required = false)
            BigDecimal maxPrice,
            @RequestParam(name = "page", defaultValue = "0")
            int page,
            @RequestParam(name = "size", defaultValue = "20")
            int size,
            @RequestParam(name = "sortBy", defaultValue = "id")
            String sortBy,
            @RequestParam(name = "direction", defaultValue = "asc")
            String direction) {

        Page<Product> result = searchService.search(
                keyword, unit, minPrice, maxPrice,
                page, size, sortBy, direction);

        return new SearchResponse(
                result.getContent(),
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages(),
                result.isFirst(),
                result.isLast()
        );
    }

    public record SearchResponse(
            List<Product> content,
            int page,
            int size,
            long totalElements,
            int totalPages,
            boolean first,
            boolean last) {
    }
}