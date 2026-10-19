package com.example.supplychain.config;

import java.time.Duration;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableCaching
@ConditionalOnProperty(prefix = "app.reporting.cache", name = "enabled", havingValue = "true")
public class ReportCacheConfiguration {
    @Bean
    public CacheManager cacheManager(
            @Value("${app.reporting.cache.maximum-size:100}") long maximumSize,
            @Value("${app.reporting.cache.ttl-seconds:60}") long ttlSeconds) {
        if (maximumSize <= 0 || ttlSeconds <= 0) {
            throw new IllegalArgumentException("Cache size va TTL phai duong");
        }
        CaffeineCacheManager manager = new CaffeineCacheManager("exportReports");
        manager.setCaffeine(Caffeine.newBuilder()
                .maximumSize(maximumSize)
                .expireAfterWrite(Duration.ofSeconds(ttlSeconds))
                .recordStats());
        return manager;
    }
}
