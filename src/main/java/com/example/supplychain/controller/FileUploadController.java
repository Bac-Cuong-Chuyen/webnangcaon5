package com.example.supplychain.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.example.supplychain.dto.FileUploadResponse;
import com.example.supplychain.service.AuditLogService;
import com.example.supplychain.service.FileUploadService;

@RestController
@RequestMapping("/api/files")
public class FileUploadController {

    private final FileUploadService fileUploadService;
    private final AuditLogService auditLogService;

    public FileUploadController(FileUploadService fileUploadService, AuditLogService auditLogService) {
        this.fileUploadService = fileUploadService;
        this.auditLogService = auditLogService;
    }

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<FileUploadResponse> upload(
            @RequestPart("file") MultipartFile file, Authentication authentication) {
        FileUploadResponse result = fileUploadService.store(file);
        auditLogService.record(authentication.getName(), "FILE_UPLOAD", result.fileName());
        return ResponseEntity.status(HttpStatus.CREATED).body(result);
    }
}
