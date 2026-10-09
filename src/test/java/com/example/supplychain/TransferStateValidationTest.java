package com.example.supplychain;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.example.supplychain.entity.StockTransfer;
import com.example.supplychain.entity.Warehouse;
import com.example.supplychain.repository.StockTransferRepository;
import com.example.supplychain.repository.WarehouseRepository;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@WithMockUser(roles = "WAREHOUSE_STAFF")
class TransferStateValidationTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private StockTransferRepository transferRepository;

    @Autowired
    private WarehouseRepository warehouseRepository;

    @ParameterizedTest
    @CsvSource({
        "COMPLETED, complete",
        "COMPLETED, cancel",
        "CANCELLED, complete",
        "CANCELLED, cancel"
    })
    void rejectInvalidTransition(String initialStatus, String action)
            throws Exception {

        Warehouse source = createWarehouse("B06-STATE-SRC");
        Warehouse target = createWarehouse("B06-STATE-DST");

        StockTransfer transfer = new StockTransfer();
        transfer.setTransferCode("B06-STATE-TEST");
        transfer.setFromWarehouseId(source.getId());
        transfer.setToWarehouseId(target.getId());
        transfer.setTransferDate(LocalDate.now());
        transfer.setStatus(initialStatus);
        transfer = transferRepository.saveAndFlush(transfer);

        mvc.perform(post("/api/transfers/{id}/{action}",
                        transfer.getId(), action))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code")
                        .value("TRANSFER_BUSINESS_ERROR"));
    }

    private Warehouse createWarehouse(String code) {
        Warehouse warehouse = new Warehouse();
        warehouse.setCode(code);
        warehouse.setName(code);
        warehouse.setAddress("Ha Noi");
        return warehouseRepository.saveAndFlush(warehouse);
    }
}