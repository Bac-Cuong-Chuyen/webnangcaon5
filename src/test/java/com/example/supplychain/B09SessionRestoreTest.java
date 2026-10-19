package com.example.supplychain;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={
    "spring.sql.init.mode=never", "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.datasource.url=jdbc:h2:mem:b09_session_restore;DB_CLOSE_DELAY=-1",
    "app.reporting.cache.enabled=false"
})
@AutoConfigureMockMvc
class B09SessionRestoreTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach void seed(){
        jdbc.update("DELETE FROM user_account");
        jdbc.update("INSERT INTO user_account(id,username,password,role) VALUES (1,'restore-manager','test-only-placeholder','WAREHOUSE_MANAGER')");
    }

    @Test void anonymousCannotReadIdentity() throws Exception {
        mvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized())
                .andExpect(header().string("Cache-Control",org.hamcrest.Matchers.containsString("no-store")));
    }

    @Test void refreshReadsExistingSessionWithoutPassword() throws Exception {
        var session=new MockHttpSession();
        var context=SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new UsernamePasswordAuthenticationToken("restore-manager",null,
                AuthorityUtils.createAuthorityList("ROLE_WAREHOUSE_MANAGER")));
        session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY,context);
        for(int refresh=0;refresh<2;refresh++){
            mvc.perform(get("/api/auth/me").session(session)).andExpect(status().isOk())
                    .andExpect(header().string("Cache-Control","no-store"))
                    .andExpect(jsonPath("$.username").value("restore-manager"))
                    .andExpect(jsonPath("$.role").value("WAREHOUSE_MANAGER"))
                    .andExpect(jsonPath("$.password").doesNotExist());
        }
        mvc.perform(post("/api/auth/logout").session(session).with(csrf())).andExpect(status().isOk());
        mvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized());
    }

    @Test void missingAccountDoesNotRestoreStaleIdentity() throws Exception {
        mvc.perform(get("/api/auth/me").with(user("deleted-account").roles("ADMIN")))
                .andExpect(status().isUnauthorized()).andExpect(header().string("Cache-Control",org.hamcrest.Matchers.containsString("no-store")));
    }
}
