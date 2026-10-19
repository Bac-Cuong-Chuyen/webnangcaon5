package com.example.supplychain.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import com.example.supplychain.service.AuditLogService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Configuration
public class AuditLoggingConfiguration implements WebMvcConfigurer {

    private static final Logger log = LoggerFactory.getLogger(AuditLoggingConfiguration.class);
    private final AuditLogService auditLogService;

    public AuditLoggingConfiguration(AuditLogService auditLogService) {
        this.auditLogService = auditLogService;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new HandlerInterceptor() {
            @Override
            public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                    Object handler, Exception ex) {
                if (!"DELETE".equalsIgnoreCase(request.getMethod()) || ex != null
                        || response.getStatus() < 200 || response.getStatus() >= 400) {
                    return;
                }
                Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
                if (authentication == null || !authentication.isAuthenticated()
                        || authentication instanceof AnonymousAuthenticationToken) {
                    return;
                }
                try {
                    auditLogService.record(authentication.getName(), "DATA_DELETE", request.getRequestURI());
                } catch (RuntimeException auditFailure) {
                    log.error("Unable to persist audit event for a completed DELETE request", auditFailure);
                }
            }
        });
    }
}
