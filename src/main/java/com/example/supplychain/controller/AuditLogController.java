package com.example.supplychain.controller;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.supplychain.entity.AuditLog;
import com.example.supplychain.service.AuditLogSearchService;

@RestController
@RequestMapping({
        "/api/admin/audit-logs",
        "/api/audit-logs"
})
public class AuditLogController {

    private final AuditLogSearchService searchService;

    public AuditLogController(AuditLogSearchService searchService) {
        this.searchService = searchService;
    }

    @GetMapping
    public SearchResponse search(
            @RequestParam(name = "username", required = false)
            String username,

            @RequestParam(name = "action", required = false)
            String action,

            @RequestParam(name = "fromTime", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            LocalDateTime fromTime,

            @RequestParam(name = "toTime", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            LocalDateTime toTime,

            @RequestParam(name = "page", defaultValue = "0")
            int page,

            @RequestParam(name = "size", defaultValue = "20")
            int size) {

        Page<AuditLog> result = searchService.search(
                username,
                action,
                fromTime,
                toTime,
                page,
                size
        );

        return new SearchResponse(
                result.getContent(),
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages(),
                result.isFirst(),
                result.isLast()
        );
    }

    public record SearchResponse(
            List<AuditLog> content,
            int page,
            int size,
            long totalElements,
            int totalPages,
            boolean first,
            boolean last
    ) {
    }
}