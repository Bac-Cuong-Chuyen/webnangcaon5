package com.example.supplychain.controller;

import java.time.LocalDate;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.supplychain.service.TransferReportService;
import com.example.supplychain.service.TransferReportService.ReportResponse;

@RestController
@RequestMapping("/api/reports")
public class TransferReportController {

    private final TransferReportService service;

    public TransferReportController(TransferReportService service) {
        this.service = service;
    }

    @GetMapping("/transfers")
    public ReportResponse transfers(
            @RequestParam("fromDate")
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate fromDate,

            @RequestParam("toDate")
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate toDate) {

        return service.report(fromDate, toDate);
    }
}