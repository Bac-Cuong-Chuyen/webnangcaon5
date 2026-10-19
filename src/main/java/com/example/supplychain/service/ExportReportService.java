package com.example.supplychain.service;

import java.time.LocalDate;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ExportReportService {

    private final JdbcTemplate jdbc;

    public ExportReportService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public record ReportRow(
            LocalDate date,
            String sku,
            long totalQuantity) {
    }

    public record ReportResponse(
            LocalDate fromDate,
            LocalDate toDate,
            List<ReportRow> data,
            int totalRows,
            long totalQuantity) {
    }

    @Transactional(readOnly = true)
    public ReportResponse report(
            LocalDate fromDate,
            LocalDate toDate) {

        if (fromDate == null || toDate == null
                || fromDate.isAfter(toDate)) {
            throw new IllegalArgumentException(
                    "Ngay bat dau phai <= ngay ket thuc");
        }

        String sql = """
                SELECT r.receipt_date, p.sku,
                       SUM(CAST(d.quantity AS BIGINT)) AS total_quantity
                FROM export_receipt r
                JOIN export_detail d ON d.receipt_id = r.id
                JOIN product p ON p.id = d.product_id
                WHERE r.status IN ('COMPLETED', 'APPROVED')
                  AND r.receipt_date BETWEEN ? AND ?
                GROUP BY r.receipt_date, p.id, p.sku
                ORDER BY r.receipt_date, p.sku, p.id
                """;

        List<ReportRow> rows = jdbc.query(
                sql,
                (rs, rowNum) -> new ReportRow(
                        rs.getDate("receipt_date").toLocalDate(),
                        rs.getString("sku"),
                        rs.getLong("total_quantity")),
                java.sql.Date.valueOf(fromDate),
                java.sql.Date.valueOf(toDate));

        long total = rows.stream()
                .mapToLong(ReportRow::totalQuantity)
                .sum();

        return new ReportResponse(
                fromDate, toDate, rows, rows.size(), total);
    }
}
