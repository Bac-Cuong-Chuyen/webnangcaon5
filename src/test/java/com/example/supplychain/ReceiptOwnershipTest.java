package com.example.supplychain;

import java.util.stream.Stream;
import com.example.supplychain.entity.*;
import com.example.supplychain.repository.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
    "spring.sql.init.mode=never",
    "spring.datasource.url=jdbc:h2:mem:b08_receipt_owner;DB_CLOSE_DELAY=-1",
    "spring.jpa.hibernate.ddl-auto=create-drop"
})
@AutoConfigureMockMvc
@Transactional
class ReceiptOwnershipTest {
    @Autowired MockMvc mvc;
    @Autowired ImportReceiptRepository imports;
    @Autowired ExportReceiptRepository exports;
    @Autowired ImportDetailRepository importDetails;
    @Autowired ExportDetailRepository exportDetails;

    private String receiptUrl(String kind) { return "/api/" + kind + "-receipts"; }
    private String detailUrl(String kind) { return "/api/" + kind + "-details"; }
    private Long receipt(String kind, String owner, String status) {
        if ("import".equals(kind)) {
            ImportReceipt row = new ImportReceipt();
            row.setCreatedBy(owner); row.setStatus(status); row.setReceiptCode("original");
            return imports.saveAndFlush(row).getId();
        }
        ExportReceipt row = new ExportReceipt();
        row.setCreatedBy(owner); row.setStatus(status); row.setReceiptCode("original");
        return exports.saveAndFlush(row).getId();
    }
    private Long detail(String kind, Long receiptId) {
        if ("import".equals(kind)) {
            ImportDetail row = new ImportDetail();
            row.setReceiptId(receiptId); row.setProductId(1L); row.setQuantity(2);
            return importDetails.saveAndFlush(row).getId();
        }
        ExportDetail row = new ExportDetail();
        row.setReceiptId(receiptId); row.setProductId(1L); row.setQuantity(2);
        return exportDetails.saveAndFlush(row).getId();
    }
    private String owner(String kind, Long id) {
        return "import".equals(kind) ? imports.findById(id).orElseThrow().getCreatedBy()
            : exports.findById(id).orElseThrow().getCreatedBy();
    }
    private String state(String kind, Long id) {
        return "import".equals(kind) ? imports.findById(id).orElseThrow().getStatus()
            : exports.findById(id).orElseThrow().getStatus();
    }
    private void complete(String kind, Long id) {
        if ("import".equals(kind)) { imports.findById(id).orElseThrow().setStatus("COMPLETED"); imports.flush(); }
        else { exports.findById(id).orElseThrow().setStatus("COMPLETED"); exports.flush(); }
    }

