package com.example.supplychain.config;

import com.example.supplychain.repository.UserAccountRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    // Cho Spring Security biết cách tìm user trong bảng user_account khi đăng nhập
    @Bean
    public UserDetailsService userDetailsService(UserAccountRepository userAccountRepository) {
        return username -> userAccountRepository.findByUsername(username)
                .map(account -> User.withUsername(account.getUsername())
                        .password(account.getPassword())
                        .roles(account.getRole() != null ? account.getRole().name() : "WAREHOUSE_STAFF")
                        .build())
                .orElseThrow(() -> new UsernameNotFoundException("Không tìm thấy user: " + username));
    }

    // AuthenticationManager dùng UserDetailsService + BCrypt ở trên
    @Bean
    public AuthenticationManager authenticationManager(UserDetailsService userDetailsService,
                                                       PasswordEncoder passwordEncoder) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder);
        return new ProviderManager(provider);
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())

            // Cho phép tự do /api/auth/** (đăng ký/đăng nhập) và h2-console.
            // Các API còn lại trong /api/** bắt buộc phải đăng nhập (TC08).
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/auth/**", "/h2-console/**").permitAll()
                .requestMatchers("/api/**").authenticated()
                .anyRequest().permitAll()
            );

        http.headers(headers -> headers.frameOptions(frame -> frame.disable()));

        return http.build();
    }
}