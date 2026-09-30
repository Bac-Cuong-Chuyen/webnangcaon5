package com.example.supplychain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import com.example.supplychain.entity.Product;
import com.example.supplychain.entity.Warehouse;
import com.example.supplychain.repository.InventoryRepository;
import com.example.supplychain.repository.ProductRepository;
import com.example.supplychain.repository.WarehouseRepository;

/**
 * B05-06: test case cho POST /api/imports và POST /api/exports.
 * Mỗi test chạy trong một transaction rồi rollback, nên không cần dữ liệu mẫu sẵn.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@WithMockUser(roles = "WAREHOUSE_STAFF")
class StockApiValidationTest {

    private static final long MISSING_ID = 999_999L;

    @Autowired MockMvc mvc;
    @Autowired ProductRepository productRepository;
    @Autowired WarehouseRepository warehouseRepository;
    @Autowired InventoryRepository inventoryRepository;

    Long productId;
    Long warehouseId;

    @BeforeEach
    void setUp() {
        Product p = new Product();
        p.setSku("SKU-TEST");
        p.setName("Test product");
        p.setUnit("pcs");
        p.setPrice(BigDecimal.TEN);
        productId = productRepository.save(p).getId();

        Warehouse w = new Warehouse();
        w.setCode("WH-TEST");
        w.setName("Test warehouse");
        w.setAddress("Ha Noi");
        warehouseId = warehouseRepository.save(w).getId();
    }

    private ResultActions call(String url, Object product, Object warehouse, Object quantity) throws Exception {
        String json = "{\"receiptCode\":\"TC-001\",\"productId\":%s,\"warehouseId\":%s,\"quantity\":%s}"
                .formatted(product, warehouse, quantity);
        return mvc.perform(post(url).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private int stock() {
        return inventoryRepository.findByWarehouseIdAndProductId(warehouseId, productId)
                .map(i -> i.getQuantity()).orElse(0);
    }

    @Test // TC01
    void tc01_importValid() throws Exception {
        call("/api/imports", productId, warehouseId, 10).andExpect(status().isOk());
        assertEquals(10, stock());
    }

    @Test // TC02
    void tc02_exportValid() throws Exception {
        call("/api/imports", productId, warehouseId, 10).andExpect(status().isOk());
        call("/api/exports", productId, warehouseId, 5).andExpect(status().isOk());
        assertEquals(5, stock());
    }

    @Test // TC03
    void tc03_productNotFound() throws Exception {
        call("/api/imports", MISSING_ID, warehouseId, 5)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test // TC04
    void tc04_warehouseNotFound() throws Exception {
        call("/api/imports", productId, MISSING_ID, 5)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test // TC05
    void tc05_quantityZero() throws Exception {
        call("/api/imports", productId, warehouseId, 0)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test // TC06
    void tc06_quantityNegative() throws Exception {
        call("/api/imports", productId, warehouseId, -5)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test // TC07
    void tc07_exportExceedsStock() throws Exception {
        call("/api/imports", productId, warehouseId, 10).andExpect(status().isOk());
        call("/api/exports", productId, warehouseId, 1_000_000)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("BUSINESS_CONFLICT"));
        assertEquals(10, stock()); // tồn kho không đổi
    }

    @Test
    @WithAnonymousUser // TC08
    void tc08_unauthenticated() throws Exception {
        call("/api/imports", productId, warehouseId, 5).andExpect(status().isUnauthorized());
    }

    @Test // TC09
    void tc09_productIdMissing() throws Exception {
        call("/api/exports", null, warehouseId, 5)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test // TC10
    void tc10_quantityWrongType() throws Exception {
        call("/api/imports", productId, warehouseId, "\"abc\"")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST_BODY"));
    }
}
