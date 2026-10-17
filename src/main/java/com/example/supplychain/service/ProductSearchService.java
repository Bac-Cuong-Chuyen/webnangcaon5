package com.example.supplychain.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import jakarta.persistence.criteria.Predicate;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.supplychain.entity.Product;
import com.example.supplychain.repository.ProductRepository;

@Service
public class ProductSearchService {

    public static final int MAX_PAGE_SIZE = 100;

    private static final Set<String> SORT_FIELDS =
            Set.of("id", "sku", "name", "unit", "price");

    private final ProductRepository productRepository;

    public ProductSearchService(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    @Transactional(readOnly = true)
    public Page<Product> search(
            String keyword, String unit,
            BigDecimal minPrice, BigDecimal maxPrice,
            int page, int size, String sortBy, String direction) {

        if (page < 0 || size <= 0) {
            throw new IllegalArgumentException(
                    "page phai >= 0 va size phai > 0");
        }

        if ((minPrice != null && minPrice.signum() < 0)
                || (maxPrice != null && maxPrice.signum() < 0)) {
            throw new IllegalArgumentException(
                    "Gia loc khong duoc am");
        }

        if (minPrice != null && maxPrice != null
                && minPrice.compareTo(maxPrice) > 0) {
            throw new IllegalArgumentException(
                    "minPrice khong duoc lon hon maxPrice");
        }

        if (!SORT_FIELDS.contains(sortBy)) {
            throw new IllegalArgumentException(
                    "sortBy chi nhan id, sku, name, unit, price");
        }

        if (!"asc".equalsIgnoreCase(direction)
                && !"desc".equalsIgnoreCase(direction)) {
            throw new IllegalArgumentException(
                    "direction chi nhan asc hoac desc");
        }

        Sort sort = Sort.by(
                Sort.Direction.fromString(direction), sortBy);

        if (!"id".equals(sortBy)) {
            sort = sort.and(Sort.by(Sort.Direction.ASC, "id"));
        }

        PageRequest pageable = PageRequest.of(
                page, Math.min(size, MAX_PAGE_SIZE), sort);

        Specification<Product> specification = (root, query, cb) -> {
            List<Predicate> conditions = new ArrayList<>();

            if (keyword != null && !keyword.isBlank()) {
                String pattern = "%" + escapeLike(
                        keyword.trim().toLowerCase(Locale.ROOT)) + "%";

                conditions.add(cb.or(
                        cb.like(
                                cb.lower(root.<String>get("name")),
                                pattern, '!'),
                        cb.like(
                                cb.lower(root.<String>get("sku")),
                                pattern, '!')
                ));
            }

            if (unit != null && !unit.isBlank()) {
                conditions.add(cb.equal(
                        cb.lower(root.<String>get("unit")),
                        unit.trim().toLowerCase(Locale.ROOT)));
            }

            if (minPrice != null) {
                conditions.add(cb.greaterThanOrEqualTo(
                        root.<BigDecimal>get("price"), minPrice));
            }

            if (maxPrice != null) {
                conditions.add(cb.lessThanOrEqualTo(
                        root.<BigDecimal>get("price"), maxPrice));
            }

            return cb.and(conditions.toArray(new Predicate[0]));
        };

        return productRepository.findAll(specification, pageable);
    }

    private static String escapeLike(String value) {
        return value.replace("!", "!!")
                .replace("%", "!%")
                .replace("_", "!_");
    }
}