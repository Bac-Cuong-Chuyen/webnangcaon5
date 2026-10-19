package com.example.supplychain.service;

import com.example.supplychain.dto.LoginRequest;
import com.example.supplychain.dto.RegisterRequest;
import com.example.supplychain.entity.Role;
import com.example.supplychain.entity.UserAccount;
import com.example.supplychain.exception.BusinessConflictException;
import com.example.supplychain.repository.UserAccountRepository;

import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private static final String LOGIN_ERROR = "Sai username hoặc password";

    private final UserAccountRepository userAccountRepository;
    private final PasswordEncoder passwordEncoder;

    public AuthService(
            UserAccountRepository userAccountRepository,
            PasswordEncoder passwordEncoder) {
        this.userAccountRepository = userAccountRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public UserAccount register(RegisterRequest request) {
        if (request == null
                || request.getUsername() == null
                || request.getUsername().isBlank()
                || request.getPassword() == null
                || request.getPassword().isBlank()) {
            throw new IllegalArgumentException(
                    "Username và password không được để trống");
        }

        String username = request.getUsername().trim();

        if (userAccountRepository.existsByUsername(username)) {
            throw new BusinessConflictException("Username đã tồn tại");
        }

        UserAccount user = new UserAccount();
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setRole(Role.WAREHOUSE_STAFF);

        return userAccountRepository.save(user);
    }

    public UserAccount login(LoginRequest request) {
        if (request == null
                || request.getUsername() == null
                || request.getUsername().isBlank()
                || request.getPassword() == null) {
            throw new BadCredentialsException(LOGIN_ERROR);
        }

        UserAccount user = userAccountRepository
                .findByUsername(request.getUsername().trim())
                .orElseThrow(() ->
                        new BadCredentialsException(LOGIN_ERROR));

        if (user.getPassword() == null
                || !passwordEncoder.matches(
                        request.getPassword(), user.getPassword())) {
            throw new BadCredentialsException(LOGIN_ERROR);
        }

        return user;
    }
}