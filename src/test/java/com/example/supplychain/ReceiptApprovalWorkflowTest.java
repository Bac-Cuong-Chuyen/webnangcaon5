package com.example.supplychain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import java.util.concurrent.*;
import com.example.supplychain.dto.TransferRequest;
import com.example.supplychain.entity.*;
import com.example.supplychain.repository.*;
import com.example.supplychain.service.ImportReportService;
import com.example.supplychain.service.ExportReportService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
    "spring.sql.init.mode=never",
    "spring.datasource.url=jdbc:h2:mem:b08_approval_workflow;DB_CLOSE_DELAY=-1",
    "spring.jpa.hibernate.ddl-auto=create-drop"
})
@AutoConfigureMockMvc
@Transactional
class ReceiptApprovalWorkflowTest {
    @Autowired MockMvc mvc;
    @Autowired ProductRepository products;
    @Autowired WarehouseRepository warehouses;
    @Autowired InventoryRepository inventory;
    @Autowired ImportReceiptRepository imports;
    @Autowired ExportReceiptRepository exports;
    @Autowired ImportDetailRepository importDetails;
    @Autowired ExportDetailRepository exportDetails;
    @Autowired StockTransferRepository transfers;
    @Autowired ImportReportService importReports;
    @Autowired ExportReportService exportReports;
    @Autowired jakarta.persistence.EntityManager entityManager;
    private Long productId, warehouseId;

    @BeforeEach
    void seed() {
        String code = "approval-" + UUID.randomUUID();
        Product product = new Product(); product.setSku(code); product.setName(code);
        product.setUnit("pcs"); product.setPrice(BigDecimal.TEN);
        productId = products.saveAndFlush(product).getId();
        Warehouse warehouse = new Warehouse(); warehouse.setCode(code); warehouse.setName(code);
        warehouseId = warehouses.saveAndFlush(warehouse).getId();
        stock(productId,10);
    }
    private void stock(Long product, int quantity) {
        Inventory row = inventory.findByWarehouseIdAndProductId(warehouseId,product).orElseGet(Inventory::new);
        row.setWarehouseId(warehouseId); row.setProductId(product); row.setQuantity(quantity); row.setMinimumQuantity(0);
        inventory.saveAndFlush(row);
    }
    private int quantity(Long product) {
        return inventory.findByWarehouseIdAndProductId(warehouseId,product).orElseThrow().getQuantity();
    }
    private String url(String kind) { return "/api/" + kind + "s"; }
    private Long draft(String kind, String creator, String role, int quantity) throws Exception {
        String code = "draft-" + UUID.randomUUID();
        String body = "{\"receiptCode\":\""+code+"\",\"productId\":"+productId+
            ",\"warehouseId\":"+warehouseId+",\"quantity\":"+quantity+"}";
        mvc.perform(post(url(kind)).with(user(creator).roles(role)).with(csrf())
            .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isOk());
        return "import".equals(kind) ? imports.findAll().stream().filter(row -> code.equals(row.getReceiptCode())).findFirst().orElseThrow().getId()
            : exports.findAll().stream().filter(row -> code.equals(row.getReceiptCode())).findFirst().orElseThrow().getId();
    }
    private String state(String kind,Long id) {
        return "import".equals(kind)?imports.findById(id).orElseThrow().getStatus():exports.findById(id).orElseThrow().getStatus();
    }
    private String approver(String kind,Long id) {
        return "import".equals(kind)?imports.findById(id).orElseThrow().getApprovedBy():exports.findById(id).orElseThrow().getApprovedBy();
    }

    @ParameterizedTest @ValueSource(strings={"import","export"})
    void draftDoesNotChangeStockAndDifferentManagerApprovesOnce(String kind) throws Exception {
        Long id = draft(kind,"staff-a","WAREHOUSE_STAFF",3);
        assertEquals(10,quantity(productId)); assertEquals("DRAFT",state(kind,id));
        mvc.perform(post(url(kind)+"/"+id+"/approve").with(user("manager-b").roles("WAREHOUSE_MANAGER")).with(csrf()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("APPROVED"))
            .andExpect(jsonPath("$.createdBy").value("staff-a")).andExpect(jsonPath("$.approvedBy").value("manager-b"));
        int expected = "import".equals(kind)?13:7;
        assertEquals(expected,quantity(productId));
        mvc.perform(post(url(kind)+"/"+id+"/approve").with(user("manager-c").roles("WAREHOUSE_MANAGER")).with(csrf()))
            .andExpect(status().isConflict());
        assertEquals(expected,quantity(productId));
    }

    @ParameterizedTest @ValueSource(strings={"import","export"})
    void managerCannotApproveOwnReceipt(String kind) throws Exception {
        Long id = draft(kind,"manager-a","WAREHOUSE_MANAGER",3);
        mvc.perform(post(url(kind)+"/"+id+"/approve").with(user("manager-a").roles("WAREHOUSE_MANAGER")).with(csrf()))
            .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("BUSINESS_CONFLICT"));
        assertEquals("DRAFT",state(kind,id)); assertNull(approver(kind,id)); assertEquals(10,quantity(productId));
    }

