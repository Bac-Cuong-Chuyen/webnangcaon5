package com.example.supplychain;

import java.util.stream.Stream;
import com.example.supplychain.entity.StockTransfer;
import com.example.supplychain.service.ProductService;
import com.example.supplychain.service.WarehouseService;
import com.example.supplychain.service.TransferService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** HTTP security integration tests; business services are mocked to prevent data changes. */
@SpringBootTest(properties = {
    "spring.sql.init.mode=never",
    "spring.datasource.url=jdbc:h2:mem:b08_catalog_transfer_matrix;DB_CLOSE_DELAY=-1",
    "spring.jpa.hibernate.ddl-auto=create-drop"
})
@AutoConfigureMockMvc
class CatalogTransferRoleMatrixTest {
    @Autowired MockMvc mvc;
    @MockitoBean ProductService products;
    @MockitoBean WarehouseService warehouses;
    @MockitoBean TransferService transfers;

    static Stream<String> roles() {
        return Stream.of("ADMIN", "WAREHOUSE_MANAGER", "WAREHOUSE_STAFF", "DIRECTOR", "ANONYMOUS");
    }

    static Stream<Arguments> catalogCases() {
        return Stream.of("products", "warehouses").flatMap(resource ->
            Stream.of("GET", "POST", "PUT", "DELETE").flatMap(method -> roles().map(role -> {
                boolean allowed = "GET".equals(method) || "ADMIN".equals(role) || "WAREHOUSE_MANAGER".equals(role);
                int expected = "ANONYMOUS".equals(role) ? 401 : allowed ? 200 : 403;
                return Arguments.of(resource, method, role, expected);
            })));
    }

    @ParameterizedTest(name = "{2} {1} {0} expects {3}")
    @MethodSource("catalogCases")
    void catalogAccessMatchesB03(String resource, String method, String role, int expected) throws Exception {
        String path = "/api/" + resource + (("PUT".equals(method) || "DELETE".equals(method)) ? "/1" : "");
        var request = request(HttpMethod.valueOf(method), path).with(csrf())
            .contentType(MediaType.APPLICATION_JSON).content("{}");
        if (!"ANONYMOUS".equals(role)) request.with(user("matrix-user").roles(role));
        mvc.perform(request).andExpect(status().is(expected));
        Object service = "products".equals(resource) ? products : warehouses;
        assertEquals(expected == 200 ? 1 : 0, mockingDetails(service).getInvocations().size(),
            "Denied requests must never reach business services");
    }

    static Stream<Arguments> transferCases() {
        return Stream.of("create", "complete", "cancel").flatMap(action -> roles().map(role ->
            Arguments.of(action, role, "ANONYMOUS".equals(role) ? 401 :
                "WAREHOUSE_MANAGER".equals(role) ? ("create".equals(action) ? 201 : 200) : 403)));
    }

    @BeforeEach
    void stubTransferCreation() {
        when(transfers.createTransfer(any())).thenReturn(new StockTransfer());
        clearInvocations(transfers);
    }

    @ParameterizedTest(name = "{1} transfer {0} expects {2}")
    @MethodSource("transferCases")
    void transferOperationsRequireManager(String action, String role, int expected) throws Exception {
        String path = "/api/transfers" + ("create".equals(action) ? "" : "/1/" + action);
        var request = request(HttpMethod.POST, path).with(csrf()).contentType(MediaType.APPLICATION_JSON)
            .content("{\"transferCode\":\"B08-MATRIX\",\"fromWarehouseId\":1,\"toWarehouseId\":2,\"productId\":1,\"quantity\":1}");
        if (!"ANONYMOUS".equals(role)) request.with(user("matrix-manager").roles(role));
        mvc.perform(request).andExpect(status().is(expected));
        assertEquals(expected == 200 || expected == 201 ? 1 : 0,
            mockingDetails(transfers).getInvocations().size(),
            "Denied transfers must not reach business services");
    }
}