    @ParameterizedTest @ValueSource(strings = {"import", "export"})
    void creatorAndDraftStateComeFromServer(String kind) throws Exception {
        mvc.perform(post(receiptUrl(kind)).with(user("alice").roles("WAREHOUSE_STAFF")).with(csrf())
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"id\":9999,\"receiptCode\":\"test\",\"createdBy\":\"victim\",\"status\":\"COMPLETED\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.createdBy").value("alice"))
            .andExpect(jsonPath("$.status").value("DRAFT"))
            .andExpect(jsonPath("$.id").value(org.hamcrest.Matchers.not(9999)));
    }

    @ParameterizedTest @ValueSource(strings = {"import", "export"})
    void staffCannotReadChangeOrDeleteAnotherPersonsReceipt(String kind) throws Exception {
        Long id = receipt(kind, "alice", "DRAFT");
        mvc.perform(get(receiptUrl(kind)+"/"+id).with(user("bob").roles("WAREHOUSE_STAFF")))
            .andExpect(status().isForbidden());
        mvc.perform(put(receiptUrl(kind)+"/"+id).with(user("bob").roles("WAREHOUSE_STAFF")).with(csrf())
            .contentType(MediaType.APPLICATION_JSON).content("{\"receiptCode\":\"hacked\"}"))
            .andExpect(status().isForbidden());
        mvc.perform(delete(receiptUrl(kind)+"/"+id).with(user("bob").roles("WAREHOUSE_STAFF")).with(csrf()))
            .andExpect(status().isForbidden());
        assertEquals("alice", owner(kind,id));
        assertEquals("DRAFT", state(kind,id));
    }

    @ParameterizedTest @ValueSource(strings = {"import", "export"})
    void staffCanEditOwnDraftButCannotForgeOwnerOrFinalize(String kind) throws Exception {
        Long id = receipt(kind, "alice", "DRAFT");
        mvc.perform(put(receiptUrl(kind)+"/"+id).with(user("alice").roles("WAREHOUSE_STAFF")).with(csrf())
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"receiptCode\":\"edited\",\"createdBy\":\"bob\",\"status\":\"APPROVED\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.receiptCode").value("edited"))
            .andExpect(jsonPath("$.createdBy").value("alice")).andExpect(jsonPath("$.status").value("DRAFT"));
    }

    @ParameterizedTest @ValueSource(strings = {"import", "export"})
    void finalizedReceiptsCannotBeEditedEvenByManagers(String kind) throws Exception {
        Long id = receipt(kind, "alice", "COMPLETED");
        for (String role : new String[]{"WAREHOUSE_STAFF", "WAREHOUSE_MANAGER", "ADMIN"}) {
            mvc.perform(put(receiptUrl(kind)+"/"+id).with(user("alice").roles(role)).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"receiptCode\":\"changed\"}"))
                .andExpect(status().isConflict());
        }
        assertEquals("COMPLETED", state(kind,id));
    }

    @ParameterizedTest @ValueSource(strings = {"import", "export"})
    void staffListsOnlyOwnReceipts(String kind) throws Exception {
        receipt(kind,"alice","DRAFT"); receipt(kind,"bob","DRAFT"); receipt(kind,null,"DRAFT");
        mvc.perform(get(receiptUrl(kind)).with(user("alice").roles("WAREHOUSE_STAFF")))
            .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].createdBy").value("alice"));
    }

    @ParameterizedTest @ValueSource(strings = {"import", "export"})
    void directorCanReadButCannotWrite(String kind) throws Exception {
        Long id = receipt(kind,"alice","DRAFT");
        mvc.perform(get(receiptUrl(kind)+"/"+id).with(user("director").roles("DIRECTOR")))
            .andExpect(status().isOk());
        mvc.perform(post(receiptUrl(kind)).with(user("director").roles("DIRECTOR")).with(csrf())
            .contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isForbidden());
        mvc.perform(put(receiptUrl(kind)+"/"+id).with(user("director").roles("DIRECTOR")).with(csrf())
            .contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isForbidden());
    }

    @ParameterizedTest @ValueSource(strings = {"import", "export"})
    void staffCannotBypassOwnershipThroughDetailEndpoints(String kind) throws Exception {
        Long parent = receipt(kind,"alice","DRAFT"), id = detail(kind,parent);
        String body = "{\"receiptId\":"+parent+",\"productId\":1,\"quantity\":99}";
        mvc.perform(get(detailUrl(kind)+"/"+id).with(user("bob").roles("WAREHOUSE_STAFF")))
            .andExpect(status().isForbidden());
        mvc.perform(post(detailUrl(kind)).with(user("bob").roles("WAREHOUSE_STAFF")).with(csrf())
            .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isForbidden());
        mvc.perform(put(detailUrl(kind)+"/"+id).with(user("bob").roles("WAREHOUSE_STAFF")).with(csrf())
            .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isForbidden());
        mvc.perform(delete(detailUrl(kind)+"/"+id).with(user("bob").roles("WAREHOUSE_STAFF")).with(csrf()))
            .andExpect(status().isForbidden());
        mvc.perform(get(detailUrl(kind)).with(user("bob").roles("WAREHOUSE_STAFF")))
            .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
    }

    @ParameterizedTest @ValueSource(strings = {"import", "export"})
    void movingOwnDetailIntoAnotherPersonsReceiptIsDenied(String kind) throws Exception {
        Long own = receipt(kind,"bob","DRAFT"), other = receipt(kind,"alice","DRAFT");
        Long id = detail(kind,own);
        mvc.perform(put(detailUrl(kind)+"/"+id).with(user("bob").roles("WAREHOUSE_STAFF")).with(csrf())
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"receiptId\":"+other+",\"productId\":1,\"quantity\":99}"))
            .andExpect(status().isForbidden());
        Long actual = "import".equals(kind) ? importDetails.findById(id).orElseThrow().getReceiptId()
            : exportDetails.findById(id).orElseThrow().getReceiptId();
        assertEquals(own,actual);
    }

    @ParameterizedTest @ValueSource(strings = {"import", "export"})
    void ownDetailsAreWritableOnlyWhileDraft(String kind) throws Exception {
        Long parent = receipt(kind,"alice","DRAFT"), id = detail(kind,parent);
        String body = "{\"receiptId\":"+parent+",\"productId\":1,\"quantity\":3}";
        mvc.perform(put(detailUrl(kind)+"/"+id).with(user("alice").roles("WAREHOUSE_STAFF")).with(csrf())
            .contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isOk()).andExpect(jsonPath("$.quantity").value(3));
        complete(kind,parent);
        mvc.perform(put(detailUrl(kind)+"/"+id).with(user("alice").roles("WAREHOUSE_STAFF")).with(csrf())
            .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isConflict());
        mvc.perform(delete(detailUrl(kind)+"/"+id).with(user("alice").roles("WAREHOUSE_STAFF")).with(csrf()))
            .andExpect(status().isConflict());
    }

    @ParameterizedTest @ValueSource(strings = {"import", "export"})
    void adminAndManagerCanEditAndDeleteDrafts(String kind) throws Exception {
        for (String role : new String[]{"ADMIN","WAREHOUSE_MANAGER"}) {
            Long id = receipt(kind,"alice","DRAFT");
            mvc.perform(put(receiptUrl(kind)+"/"+id).with(user("manager").roles(role)).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"receiptCode\":\"edited\"}"))
                .andExpect(status().isOk());
            mvc.perform(delete(receiptUrl(kind)+"/"+id).with(user("manager").roles(role)).with(csrf()))
                .andExpect(status().isOk());
        }
    }

    static Stream<Arguments> legacyCases() {
        return Stream.of("/api/import", "/api/imports", "/api/exports").flatMap(path ->
            Stream.of("ADMIN","WAREHOUSE_STAFF","DIRECTOR","ANONYMOUS")
                .map(role -> Arguments.of(path,role,"ANONYMOUS".equals(role)?401:"DIRECTOR".equals(role)?403:400)));
    }
    @ParameterizedTest @MethodSource("legacyCases")
    void draftEntryPointsEnforceRoleAndValidateInput(String path, String role, int expected) throws Exception {
        var request = post(path).with(csrf()).contentType(MediaType.APPLICATION_JSON)
            .content("{\"receiptCode\":\"B08\",\"productId\":1,\"warehouseId\":1,\"quantity\":0}");
        if (!"ANONYMOUS".equals(role)) request.with(user("matrix-user").roles(role));
        mvc.perform(request).andExpect(status().is(expected));
    }
}
