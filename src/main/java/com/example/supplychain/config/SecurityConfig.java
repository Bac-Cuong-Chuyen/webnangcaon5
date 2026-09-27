package com.example.supplychain.config; // Nếu file nằm trong thư mục config thì sửa thành: package com.example.supplychain.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
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

    // Bổ sung Bean này để sửa triệt để lỗi sập AuthController khi startup
    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            // 1. Tắt CSRF để gửi request POST/PUT/DELETE từ Thunder Client không bị chặn
            .csrf(csrf -> csrf.disable())

            // 2. Cho phép truy cập tự do vào tất cả đường dẫn /api/** và h2-console
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/**", "/h2-console/**").permitAll()
                .anyRequest().permitAll()
            );

        // 3. Bỏ chặn frame để xem được H2 Console trên trình duyệt
        http.headers(headers -> headers.frameOptions(frame -> frame.disable()));

        return http.build();
    }
}