    @ParameterizedTest @ValueSource(strings={"import","export"})
    void adminStaffAndDirectorCannotApprove(String kind) throws Exception {
        Long id = draft(kind,"staff-a","WAREHOUSE_STAFF",3);
        for (String role : new String[]{"ADMIN","WAREHOUSE_STAFF","DIRECTOR"}) {
            mvc.perform(post(url(kind)+"/"+id+"/approve").with(user("other-user").roles(role)).with(csrf()))
                .andExpect(status().isForbidden());
        }
        mvc.perform(post(url(kind)+"/"+id+"/approve").with(csrf())).andExpect(status().isUnauthorized());
        assertEquals("DRAFT",state(kind,id)); assertEquals(10,quantity(productId));
    }

    @ParameterizedTest @ValueSource(strings={"import","export"})
    void reportsExcludeDraftsAndIncludeApprovedReceipts(String kind) throws Exception {
        Long id = draft(kind,"staff-a","WAREHOUSE_STAFF",3);
        String sku = products.findById(productId).orElseThrow().getSku();
        assertEquals(0,reportQuantity(kind,sku));
        mvc.perform(post(url(kind)+"/"+id+"/approve").with(user("manager-b").roles("WAREHOUSE_MANAGER")).with(csrf()))
            .andExpect(status().isOk());
        assertEquals(3,reportQuantity(kind,sku));
    }
    private long reportQuantity(String kind,String sku) {
        // The test wraps requests in one transaction; materialize JPA changes before JDBC reads.
        entityManager.flush();
        LocalDate today=LocalDate.now();
        return "import".equals(kind)?importReports.report(today,today).data().stream().filter(row -> sku.equals(row.sku())).mapToLong(row -> row.totalQuantity()).sum()
            : exportReports.report(today,today).data().stream().filter(row -> sku.equals(row.sku())).mapToLong(row -> row.totalQuantity()).sum();
    }

    @ParameterizedTest @ValueSource(strings={"import","export"})
    void finalizedReceiptAndDetailsCannotBeEdited(String kind) throws Exception {
        Long id = draft(kind,"staff-a","WAREHOUSE_STAFF",3);
        mvc.perform(post(url(kind)+"/"+id+"/approve").with(user("manager-b").roles("WAREHOUSE_MANAGER")).with(csrf())).andExpect(status().isOk());
        mvc.perform(put("/api/"+kind+"-receipts/"+id).with(user("staff-a").roles("WAREHOUSE_STAFF")).with(csrf())
            .contentType(MediaType.APPLICATION_JSON).content("{\"receiptCode\":\"changed\"}"))
            .andExpect(status().isConflict());
        Long lineId = "import".equals(kind)?importDetails.findByReceiptIdOrderByProductIdAsc(id).get(0).getId()
            :exportDetails.findByReceiptIdOrderByProductIdAsc(id).get(0).getId();
        mvc.perform(delete("/api/"+kind+"-details/"+lineId).with(user("manager-b").roles("WAREHOUSE_MANAGER")).with(csrf()))
            .andExpect(status().isConflict());
    }

    @ParameterizedTest @ValueSource(strings={"import","export"})
    @Transactional(propagation=Propagation.NOT_SUPPORTED)
    void failedSecondLineRollsBackFirstLineAndReceipt(String kind) throws Exception {
        Long id = draft(kind,"staff-a","WAREHOUSE_STAFF",3);
        Product other = new Product(); other.setSku("other-"+UUID.randomUUID()); other.setName("other");
        other.setUnit("pcs"); other.setPrice(BigDecimal.ONE); other=products.saveAndFlush(other);
        stock(other.getId(),"import".equals(kind)?Integer.MAX_VALUE:0);
        if ("import".equals(kind)) {
            ImportDetail detail=new ImportDetail(); detail.setReceiptId(id); detail.setProductId(other.getId()); detail.setQuantity(1); importDetails.saveAndFlush(detail);
        } else {
            ExportDetail detail=new ExportDetail(); detail.setReceiptId(id); detail.setProductId(other.getId()); detail.setQuantity(1); exportDetails.saveAndFlush(detail);
        }
        mvc.perform(post(url(kind)+"/"+id+"/approve").with(user("manager-b").roles("WAREHOUSE_MANAGER")).with(csrf()))
            .andExpect(status().isConflict());
        assertEquals(10,quantity(productId)); assertEquals("DRAFT",state(kind,id)); assertNull(approver(kind,id));
        System.out.println("APPROVAL ROLLBACK PASS: "+kind+" first inventory row unchanged after second line failed");
    }

