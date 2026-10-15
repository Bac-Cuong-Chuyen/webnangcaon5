package com.example.supplychain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import com.example.supplychain.entity.AuditLog;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {
}
