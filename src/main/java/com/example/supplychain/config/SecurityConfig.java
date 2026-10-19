package com.example.supplychain.config;

import com.example.supplychain.entity.UserAccount;
import com.example.supplychain.repository.UserAccountRepository;

import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpMethod;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy;

@Configuration
public class SecurityConfig {

    private final UserAccountRepository userAccountRepository;

    public SecurityConfig(UserAccountRepository userAccountRepository) {
        this.userAccountRepository = userAccountRepository;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public UserDetailsService userDetailsService() {
        return username -> {
            UserAccount account = userAccountRepository
                    .findByUsername(username)
                    .orElseThrow(() ->
                            new UsernameNotFoundException("Khong tim thay nguoi dung"));

            if (account.getRole() == null) {
                throw new UsernameNotFoundException("Tai khoan chua duoc cap vai tro");
            }

            return User.builder()
                    .username(account.getUsername())
                    .password(account.getPassword())
                    .roles(account.getRole().name())
                    .build();
        };
    }

    @Bean
    public DaoAuthenticationProvider authenticationProvider(
            UserDetailsService userDetailsService,
            PasswordEncoder passwordEncoder) {

        DaoAuthenticationProvider provider =
                new DaoAuthenticationProvider(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder);
        return provider;
    }

    @Bean
    public AuthenticationManager authenticationManager(
            AuthenticationConfiguration configuration) throws Exception {
        return configuration.getAuthenticationManager();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http)
            throws Exception {

        http
                .csrf(Customizer.withDefaults())
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/api/auth/csrf",
                                "/api/auth/register",
                                "/api/auth/login",
                                "/api/auth/logout",
                                "/h2-console/**"
                        ).permitAll()
                        .requestMatchers("/api/admin/**")
                                .hasRole("ADMIN")
                        .requestMatchers("/api/user-accounts", "/api/user-accounts/**")
                                .hasRole("ADMIN")
                        .requestMatchers("/api/audit-logs", "/api/audit-logs/**")
                                .hasRole("ADMIN")
                        .requestMatchers("/api/files/**")
                                .hasAnyRole("ADMIN", "WAREHOUSE_MANAGER", "WAREHOUSE_STAFF")
                        .requestMatchers("/api/reports", "/api/reports/**")
                                .hasAnyRole("WAREHOUSE_MANAGER", "DIRECTOR")
                        .requestMatchers("/api/transfers", "/api/transfers/**")
                                .hasRole("WAREHOUSE_MANAGER")
                        .requestMatchers(HttpMethod.GET,
                                "/api/products", "/api/products/**",
                                "/api/warehouses", "/api/warehouses/**")
                                .authenticated()
                        .requestMatchers(HttpMethod.HEAD,
                                "/api/products", "/api/products/**",
                                "/api/warehouses", "/api/warehouses/**")
                                .authenticated()
                        .requestMatchers(
                                "/api/products", "/api/products/**",
                                "/api/warehouses", "/api/warehouses/**")
                                .hasAnyRole("ADMIN", "WAREHOUSE_MANAGER")
                        .requestMatchers(HttpMethod.POST,
                                "/api/import/*/approve", "/api/imports/*/approve", "/api/exports/*/approve")
                                .hasRole("WAREHOUSE_MANAGER")
                        .requestMatchers("/api/import", "/api/import/**", "/api/imports", "/api/imports/**",
                                "/api/exports", "/api/exports/**")
                                .hasAnyRole("ADMIN", "WAREHOUSE_MANAGER", "WAREHOUSE_STAFF")
                        .requestMatchers(HttpMethod.GET, "/api/inventories", "/api/inventories/**")
                                .authenticated()
                        .requestMatchers(HttpMethod.PUT, "/api/inventories/*")
                                .hasRole("WAREHOUSE_MANAGER")
                        .requestMatchers("/api/inventories", "/api/inventories/**").denyAll()
                        .requestMatchers(HttpMethod.GET,
                                "/api/import-receipts", "/api/import-receipts/**",
                                "/api/export-receipts", "/api/export-receipts/**",
                                "/api/import-details", "/api/import-details/**",
                                "/api/export-details", "/api/export-details/**")
                                .authenticated()
                        .requestMatchers(
                                "/api/import-receipts", "/api/import-receipts/**",
                                "/api/export-receipts", "/api/export-receipts/**",
                                "/api/import-details", "/api/import-details/**",
                                "/api/export-details", "/api/export-details/**")
                                .hasAnyRole("ADMIN", "WAREHOUSE_MANAGER", "WAREHOUSE_STAFF")
                        .requestMatchers("/api/**").authenticated()
                        .anyRequest().permitAll()
                )
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint((request, response, ex) -> {
                            response.setStatus(401);
                            response.setContentType("application/json");
                            response.getWriter().write(
                                    "{\"status\":401,\"message\":\"Unauthorized\"}");
                        })
                )
                                                .headers(headers -> headers
                        .contentSecurityPolicy(csp -> csp.policyDirectives(
                                "default-src 'self'; object-src 'none'; frame-ancestors 'self'; base-uri 'self'"))
                        .referrerPolicy(referrer -> referrer.policy(
                                ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN))
                        .frameOptions(frame -> frame.sameOrigin())
                        .httpStrictTransportSecurity(hsts -> hsts
                                .includeSubDomains(true)
                                .maxAgeInSeconds(31536000))
                );

        return http.build();
    }
}
