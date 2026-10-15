package com.example.supplychain.service;

import java.time.LocalDate;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TransferReportService {

    private final JdbcTemplate jdbc;

    public TransferReportService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public record ReportRow(
            String fromWarehouseCode,
            String toWarehouseCode,
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
                SELECT
                    source.code AS from_code,
                    destination.code AS to_code,
                    SUM(CAST(d.quantity AS BIGINT)) AS total_quantity
                FROM stock_transfer t
                JOIN transfer_detail d ON d.transfer_id = t.id
                JOIN warehouse source ON source.id = t.from_warehouse_id
                JOIN warehouse destination ON destination.id = t.to_warehouse_id
                WHERE t.status = 'COMPLETED'
                  AND t.transfer_date BETWEEN ? AND ?
                GROUP BY source.id, source.code,
                         destination.id, destination.code
                ORDER BY source.code, destination.code,
                         source.id, destination.id
                """;

        List<ReportRow> rows = jdbc.query(
                sql,
                (rs, rowNum) -> new ReportRow(
                        rs.getString("from_code"),
                        rs.getString("to_code"),
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