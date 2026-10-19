package com.example.supplychain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doAnswer;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.example.supplychain.dto.TransferRequest;
import com.example.supplychain.entity.Inventory;
import com.example.supplychain.entity.Product;
import com.example.supplychain.entity.StockTransfer;
import com.example.supplychain.entity.Warehouse;
import com.example.supplychain.repository.InventoryRepository;
import com.example.supplychain.repository.ProductRepository;
import com.example.supplychain.repository.WarehouseRepository;
import com.example.supplychain.service.TransferService;

@SpringBootTest(properties = {
        "spring.sql.init.mode=never",
        "spring.datasource.url=jdbc:h2:mem:transfer_rollback_test;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@org.springframework.security.test.context.support.WithMockUser(username = "creator", roles = "WAREHOUSE_MANAGER")
class TransferRollbackTest {

    @Autowired
    private TransferService transferService;

    @Autowired
    private WarehouseRepository warehouseRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private JdbcTemplate jdbc;

    @MockitoSpyBean
    private InventoryRepository inventoryRepository;

    @Test
    void rollbackAfterInventoryWritesBeforeReceiptCompletion() {
        assertFalse(
                TransactionSynchronizationManager.isActualTransactionActive());

        String code = "TV4_ROLLBACK_"
                + UUID.randomUUID().toString().substring(0, 8);

        TransactionTemplate setup =
                new TransactionTemplate(transactionManager);

        Long[] ids = setup.execute(transactionStatus -> {
            Warehouse source = warehouseRepository.save(
                    warehouse(code + "_SRC"));

            Warehouse target = warehouseRepository.save(
                    warehouse(code + "_DST"));

            Product product = new Product();
            product.setSku(code);
            product.setName("Rollback test product");
            product.setUnit("pcs");
            product.setPrice(BigDecimal.TEN);
            product = productRepository.save(product);

            inventoryRepository.save(
                    inventory(source.getId(), product.getId(), 10));

            inventoryRepository.save(
                    inventory(target.getId(), product.getId(), 0));

            return new Long[] {
                    source.getId(),
                    target.getId(),
                    product.getId()
            };
        });

        Long sourceId = ids[0];
        Long targetId = ids[1];
        Long productId = ids[2];

        TransferRequest request = new TransferRequest();
        request.setTransferCode(code);
        request.setFromWarehouseId(sourceId);
        request.setToWarehouseId(targetId);
        request.setProductId(productId);
        request.setQuantity(3);

        StockTransfer transfer =
                transferService.createTransfer(request);

        org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(
            new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                "other-manager", null, java.util.List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_WAREHOUSE_MANAGER"))));
        assertEquals(10, quantity(sourceId, productId));
        assertEquals(0, quantity(targetId, productId));

        AtomicBoolean writesVerified = new AtomicBoolean(false);

        doAnswer(invocation -> {
            // Force SQL writes before injecting the controlled failure.
            inventoryRepository.flush();

            assertEquals(7, quantity(sourceId, productId));
            assertEquals(3, quantity(targetId, productId));

            writesVerified.set(true);

            System.out.println(
                    "B06_ROLLBACK INSIDE TRANSACTION: source=7, target=3");

            throw new IllegalStateException(
                    "B06_CONTROLLED_FAILURE_AFTER_WRITES");

        }).when(inventoryRepository).save(
                argThat((Inventory row) ->
                        row != null
                                && targetId.equals(row.getWarehouseId())
                                && productId.equals(row.getProductId())));

        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> transferService.completeTransfer(transfer.getId()));

        assertEquals(
                "B06_CONTROLLED_FAILURE_AFTER_WRITES",
                error.getMessage());

        assertTrue(
                writesVerified.get(),
                "Controlled failure must occur after SQL writes");

        assertFalse(
                TransactionSynchronizationManager.isActualTransactionActive());

        // Read after the service transaction has rolled back.
        int sourceAfter = quantity(sourceId, productId);
        int targetAfter = quantity(targetId, productId);

        String statusAfter = jdbc.queryForObject(
                "SELECT status FROM stock_transfer WHERE id = ?",
                String.class,
                transfer.getId());

        Integer detailAfter = jdbc.queryForObject(
                """
                SELECT quantity
                FROM transfer_detail
                WHERE transfer_id = ? AND product_id = ?
                """,
                Integer.class,
                transfer.getId(),
                productId);

        assertEquals(10, sourceAfter);
        assertEquals(0, targetAfter);
        assertEquals("DRAFT", statusAfter);
        assertEquals(Integer.valueOf(3), detailAfter);

        System.out.println("===== B06 ROLLBACK VERIFIED =====");
        System.out.println(
                "Transfer: " + code + "; id=" + transfer.getId());
        System.out.println(
                "Before: source=10, target=0, status=DRAFT");
        System.out.println(
                "Flushed inside failed transaction: source=7, target=3");
        System.out.println(
                "After rollback: source=" + sourceAfter
                        + ", target=" + targetAfter
                        + ", status=" + statusAfter);
        System.out.println(
                "Detail quantity after rollback: " + detailAfter);
        System.out.println(
                "PASS: database changes rolled back after actual SQL writes");
    }

    private int quantity(Long warehouseId, Long productId) {
        return jdbc.queryForObject(
                """
                SELECT quantity
                FROM inventory
                WHERE warehouse_id = ? AND product_id = ?
                """,
                Integer.class,
                warehouseId,
                productId);
    }

    private Warehouse warehouse(String code) {
        Warehouse warehouse = new Warehouse();
        warehouse.setCode(code);
        warehouse.setName("Rollback test warehouse");
        warehouse.setAddress("Local test");
        return warehouse;
    }

    private Inventory inventory(
            Long warehouseId, Long productId, int quantity) {

        Inventory row = new Inventory();
        row.setWarehouseId(warehouseId);
        row.setProductId(productId);
        row.setQuantity(quantity);
        row.setMinimumQuantity(0);
        return row;
    }
}
