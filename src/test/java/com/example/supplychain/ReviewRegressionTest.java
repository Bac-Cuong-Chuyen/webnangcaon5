package com.example.supplychain;

import com.example.supplychain.entity.*;
import com.example.supplychain.repository.*;
import com.example.supplychain.service.ExportReportService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.UUID;
import java.time.LocalDate;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:review_regression;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
class ReviewRegressionTest {
    @Autowired MockMvc mvc;
    @Autowired UserAccountRepository accounts;
    @Autowired ProductRepository products;
    @Autowired PasswordEncoder encoder;
    @Autowired JdbcTemplate jdbc;
    @Autowired ExportReportService reports;

    private UserAccount account(Role role) {
        UserAccount value = new UserAccount();
        value.setUsername("review-" + UUID.randomUUID());
        value.setPassword(encoder.encode("ExamplePass123!"));
        value.setRole(role);
        return accounts.saveAndFlush(value);
    }
    private MockHttpSession login(UserAccount account) throws Exception {
        return (MockHttpSession) mvc.perform(post("/api/auth/login").with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"" + account.getUsername() + "\",\"password\":\"ExamplePass123!\"}"))
                .andExpect(status().isOk()).andReturn().getRequest().getSession(false);
    }
    private void updateAccount(UserAccount account, String username, Role role, String password) throws Exception {
        mvc.perform(put("/api/user-accounts/{id}", account.getId()).with(user("admin").roles("ADMIN"))
                .with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"" + username + "\",\"role\":\"" + role
                        + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk());
    }
    @ParameterizedTest
    @ValueSource(strings = {"role", "password", "username", "delete"})
    void existingSessionsCannotKeepOldAccess(String change) throws Exception {
        UserAccount account = account(Role.ADMIN);
        MockHttpSession first = login(account), second = login(account);
        mvc.perform(get("/api/user-accounts").session(first)).andExpect(status().isOk());
        if (change.equals("delete")) {
            mvc.perform(delete("/api/user-accounts/{id}", account.getId())
                    .with(user("other-admin").roles("ADMIN")).with(csrf())).andExpect(status().isOk());
        } else {
            updateAccount(account, change.equals("username") ? "renamed-" + UUID.randomUUID() : account.getUsername(),
                    change.equals("role") ? Role.WAREHOUSE_STAFF : Role.ADMIN,
                    change.equals("password") ? "ChangedPass123!" : "");
        }
        mvc.perform(get("/api/user-accounts").session(first)).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/user-accounts").session(second)).andExpect(status().isUnauthorized());
        assertTrue(first.isInvalid());
        assertTrue(second.isInvalid());
    }
    @Test
    void unchangedAccountKeepsSession() throws Exception {
        UserAccount account = account(Role.ADMIN);
        MockHttpSession session = login(account);
        updateAccount(account, account.getUsername(), Role.ADMIN, "");
        mvc.perform(get("/api/user-accounts").session(session)).andExpect(status().isOk());
    }
    @Test
    void restoringRoleDoesNotReviveOldSession() throws Exception {
        UserAccount account = account(Role.ADMIN);
        MockHttpSession session = login(account);
        updateAccount(account, account.getUsername(), Role.WAREHOUSE_STAFF, "");
        updateAccount(account, account.getUsername(), Role.ADMIN, "");
        mvc.perform(get("/api/user-accounts").session(session)).andExpect(status().isUnauthorized());
    }
    @Test
    void duplicateRenameReturnsConflictAndPreservesBothAccounts() throws Exception {
        UserAccount first = account(Role.WAREHOUSE_STAFF), second = account(Role.ADMIN);
        String original = first.getUsername();
        mvc.perform(put("/api/user-accounts/{id}", first.getId()).with(user("admin").roles("ADMIN"))
                .with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"  " + second.getUsername() + "  \",\"role\":\"ADMIN\"}"))
                .andExpect(status().isConflict());
        assertEquals(original, accounts.findById(first.getId()).orElseThrow().getUsername());
        assertNotNull(login(second));
    }
    @Test
    void databaseRejectsDuplicateUsername() {
        UserAccount first = account(Role.ADMIN);
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class, () ->
            jdbc.update("insert into user_account (username, password, role) values (?, ?, ?)",
                    first.getUsername(), first.getPassword(), "ADMIN"));
    }
    private Product product() {
        Product product = new Product();
        product.setSku("review-" + UUID.randomUUID());
        product.setName("Review product");
        return products.saveAndFlush(product);
    }
    @ParameterizedTest
    @ValueSource(strings = {"inventory", "import_detail", "export_detail", "transfer_detail"})
    void referencedProductsCannotBeDeleted(String table) throws Exception {
        Product product = product();
        jdbc.update("insert into " + table + " (product_id, quantity) values (?, 5)", product.getId());
        mvc.perform(delete("/api/products/{id}", product.getId()).with(user("admin").roles("ADMIN"))
                .with(csrf())).andExpect(status().isConflict());
        assertTrue(products.existsById(product.getId()));
    }
    @Test
    void unusedProductCanBeDeleted() throws Exception {
        Product product = product();
        mvc.perform(delete("/api/products/{id}", product.getId()).with(user("admin").roles("ADMIN"))
                .with(csrf())).andExpect(status().isOk());
        assertFalse(products.existsById(product.getId()));
    }
    @Test
    void failedDeletionPreservesHistoricalReport() throws Exception {
        Product product = product();
        jdbc.update("insert into export_receipt (receipt_code, receipt_date, status) values (?, ?, 'APPROVED')",
                product.getSku(), LocalDate.of(2026, 1, 1));
        Long receipt = jdbc.queryForObject("select id from export_receipt where receipt_code = ?", Long.class, product.getSku());
        jdbc.update("insert into export_detail (receipt_id, product_id, quantity) values (?, ?, 7)", receipt, product.getId());
        var before = reports.report(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 1));
        mvc.perform(delete("/api/products/{id}", product.getId()).with(user("admin").roles("ADMIN"))
                .with(csrf())).andExpect(status().isConflict());
        assertEquals(before, reports.report(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 1)));
        assertTrue(before.data().stream().anyMatch(row -> row.sku().equals(product.getSku()) && row.totalQuantity() == 7));
    }
}
