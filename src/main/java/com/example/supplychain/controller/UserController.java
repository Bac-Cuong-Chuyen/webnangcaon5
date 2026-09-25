package com.example.supplychain.controller;

import com.example.supplychain.entity.UserAccount;
import com.example.supplychain.repository.UserAccountRepository;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserAccountRepository userAccountRepository;

    public UserController(UserAccountRepository userAccountRepository) {
        this.userAccountRepository = userAccountRepository;
    }

    @GetMapping("/{username}")
    public ResponseEntity<?> getUser(
            @PathVariable String username,
            Authentication authentication) {

        // Username đang đăng nhập
        String currentUsername = authentication.getName();

        // Chặn truy cập dữ liệu của tài khoản khác
        if (!currentUsername.equals(username)) {
            return ResponseEntity.status(403)
                    .body("Bạn không có quyền truy cập dữ liệu tài khoản này");
        }

        UserAccount user = userAccountRepository
                .findByUsername(username)
                .orElse(null);

        if (user == null) {
            return ResponseEntity.notFound().build();
        }

        // Không trả password
        user.setPassword(null);

        return ResponseEntity.ok(user);
    }
}