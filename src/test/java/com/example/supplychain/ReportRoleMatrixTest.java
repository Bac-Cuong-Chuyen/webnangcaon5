package com.example.supplychain;

import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
    "spring.sql.init.mode=never",
    "spring.datasource.url=jdbc:h2:mem:b08_report_matrix;DB_CLOSE_DELAY=-1",
    "spring.jpa.hibernate.ddl-auto=create-drop"
})
@AutoConfigureMockMvc
@Transactional
class ReportRoleMatrixTest {
    @Autowired MockMvc mvc;

    static Stream<Arguments> reportCases() {
        return Stream.of("imports", "exports", "transfers").flatMap(report -> Stream.of(
            Arguments.of("ADMIN", report, 403),
            Arguments.of("WAREHOUSE_STAFF", report, 403),
            Arguments.of("WAREHOUSE_MANAGER", report, 200),
            Arguments.of("DIRECTOR", report, 200),
            Arguments.of("ANONYMOUS", report, 401)
        ));
    }

    @ParameterizedTest(name = "{0} GET reports/{1} expects {2}")
    @MethodSource("reportCases")
    void reportAccessFollowsB03Matrix(String role, String report, int expected) throws Exception {
        var request = get("/api/reports/" + report)
            .param("fromDate", "2026-10-01").param("toDate", "2026-10-19");
        if (!"ANONYMOUS".equals(role)) request.with(user("matrix-user").roles(role));
        var result = mvc.perform(request).andExpect(status().is(expected)).andReturn();
        System.out.printf("B03 REPORT MATRIX: %-18s %-10s expected=%d actual=%d PASS%n",
            role, report, expected, result.getResponse().getStatus());
    }
}
