package com.example.supplychain;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import com.example.supplychain.service.ImportDetailService;
import com.example.supplychain.service.ExportDetailService;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:b09_detail_query_count;DB_CLOSE_DELAY=-1",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.sql.init.mode=never",
    "spring.jpa.properties.hibernate.generate_statistics=true",
    "spring.jpa.show-sql=false",
    "app.reporting.cache.enabled=false",
    "logging.level.org.hibernate.stat=OFF",
    "logging.level.org.hibernate.engine.internal.StatisticalLoggingSessionEventListener=OFF"
})
class B09DetailQueryTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired EntityManagerFactory entityManagerFactory;
    @Autowired ImportDetailService imports;
    @Autowired ExportDetailService exports;

    private static final String PHASE = System.getProperty("b09.nplusone.phase", "after");
    private static Path output;

    @BeforeAll
    static void startEvidence() throws Exception {
        if (!PHASE.equals("before") && !PHASE.equals("after")) {
            throw new IllegalArgumentException("Phase can la before hoac after");
        }
        String stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss_SSS"));
        output = Path.of("scripts/B09-test-results/NPLUSONE_" + PHASE.toUpperCase() + "_" + stamp);
        Files.createDirectories(output);
        Files.writeString(output.resolve("query-counts.csv"), "Kind,N,SqlCount,Phase\n", StandardCharsets.UTF_8);
        System.out.println("B09_NPLUSONE Ket qua: " + output.toAbsolutePath());
    }

    // No test transaction: each service call has its own persistence context, like a fresh request.
    private void seed(int count) {
        for (String table : new String[]{"import_detail", "export_detail", "import_receipt", "export_receipt", "product", "warehouse"}) {
            jdbc.update("DELETE FROM " + table);
        }
        jdbc.update("INSERT INTO warehouse(id,code,name,address) VALUES (1,'NPLUSONE-WH','Test','Test')");
        jdbc.update("INSERT INTO product(id,sku,name,unit,price) VALUES (1,'NPLUSONE-P','Test','Cai',1000)");
        for (int i = 1; i <= count; i++) {
            jdbc.update("INSERT INTO import_receipt(id,receipt_code,receipt_date,status,warehouse_id,created_by) VALUES (?,?,DATE '2026-10-01','DRAFT',1,'owner')", i, "NPLUSONE-I" + i);
            jdbc.update("INSERT INTO export_receipt(id,receipt_code,receipt_date,status,warehouse_id,created_by) VALUES (?,?,DATE '2026-10-01','DRAFT',1,'owner')", i, "NPLUSONE-E" + i);
            jdbc.update("INSERT INTO import_detail(id,receipt_id,product_id,quantity) VALUES (?,?,1,1)", i, i);
            jdbc.update("INSERT INTO export_detail(id,receipt_id,product_id,quantity) VALUES (?,?,1,1)", i, i);
        }
    }

    private void record(String kind, int count, long sqlCount) throws Exception {
        Files.writeString(output.resolve("query-counts.csv"), kind + "," + count + "," + sqlCount + "," + PHASE + "\n",
                StandardCharsets.UTF_8, StandardOpenOption.APPEND);
        System.out.printf("B09_NPLUSONE phase=%s kind=%s rows=%d SQL=%d%n", PHASE, kind, count, sqlCount);
        if (PHASE.equals("after")) {
            assertTrue(sqlCount <= 2, "So SQL phai <=2, khong tang theo so dong: " + sqlCount);
        }
    }

    @Test
    @WithMockUser(username = "query_manager", roles = "WAREHOUSE_MANAGER")
    void importListQueryCount() throws Exception {
        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        for (int n : new int[]{10, 50}) {
            seed(n);
            statistics.clear();
            var rows = imports.getAll();
            long queries = statistics.getPrepareStatementCount();
            assertEquals(n, rows.size());
            record("imports", n, queries);
        }
    }

    @Test
    @WithMockUser(username = "query_manager", roles = "WAREHOUSE_MANAGER")
    void exportListQueryCount() throws Exception {
        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        for (int n : new int[]{10, 50}) {
            seed(n);
            statistics.clear();
            var rows = exports.getAll();
            long queries = statistics.getPrepareStatementCount();
            assertEquals(n, rows.size());
            record("exports", n, queries);
        }
    }

    @Test
    @WithMockUser(username = "owner", roles = "WAREHOUSE_STAFF")
    void staffStillSeesOnlyOwnDetails() {
        seed(2);
        jdbc.update("UPDATE import_receipt SET created_by='other-owner' WHERE id=2");
        jdbc.update("UPDATE export_receipt SET created_by='other-owner' WHERE id=2");
        var importRows = imports.getAll();
        var exportRows = exports.getAll();
        assertEquals(1, importRows.size());
        assertEquals(1L, importRows.get(0).getReceiptId());
        assertEquals(1, exportRows.size());
        assertEquals(1L, exportRows.get(0).getReceiptId());
        System.out.println("B09_NPLUSONE OWNERSHIP PASS: STAFF chi thay chi tiet phieu cua minh.");
    }
}
