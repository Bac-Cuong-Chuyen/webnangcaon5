package com.example.supplychain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import com.example.supplychain.exception.GlobalExceptionHandler;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

class ErrorResponseSecurityTest {

    @Test
    void internalErrorHidesDetailsAndReturnsMatchingRequestId()
            throws Exception {

        MockMvc mvc = MockMvcBuilders
                .standaloneSetup(new FailureProbe())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        MvcResult result = mvc.perform(get("/__b08_test/error"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.message").value("Lỗi hệ thống"))
                .andExpect(jsonPath("$.path").value("/__b08_test/error"))
                .andReturn();

        MockHttpServletResponse response = result.getResponse();
        String body = response.getContentAsString(StandardCharsets.UTF_8);
        String requestId = response.getHeader("X-Request-ID");

        assertNotNull(requestId);
        UUID.fromString(requestId);

        String bodyRequestId = new ObjectMapper()
                .readTree(body)
                .path("requestId")
                .asText();

        assertEquals(requestId, bodyRequestId);
        assertFalse(body.contains("B08_INTERNAL_DETAIL_MARKER"));
        assertFalse(body.contains("IllegalStateException"));
        assertFalse(body.contains("org.springframework"));
        assertFalse(body.contains("stackTrace"));

        System.out.println("=== BM9 CONTROLLED 500 ===");
        System.out.println("HTTP: " + response.getStatus());
        System.out.println("X-Request-ID: " + requestId);
        System.out.println("BODY: " + body);
        System.out.println("Internal details hidden: PASS");
        System.out.println("Header/body requestId match: PASS");
    }

    @RestController
    static class FailureProbe {

        @GetMapping("/__b08_test/error")
        public void fail() {
            throw new IllegalStateException("B08_INTERNAL_DETAIL_MARKER");
        }
    }
}