package com.example.supplychain.controller;

import java.util.Comparator;
import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.supplychain.entity.AuditLog;
import com.example.supplychain.repository.AuditLogRepository;

@RestController
@RequestMapping("/api/audit-logs")
public class AuditLogController {

    private final AuditLogRepository auditLogRepository;

    public AuditLogController(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    @GetMapping
    public ResponseEntity<List<AuditLog>> getAll() {
        List<AuditLog> logs = auditLogRepository.findAll().stream()
                .sorted(Comparator.comparing(AuditLog::getCreatedAt,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
        return ResponseEntity.ok(logs);
    }
}
