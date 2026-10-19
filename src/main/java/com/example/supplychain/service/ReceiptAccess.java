package com.example.supplychain.service;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import com.example.supplychain.exception.BusinessConflictException;

final class ReceiptAccess {
    private ReceiptAccess() {}
    private static Authentication authentication() {
        Authentication value = SecurityContextHolder.getContext().getAuthentication();
        if (value == null || !value.isAuthenticated() || value instanceof AnonymousAuthenticationToken)
            throw new AccessDeniedException("Authentication required");
        return value;
    }
    static boolean role(String role) {
        return authentication().getAuthorities().stream()
            .anyMatch(value -> value.getAuthority().equals("ROLE_" + role));
    }
    static String actor() { return authentication().getName(); }
    static void requireCreate() {
        if (!role("ADMIN") && !role("WAREHOUSE_MANAGER") && !role("WAREHOUSE_STAFF"))
            throw new AccessDeniedException("Receipt write denied");
    }
    static boolean canRead(String owner) {
        return role("ADMIN") || role("WAREHOUSE_MANAGER") || role("DIRECTOR")
            || (role("WAREHOUSE_STAFF") && actor().equals(owner));
    }
    static void requireRead(String owner) {
        if (!canRead(owner)) throw new AccessDeniedException("Receipt read denied");
    }
    static void requireEdit(String owner, String status) {
        requireCreate();
        if (role("WAREHOUSE_STAFF") && !actor().equals(owner))
            throw new AccessDeniedException("Receipt belongs to another user");
        if (!"DRAFT".equals(status))
            throw new BusinessConflictException("Chi duoc sua phieu DRAFT");
    }
    static void requireDelete(String owner, String status) {
        if (!role("ADMIN") && !role("WAREHOUSE_MANAGER"))
            throw new AccessDeniedException("Receipt delete denied");
        requireEdit(owner, status);
    }
    static void requireDifferentApprover(String creator) {
        if (creator == null || creator.isBlank())
            throw new BusinessConflictException("Phieu chua co nguoi tao de doi chieu");
        if (actor().equals(creator))
            throw new BusinessConflictException("Nguoi tao khong duoc tu duyet phieu");
    }
    static void requireManager() {
        if (!role("WAREHOUSE_MANAGER")) throw new AccessDeniedException("Manager required");
    }
}
