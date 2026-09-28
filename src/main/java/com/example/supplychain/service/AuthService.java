package com.example.supplychain.service;

import org.springframework.stereotype.Service;

import com.example.supplychain.dto.LoginRequest;
import com.example.supplychain.dto.RegisterRequest;
import com.example.supplychain.entity.Role;
import com.example.supplychain.entity.UserAccount;
import com.example.supplychain.repository.UserAccountRepository;

@Service
public class AuthService {

    private final UserAccountRepository userAccountRepository;

    public AuthService(UserAccountRepository userAccountRepository) {
        this.userAccountRepository = userAccountRepository;
    }

    public UserAccount register(RegisterRequest request) {

        if (userAccountRepository.existsByUsername(request.getUsername())) {
            throw new RuntimeException("Username already exists");
        }

        UserAccount user = new UserAccount();

        user.setUsername(request.getUsername());
        user.setPassword(request.getPassword());

        // Tài khoản đăng ký mới mặc định là nhân viên kho
        user.setRole(Role.WAREHOUSE_STAFF);

        return userAccountRepository.save(user);
    }

    public UserAccount login(LoginRequest request) {

        UserAccount user = userAccountRepository
                .findByUsername(request.getUsername())
                .orElseThrow(() -> new RuntimeException("Username not found"));

        if (!user.getPassword().equals(request.getPassword())) {
            throw new RuntimeException("Wrong password");
        }

        return user;
    }
}