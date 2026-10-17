package com.example.supplychain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.example.supplychain.entity.Role;
import com.example.supplychain.entity.UserAccount;
import com.example.supplychain.repository.UserAccountRepository;

/** B08 V4: sending an account response must never overwrite its stored password hash. */
@SpringBootTest(properties = {
        "spring.sql.init.mode=never",
        "spring.datasource.url=jdbc:h2:mem:password_integrity_test;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@AutoConfigureMockMvc
@Transactional
@WithMockUser(username = "admin", roles = "ADMIN")
class UserAccountPasswordIntegrityTest {

    @Autowired MockMvc mvc;
    @Autowired UserAccountRepository repository;
    @Autowired PasswordEncoder encoder;

    private UserAccount createAccount() {
        UserAccount account = new UserAccount();
        account.setUsername("v4-safe-" + UUID.randomUUID());
        account.setPassword(encoder.encode("ExamplePass123!"));
        account.setRole(Role.WAREHOUSE_STAFF);
        return repository.saveAndFlush(account);
    }

    @Test
    void readingAnAccountDoesNotClearPasswordHash() throws Exception {
        UserAccount account = createAccount();
        String hash = account.getPassword();
        mvc.perform(get("/api/user-accounts/{id}", account.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.password").value(org.hamcrest.Matchers.nullValue()));
        assertEquals(hash, repository.findById(account.getId()).orElseThrow().getPassword());
        assertTrue(encoder.matches("ExamplePass123!", account.getPassword()));
    }

    @Test
    void roleUpdateWithoutNewPasswordRetainsExistingHash() throws Exception {
        UserAccount account = createAccount();
        String hash = account.getPassword();
        String json = "{\"username\":\"" + account.getUsername() + "\",\"role\":\"WAREHOUSE_MANAGER\"}";
        mvc.perform(put("/api/user-accounts/{id}", account.getId())
                        .with(csrf().asHeader())
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.password").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.role").value("WAREHOUSE_MANAGER"));
        UserAccount persisted = repository.findById(account.getId()).orElseThrow();
        assertEquals(hash, persisted.getPassword());
        assertTrue(encoder.matches("ExamplePass123!", persisted.getPassword()));
    }
}
