package com.example.supplychain.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.supplychain.entity.UserAccount;
import com.example.supplychain.repository.UserAccountRepository;

@RestController
@RequestMapping("/api/user-accounts")
public class UserAccountController {

    private final UserAccountRepository userAccountRepository;

    private final BCryptPasswordEncoder passwordEncoder =
            new BCryptPasswordEncoder();

    public UserAccountController(
            UserAccountRepository userAccountRepository) {
        this.userAccountRepository = userAccountRepository;
    }

    // READ ALL
    @GetMapping
    public ResponseEntity<List<UserAccount>> getAll() {
        List<UserAccount> users = userAccountRepository.findAll();

        users.forEach(user -> user.setPassword(null));

        return ResponseEntity.ok(users);
    }

    // READ BY ID
    @GetMapping("/{id}")
    public ResponseEntity<UserAccount> getById(
            @PathVariable Long id) {

        UserAccount user = userAccountRepository.findById(id)
                .orElseThrow(() -> new RuntimeException(
                        "Không tìm thấy UserAccount với id: " + id));

        user.setPassword(null);

        return ResponseEntity.ok(user);
    }

    // UPDATE
    @PutMapping("/{id}")
    public ResponseEntity<UserAccount> update(
            @PathVariable Long id,
            @RequestBody UserAccount user) {

        UserAccount existing =
                userAccountRepository.findById(id)
                        .orElseThrow(() -> new RuntimeException(
                                "Không tìm thấy UserAccount với id: " + id));

        existing.setUsername(user.getUsername());
        existing.setRole(user.getRole());

        if (user.getPassword() != null &&
                !user.getPassword().isBlank()) {

            existing.setPassword(
                    passwordEncoder.encode(user.getPassword()));
        }

        UserAccount saved =
                userAccountRepository.save(existing);

        saved.setPassword(null);

        return ResponseEntity.ok(saved);
    }

    // DELETE
    @DeleteMapping("/{id}")
    public ResponseEntity<String> delete(
            @PathVariable Long id) {

        UserAccount existing =
                userAccountRepository.findById(id)
                        .orElseThrow(() -> new RuntimeException(
                                "Không tìm thấy UserAccount với id: " + id));

        userAccountRepository.delete(existing);

        return ResponseEntity.ok(
                "UserAccount deleted successfully");
    }
}