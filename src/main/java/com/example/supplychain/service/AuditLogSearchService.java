package com.example.supplychain.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.criteria.Predicate;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.supplychain.entity.AuditLog;
import com.example.supplychain.repository.AuditLogRepository;

@Service
public class AuditLogSearchService {

    private final AuditLogRepository repository;

    public AuditLogSearchService(AuditLogRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public Page<AuditLog> search(
            String username,
            String action,
            LocalDateTime fromTime,
            LocalDateTime toTime,
            int page,
            int size) {

        if (page < 0 || size <= 0) {
            throw new IllegalArgumentException(
                    "page phai >= 0 va size phai > 0");
        }

        if (fromTime != null && toTime != null
                && fromTime.isAfter(toTime)) {
            throw new IllegalArgumentException(
                    "Thoi diem bat dau phai <= thoi diem ket thuc");
        }

        Specification<AuditLog> specification = (root, query, cb) -> {

            List<Predicate> conditions = new ArrayList<>();

            if (username != null && !username.isBlank()) {
                conditions.add(cb.equal(
                        root.<String>get("username"),
                        username.trim()));
            }

            if (action != null && !action.isBlank()) {
                conditions.add(cb.equal(
                        root.<String>get("action"),
                        action.trim()));
            }

            if (fromTime != null) {
                conditions.add(cb.greaterThanOrEqualTo(
                        root.<LocalDateTime>get("createdAt"),
                        fromTime));
            }

            if (toTime != null) {
                conditions.add(cb.lessThanOrEqualTo(
                        root.<LocalDateTime>get("createdAt"),
                        toTime));
            }

            return cb.and(conditions.toArray(new Predicate[0]));
        };

        Sort sort = Sort.by(
                Sort.Order.desc("createdAt"),
                Sort.Order.desc("id"));

        PageRequest pageable = PageRequest.of(
                page, Math.min(size, 100), sort);

        return repository.findAll(specification, pageable);
    }
}