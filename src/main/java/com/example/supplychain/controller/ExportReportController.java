package com.example.supplychain.controller;

import java.time.LocalDate;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.supplychain.service.ExportReportService;
import com.example.supplychain.service.ExportReportService.ReportResponse;

@RestController
@RequestMapping("/api/reports")
public class ExportReportController {

    private final ExportReportService service;

    public ExportReportController(ExportReportService service) {
        this.service = service;
    }

    @GetMapping("/exports")
    public ReportResponse exports(
            @RequestParam("fromDate")
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate fromDate,

            @RequestParam("toDate")
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate toDate) {

        return service.report(fromDate, toDate);
    }
}