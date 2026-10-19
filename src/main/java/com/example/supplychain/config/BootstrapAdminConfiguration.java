package com.example.supplychain.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.example.supplychain.entity.Role;
import com.example.supplychain.entity.UserAccount;
import com.example.supplychain.repository.UserAccountRepository;

@Configuration
public class BootstrapAdminConfiguration {

    @Bean
    public ApplicationRunner bootstrapAdmin(
            UserAccountRepository repository,
            PasswordEncoder passwordEncoder,
            @Value("${app.security.bootstrap-admin-username:}") String username,
            @Value("${app.security.bootstrap-admin-password:}") String password) {
        return args -> {
            if (username == null || username.isBlank() || password == null || password.length() < 12) {
                return;
            }
            if (password.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72) {
                return;
            }
            String normalizedUsername = username.trim();
            if (repository.existsByUsername(normalizedUsername)) {
                return;
            }
            UserAccount admin = new UserAccount();
            admin.setUsername(normalizedUsername);
            admin.setPassword(passwordEncoder.encode(password));
            admin.setRole(Role.ADMIN);
            repository.save(admin);
        };
    }
}