    @ParameterizedTest @ValueSource(strings={"import","export"})
    @Transactional(propagation=Propagation.NOT_SUPPORTED)
    void concurrentApprovalChangesStockOnlyOnce(String kind) throws Exception {
        Long id = draft(kind,"staff-a","WAREHOUSE_STAFF",3);
        ExecutorService executor=Executors.newFixedThreadPool(2);
        CountDownLatch start=new CountDownLatch(1);
        try {
            Callable<Integer> approve=() -> {
                start.await();
                return mvc.perform(post(url(kind)+"/"+id+"/approve").with(user("manager-b").roles("WAREHOUSE_MANAGER")).with(csrf()))
                    .andReturn().getResponse().getStatus();
            };
            Future<Integer> first=executor.submit(approve), second=executor.submit(approve);
            start.countDown();
            var codes=new java.util.ArrayList<>(java.util.List.of(first.get(30,TimeUnit.SECONDS),second.get(30,TimeUnit.SECONDS)));
            codes.sort(Integer::compareTo); assertEquals(java.util.List.of(200,409),codes);
            assertEquals("import".equals(kind)?13:7,quantity(productId));
        } finally { start.countDown(); executor.shutdownNow(); }
    }

    @Test
    void inventoryCannotBeChangedDirectlyButManagerCanSetThreshold() throws Exception {
        Long id=inventory.findByWarehouseIdAndProductId(warehouseId,productId).orElseThrow().getId();
        for (String role:new String[]{"ADMIN","WAREHOUSE_STAFF","DIRECTOR"}) {
            mvc.perform(put("/api/inventories/"+id).with(user("user").roles(role)).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"minimumQuantity\":5}"))
                .andExpect(status().isForbidden());
        }
        mvc.perform(put("/api/inventories/"+id).with(user("manager").roles("WAREHOUSE_MANAGER")).with(csrf())
            .contentType(MediaType.APPLICATION_JSON).content("{\"quantity\":999,\"minimumQuantity\":5}"))
            .andExpect(status().isForbidden());
        mvc.perform(put("/api/inventories/"+id).with(user("manager").roles("WAREHOUSE_MANAGER")).with(csrf())
            .contentType(MediaType.APPLICATION_JSON).content("{\"minimumQuantity\":5}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.quantity").value(10)).andExpect(jsonPath("$.minimumQuantity").value(5));
        mvc.perform(post("/api/inventories").with(user("manager").roles("WAREHOUSE_MANAGER")).with(csrf())
            .contentType(MediaType.APPLICATION_JSON).content("{}")) .andExpect(status().isForbidden());
        mvc.perform(delete("/api/inventories/"+id).with(user("manager").roles("WAREHOUSE_MANAGER")).with(csrf()))
            .andExpect(status().isForbidden());
        assertEquals(10,quantity(productId));
    }

    @Test
    void transferCreatorCannotCompleteButAnotherManagerCan() throws Exception {
        Warehouse target=new Warehouse(); target.setCode("target-"+UUID.randomUUID()); target.setName("target");
        Long targetId=warehouses.saveAndFlush(target).getId();
        String code="transfer-"+UUID.randomUUID();
        String body="{\"transferCode\":\""+code+"\",\"fromWarehouseId\":"+warehouseId+
            ",\"toWarehouseId\":"+targetId+",\"productId\":"+productId+",\"quantity\":3}";
        mvc.perform(post("/api/transfers").with(user("manager-a").roles("WAREHOUSE_MANAGER")).with(csrf())
            .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isCreated());
        Long id=transfers.findAll().stream().filter(row -> code.equals(row.getTransferCode())).findFirst().orElseThrow().getId();
        mvc.perform(post("/api/transfers/"+id+"/complete").with(user("manager-a").roles("WAREHOUSE_MANAGER")).with(csrf()))
            .andExpect(status().isConflict());
        assertEquals(10,quantity(productId));
        mvc.perform(post("/api/transfers/"+id+"/complete").with(user("manager-b").roles("WAREHOUSE_MANAGER")).with(csrf()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.approvedBy").value("manager-b"));
        assertEquals(7,quantity(productId));
        assertEquals(3,inventory.findByWarehouseIdAndProductId(targetId,productId).orElseThrow().getQuantity());
    }
}
