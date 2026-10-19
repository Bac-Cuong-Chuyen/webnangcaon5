package com.example.supplychain;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:b09_api_pagination;DB_CLOSE_DELAY=-1",
    "spring.jpa.hibernate.ddl-auto=create-drop", "spring.sql.init.mode=never",
    "app.reporting.cache.enabled=false", "spring.jpa.show-sql=false",
    "spring.jpa.properties.hibernate.generate_statistics=true",
    "logging.level.org.hibernate.stat=OFF",
    "logging.level.org.hibernate.engine.internal.StatisticalLoggingSessionEventListener=OFF"
})
@AutoConfigureMockMvc
class B09PaginationTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;
    @Autowired EntityManagerFactory entityManagerFactory;
    static Path output;

    @BeforeAll static void evidence() throws Exception {
        String stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss_SSS"));
        output = Path.of("scripts/B09-test-results/PAGINATION_" + stamp);
        Files.createDirectories(output);
        Files.writeString(output.resolve("sql-counts.csv"), "Endpoint,Size,Returned,SqlCount\n", StandardCharsets.UTF_8);
        System.out.println("B09_PAGINATION Ket qua: " + output.toAbsolutePath());
    }

    @BeforeEach void seed() {
        for (String table : new String[]{"import_detail", "export_detail", "import_receipt", "export_receipt", "inventory", "product", "warehouse", "user_account"}) {
            jdbc.update("DELETE FROM " + table);
        }
        jdbc.update("INSERT INTO warehouse(id,code,name,address) VALUES (1,'PAGE-A','Test A','Test')");
        jdbc.update("INSERT INTO warehouse(id,code,name,address) VALUES (2,'PAGE-B','Test B','Test')");
        for (int i = 1; i <= 3; i++) {
            jdbc.update("INSERT INTO user_account(id,username,password,role) VALUES (?,?,?,'WAREHOUSE_STAFF')", i, "page-user-" + i, "test-only-placeholder");
        }
        for (int i = 1; i <= 120; i++) {
            String owner = i % 2 == 0 ? "page-owner" : "other-owner";
            jdbc.update("INSERT INTO product(id,sku,name,unit,price) VALUES (?,?,?,'Cai',1000)", i, "PAGE-P" + i, "Test " + i);
            jdbc.update("INSERT INTO inventory(id,warehouse_id,product_id,quantity,minimum_quantity) VALUES (?,1,?,100,1)", i, i);
            jdbc.update("INSERT INTO import_receipt(id,receipt_code,receipt_date,status,warehouse_id,created_by) VALUES (?,?,DATE '2026-10-01','DRAFT',1,?)", i, "PAGE-I" + i, owner);
            jdbc.update("INSERT INTO export_receipt(id,receipt_code,receipt_date,status,warehouse_id,created_by) VALUES (?,?,DATE '2026-10-01','DRAFT',1,?)", i, "PAGE-E" + i, owner);
            jdbc.update("INSERT INTO import_detail(id,receipt_id,product_id,quantity) VALUES (?,?,?,1)", i, i, i);
            jdbc.update("INSERT INTO export_detail(id,receipt_id,product_id,quantity) VALUES (?,?,?,1)", i, i, i);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"products", "inventories", "warehouses", "user-accounts", "import-receipts", "export-receipts", "import-details", "export-details"})
    @WithMockUser(username = "page-admin", roles = "ADMIN")
    void defaultsCapsAndBadInputs(String endpoint) throws Exception {
        int total = endpoint.equals("warehouses") ? 2 : endpoint.equals("user-accounts") ? 3 : 120;
        mvc.perform(get("/api/" + endpoint))
                .andExpect(status().isOk()).andExpect(header().string("X-Page", "0"))
                .andExpect(header().string("X-Page-Size", "20"))
                .andExpect(header().string("X-Has-Next", Boolean.toString(total > 20)))
                .andExpect(jsonPath("$.length()").value(Math.min(20, total)));
        mvc.perform(get("/api/" + endpoint).param("size", "10000"))
                .andExpect(status().isOk()).andExpect(header().string("X-Page-Size", "100"))
                .andExpect(jsonPath("$.length()").value(Math.min(100, total)));
        mvc.perform(get("/api/" + endpoint).param("page", "-1")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/" + endpoint).param("size", "0")).andExpect(status().isBadRequest());
        System.out.println("B09_PAGINATION PASS " + endpoint + " default20/max100/invalid400");
    }

    @Test @WithMockUser(username = "page-manager", roles = "WAREHOUSE_MANAGER")
    void adjacentPagesHaveStableIdsAndNoOverlap() throws Exception {
        JsonNode first = json.readTree(mvc.perform(get("/api/products").param("page", "0").param("size", "20"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        JsonNode second = json.readTree(mvc.perform(get("/api/products").param("page", "1").param("size", "20"))
                .andExpect(status().isOk()).andExpect(header().string("X-Page", "1"))
                .andReturn().getResponse().getContentAsString());
        assertEquals(20, first.size()); assertEquals(20, second.size());
        assertEquals(1L, first.get(0).get("id").asLong());
        assertEquals(20L, first.get(19).get("id").asLong());
        assertEquals(21L, second.get(0).get("id").asLong());
        assertEquals(40L, second.get(19).get("id").asLong());
        System.out.println("B09_PAGINATION STABLE PASS pages0/1 sorted by id, no overlap");
    }

    @Test @WithMockUser(username = "page-owner", roles = "WAREHOUSE_STAFF")
    void ownershipIsFilteredBeforePagination() throws Exception {
        for (String endpoint : new String[]{"import-receipts", "export-receipts", "import-details", "export-details"}) {
            JsonNode rows = json.readTree(mvc.perform(get("/api/" + endpoint).param("size", "10"))
                    .andExpect(status().isOk()).andExpect(header().string("X-Has-Next", "true"))
                    .andReturn().getResponse().getContentAsString());
            assertEquals(10, rows.size(), "Loc quyen phai truoc LIMIT, khong phai cat trang roi moi loc");
            for (JsonNode row : rows) {
                long receiptId = row.get(endpoint.endsWith("details") ? "receiptId" : "id").asLong();
                assertEquals(0L, receiptId % 2, "Khong duoc lo phieu cua other-owner");
            }
        }
        System.out.println("B09_PAGINATION OWNERSHIP PASS SQL filters ownership before LIMIT");
    }

    @Test @WithMockUser(username = "page-admin", roles = "ADMIN")
    void queryCountsStayBoundedAt20And100Rows() throws Exception {
        var stats = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        for (String endpoint : new String[]{"products", "inventories", "import-receipts", "export-receipts", "import-details", "export-details"}) {
            for (int size : new int[]{20, 100}) {
                stats.clear();
                String body = mvc.perform(get("/api/" + endpoint).param("size", Integer.toString(size)))
                        .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
                long queries = stats.getPrepareStatementCount();
                int returned = json.readTree(body).size();
                assertEquals(size, returned);
                Files.writeString(output.resolve("sql-counts.csv"), endpoint + "," + size + "," + returned + "," + queries + "\n",
                        StandardCharsets.UTF_8, StandardOpenOption.APPEND);
                assertTrue(queries <= 2, "Khong tai phieu cha tung dong: " + endpoint + " SQL=" + queries);
                System.out.printf("B09_PAGINATION SQL PASS endpoint=%s rows=%d SQL=%d%n", endpoint, returned, queries);
            }
        }
    }
}
