package com.example.supplychain;

import com.example.supplychain.controller.AuthController;
import com.example.supplychain.repository.UserAccountRepository;
import com.example.supplychain.service.AuditLogService;
import com.example.supplychain.service.LoginAttemptService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class LoginIpRateLimitTest {
    private MockMvc mvc;
    private AuthenticationManager authenticationManager;
    private AuditLogService audit;
    private LoginAttemptService attempts;

    @BeforeEach
    void setUp() {
        authenticationManager = mock(AuthenticationManager.class);
        when(authenticationManager.authenticate(any())).thenThrow(new BadCredentialsException("Invalid credentials"));
        audit = mock(AuditLogService.class);
        attempts = new LoginAttemptService();
        AuthController controller = new AuthController(mock(UserAccountRepository.class),
                authenticationManager, audit, attempts, mock(PasswordEncoder.class));
        mvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    private MockHttpServletRequestBuilder login(String username, String ip) {
        return post("/api/auth/login").with(request -> { request.setRemoteAddr(ip); return request; })
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"" + username + "\",\"password\":\"WrongPassword_B08!\"}");
    }

    @Test
    void changingUsernamesAndForwardedHeadersCannotBypassIpLimit() throws Exception {
        for (int i = 0; i < 20; i++) {
            mvc.perform(login("unknown-" + i, "192.0.2.10")
                    .header("X-Forwarded-For", "198.51.100." + (i + 1)))
                    .andExpect(status().isUnauthorized());
        }
        mvc.perform(login("unknown-next", "192.0.2.10")
                .header("X-Forwarded-For", "203.0.113.20"))
                .andExpect(status().isTooManyRequests());
        verify(authenticationManager, times(20)).authenticate(any());
        verify(audit).record(eq("unknown-next"), eq("LOGIN_BLOCKED_IP"), anyString());
        System.out.println("BM10 IP: 20 different usernames -> 401; request 21 -> 429; changing X-Forwarded-For did not bypass limit.");
    }

    @Test
    void blockedIpDoesNotBlockAnUnrelatedIp() throws Exception {
        for (int i = 0; i < 20; i++) attempts.recordIpFailure("192.0.2.10");
        mvc.perform(login("new-user", "192.0.2.10")).andExpect(status().isTooManyRequests());
        mvc.perform(login("new-user", "192.0.2.11")).andExpect(status().isUnauthorized());
    }

    @Test
    void accountLimitStillAppliesAcrossDifferentIps() throws Exception {
        for (int i = 0; i < 5; i++) {
            mvc.perform(login("same-user", "198.51.100." + (i + 1)))
                    .andExpect(status().isUnauthorized());
        }
        mvc.perform(login("same-user", "198.51.100.99")).andExpect(status().isTooManyRequests());
        verify(audit).record(eq("same-user"), eq("LOGIN_BLOCKED"), anyString());
    }

    @Test
    void successfulAccountLoginDoesNotResetSharedIpCounter() {
        attempts.recordFailure("account");
        for (int i = 0; i < 20; i++) attempts.recordIpFailure("192.0.2.10");
        attempts.recordSuccess("account");
        assertFalse(attempts.isBlocked("account"));
        assertTrue(attempts.isIpBlocked("192.0.2.10"));
    }
}
