package com.example.supplychain.controller;

import java.util.LinkedHashMap;
import java.util.Map;
import com.example.supplychain.config.ReportCacheInvalidation;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

// /api/admin/** is protected by the existing ADMIN rule in SecurityConfig.
@RestController
@ConditionalOnProperty(prefix = "app.reporting.cache", name = "enabled", havingValue = "true")
public class ReportCacheDiagnosticsController {
    private final CacheManager cacheManager;
    private final ReportCacheInvalidation invalidation;

    public ReportCacheDiagnosticsController(CacheManager cacheManager, ReportCacheInvalidation invalidation) {
        this.cacheManager = cacheManager;
        this.invalidation = invalidation;
    }

    @GetMapping("/api/admin/report-cache")
    public Map<String, Object> reportCache() {
        Cache cache = cacheManager.getCache("exportReports");
        if (!(cache instanceof CaffeineCache caffeine)) {
            throw new IllegalStateException("Khong tim thay Caffeine cache exportReports");
        }
        var nativeCache = caffeine.getNativeCache();
        var stats = nativeCache.stats();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("cache", "exportReports");
        result.put("generation", invalidation.generation());
        result.put("estimatedSize", nativeCache.estimatedSize());
        result.put("hitCount", stats.hitCount());
        result.put("missCount", stats.missCount());
        result.put("loadSuccessCount", stats.loadSuccessCount());
        result.put("loadFailureCount", stats.loadFailureCount());
        result.put("evictionCount", stats.evictionCount());
        return result;
    }
}
