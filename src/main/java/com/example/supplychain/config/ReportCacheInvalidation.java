package com.example.supplychain.config;

import java.io.IOException;
import java.time.LocalDate;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component("reportCacheInvalidation")
@Order(Ordered.LOWEST_PRECEDENCE)
@ConditionalOnProperty(prefix = "app.reporting.cache", name = "enabled", havingValue = "true")
public class ReportCacheInvalidation extends OncePerRequestFilter {
    private static final Set<String> READ_METHODS = Set.of("GET", "HEAD", "OPTIONS", "TRACE");
    private final AtomicLong generation = new AtomicLong();
    private final CacheManager cacheManager;

    public ReportCacheInvalidation(CacheManager cacheManager) {
        this.cacheManager = cacheManager;
    }

    public String exportKey(LocalDate fromDate, LocalDate toDate) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String username = authentication == null ? "internal" : authentication.getName();
        // The generation prevents an in-flight pre-write read from repopulating a usable stale key.
        return generation.get() + ":" + username.length() + ":" + username + ":" + fromDate + ":" + toDate;
    }

    public long generation() { return generation.get(); }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        return READ_METHODS.contains(request.getMethod())
                || !path.startsWith("/api/") || path.startsWith("/api/auth/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        try {
            filterChain.doFilter(request, response);
        } finally {
            // Current application writes commit/rollback in the service/repository before returning.
            // Also evict on failed writes: a failed response must not conceal a previously committed change.
            generation.incrementAndGet();
            Cache cache = cacheManager.getCache("exportReports");
            if (cache != null) cache.clear();
        }
    }
}
