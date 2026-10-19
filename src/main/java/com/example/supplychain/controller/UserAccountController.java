package com.example.supplychain.controller;

import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.transaction.annotation.Transactional;

import com.example.supplychain.entity.Role;
import com.example.supplychain.entity.UserAccount;
import com.example.supplychain.exception.ResourceNotFoundException;
import com.example.supplychain.repository.UserAccountRepository;
import com.example.supplychain.service.AuditLogService;

@RestController
@RequestMapping("/api/user-accounts")
public class UserAccountController {

    private final UserAccountRepository userAccountRepository;
    private final AuditLogService auditLogService;
    private final PasswordEncoder passwordEncoder;

    public UserAccountController(UserAccountRepository userAccountRepository,
                                 AuditLogService auditLogService,
                                 PasswordEncoder passwordEncoder) {
        this.userAccountRepository = userAccountRepository;
        this.auditLogService = auditLogService;
        this.passwordEncoder = passwordEncoder;
    }

    @GetMapping
    @Transactional(readOnly = true)
    public ResponseEntity<List<Map<String, Object>>> getAll() {
        List<Map<String, Object>> response = userAccountRepository.findAll().stream()
                .map(UserAccountController::safeAccount)
                .toList();
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    @Transactional(readOnly = true)
    public ResponseEntity<Map<String, Object>> getById(@PathVariable Long id) {
        UserAccount account = userAccountRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("UserAccount khong ton tai: " + id));
        return ResponseEntity.ok(safeAccount(account));
    }

    @PutMapping("/{id}")
    public ResponseEntity<Map<String, Object>> update(@PathVariable Long id,
                                                       @RequestBody UserAccount request) {
        UserAccount existing = userAccountRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("UserAccount khong ton tai: " + id));
        if (request == null || request.getUsername() == null || request.getUsername().isBlank()
                || request.getRole() == null) {
            throw new IllegalArgumentException("Username va role khong hop le");
        }
        Role oldRole = existing.getRole();
        existing.setUsername(request.getUsername().trim());
        existing.setRole(request.getRole());
        if (request.getPassword() != null && !request.getPassword().isBlank()) {
            existing.setPassword(passwordEncoder.encode(request.getPassword()));
        }
        UserAccount saved = userAccountRepository.save(existing);
        if (oldRole != saved.getRole()) {
            String actor = SecurityContextHolder.getContext().getAuthentication().getName();
            auditLogService.record(actor, "ROLE_CHANGE",
                    "UserAccount:" + saved.getId() + "; oldRole=" + oldRole
                            + "; newRole=" + saved.getRole());
        }
        // Never null out the managed JPA entity password just to hide it in JSON.
        return ResponseEntity.ok(safeAccount(saved));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<String> delete(@PathVariable Long id) {
        UserAccount existing = userAccountRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("UserAccount khong ton tai: " + id));
        userAccountRepository.delete(existing);
        return ResponseEntity.ok("UserAccount deleted successfully");
    }

    private static Map<String, Object> safeAccount(UserAccount account) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("id", account.getId());
        response.put("username", account.getUsername());
        response.put("password", null);
        response.put("role", account.getRole());
        return response;
    }
}
