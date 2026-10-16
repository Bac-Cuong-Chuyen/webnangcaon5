package com.example.supplychain.controller;

import com.example.supplychain.entity.Role;
import com.example.supplychain.entity.UserAccount;
import com.example.supplychain.repository.UserAccountRepository;
import com.example.supplychain.service.AuditLogService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import org.springframework.http.ResponseEntity;

import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final UserAccountRepository userAccountRepository;

    private final AuthenticationManager authenticationManager;

    private final AuditLogService auditLogService;

    private final BCryptPasswordEncoder passwordEncoder =
            new BCryptPasswordEncoder();

    private final SecurityContextRepository securityContextRepository =
            new HttpSessionSecurityContextRepository();

    public AuthController(
            UserAccountRepository userAccountRepository,
            AuthenticationManager authenticationManager,
            AuditLogService auditLogService) {

        this.userAccountRepository = userAccountRepository;
        this.authenticationManager = authenticationManager;
        this.auditLogService = auditLogService;
    }

    // =========================
    // ĐĂNG KÝ
    // =========================

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody UserAccount user) {

        if (userAccountRepository
                .findByUsername(user.getUsername())
                .isPresent()) {

            return ResponseEntity.badRequest()
                    .body("Username đã tồn tại");
        }

        user.setRole(Role.WAREHOUSE_STAFF);

        user.setPassword(
                passwordEncoder.encode(user.getPassword())
        );

        UserAccount savedUser =
                userAccountRepository.save(user);

        savedUser.setPassword(null);

        return ResponseEntity.ok(savedUser);
    }

    // =========================
    // ĐĂNG NHẬP
    // =========================

    @PostMapping("/login")
    public ResponseEntity<?> login(
            @RequestBody UserAccount user,
            HttpServletRequest request,
            HttpServletResponse response) {

        try {

            Authentication authentication =
                    authenticationManager.authenticate(
                            new UsernamePasswordAuthenticationToken(
                                    user.getUsername(),
                                    user.getPassword()
                            )
                    );

            // Đặt authentication vào SecurityContext
            SecurityContext context =
                    SecurityContextHolder.createEmptyContext();

            context.setAuthentication(authentication);

            SecurityContextHolder.setContext(context);

            // Lưu SecurityContext vào session
            securityContextRepository.saveContext(
                    context,
                    request,
                    response
            );

            UserAccount account =
                    userAccountRepository
                            .findByUsername(user.getUsername())
                            .orElseThrow();

            auditLogService.record(
                    authentication.getName(),
                    "LOGIN_SUCCESS",
                    "UserAccount:" + account.getId());

            account.setPassword(null);

            return ResponseEntity.ok(account);

        } catch (Exception e) {

            return ResponseEntity
                    .badRequest()
                    .body("Sai username hoặc password");
        }
    }

    // =========================
    // ĐĂNG XUẤT
    // =========================

    @PostMapping("/logout")
    public ResponseEntity<?> logout(
            HttpServletRequest request) {

        SecurityContextHolder.clearContext();

        HttpSession session = request.getSession(false);

        if (session != null) {
            session.invalidate();
        }

        return ResponseEntity.ok(
                "Đăng xuất thành công"
        );
    }
}