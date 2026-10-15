package com.example.supplychain.service;

import java.time.LocalDateTime;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.supplychain.entity.AuditLog;
import com.example.supplychain.repository.AuditLogRepository;

@Service
public class AuditLogService {

    private final AuditLogRepository repository;

    public AuditLogService(AuditLogRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public void record(
            String username,
            String action,
            String entityName) {

        AuditLog log = new AuditLog();
        log.setUsername(username);
        log.setAction(action);
        log.setEntityName(entityName);
        log.setCreatedAt(LocalDateTime.now());

        repository.save(log);
    }
}