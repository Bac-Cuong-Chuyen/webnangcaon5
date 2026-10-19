package com.example.supplychain;

import java.io.ByteArrayInputStream;
import javax.xml.parsers.DocumentBuilderFactory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={
    "spring.datasource.url=jdbc:h2:mem:b09_public_web;DB_CLOSE_DELAY=-1",
    "spring.jpa.hibernate.ddl-auto=create-drop", "spring.sql.init.mode=never",
    "app.public-base-url=https://public.example.test/", "app.reporting.cache.enabled=false"
})
@AutoConfigureMockMvc
class B09PublicWebTest {
    @Autowired MockMvc mvc;

    @Test void publicHomeHasConfiguredCanonicalAndOpenGraph() throws Exception {
        for(String url:new String[]{"/","/index.html"}){
            mvc.perform(get(url).header("Host","untrusted.example.test"))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString("<meta name=\"description\"")))
                    .andExpect(content().string(containsString("property=\"og:title\"")))
                    .andExpect(content().string(containsString("property=\"og:description\"")))
                    .andExpect(content().string(containsString("href=\"https://public.example.test/\"")))
                    .andExpect(content().string(containsString("content=\"https://public.example.test/\"")))
                    .andExpect(content().string(not(containsString("__B09_PUBLIC_BASE__"))))
                    .andExpect(content().string(not(containsString("untrusted.example.test"))))
                    .andExpect(header().string(HttpHeaders.CACHE_CONTROL,containsString("no-cache")));
        }
    }

    @Test void sitemapIsValidXmlAndIncludesOnlyPublicHome() throws Exception {
        var response=mvc.perform(get("/sitemap.xml").header("Host","untrusted.example.test"))
                .andExpect(status().isOk()).andExpect(content().contentTypeCompatibleWith("application/xml"))
                .andReturn().getResponse();
        var factory=DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl",true);
        var document=factory.newDocumentBuilder().parse(new ByteArrayInputStream(response.getContentAsByteArray()));
        var locations=document.getElementsByTagNameNS("http://www.sitemaps.org/schemas/sitemap/0.9","loc");
        assertEquals(1,locations.getLength());
        assertEquals("https://public.example.test/",locations.item(0).getTextContent());
        mvc.perform(get("/robots.txt")).andExpect(status().isOk())
                .andExpect(content().string(containsString("Sitemap: https://public.example.test/sitemap.xml")))
                .andExpect(content().string(containsString("Disallow: /api/")))
                .andExpect(content().string(containsString("Disallow: /reports.html")))
                .andExpect(content().string(containsString("Disallow: /audit-logs.html")));
    }

    @Test void applicationAssetsRevalidateAndCanReturn304() throws Exception {
        var first=mvc.perform(get("/reports.css")).andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL,containsString("no-cache")))
                .andExpect(header().exists(HttpHeaders.LAST_MODIFIED)).andReturn().getResponse();
        mvc.perform(get("/reports.css").header(HttpHeaders.IF_MODIFIED_SINCE,first.getHeader(HttpHeaders.LAST_MODIFIED)))
                .andExpect(status().isNotModified());
        mvc.perform(get("/reports.js")).andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL,containsString("no-cache")));
        mvc.perform(get("/vendor/chart-4.4.1.umd.min.js")).andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL,containsString("max-age=31536000")))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL,containsString("immutable")));
    }

    @Test void privatePagesHaveDistinctDescriptionsAndAreNotIndexed() throws Exception {
        for(String url:new String[]{"/reports.html","/audit-logs.html"}){
            mvc.perform(get(url)).andExpect(status().isOk())
                    .andExpect(content().string(containsString("name=\"description\"")))
                    .andExpect(content().string(containsString("name=\"robots\" content=\"noindex, nofollow\"")))
                    .andExpect(header().string(HttpHeaders.CACHE_CONTROL,containsString("no-store")));
        }
    }

    @Test void apiCacheAndAccessRulesArePreserved() throws Exception {
        mvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL,containsString("no-store")));
        mvc.perform(get("/api/reports/imports").param("fromDate","2026-10-01").param("toDate","2026-10-15")
                .with(user("public-web-manager").roles("WAREHOUSE_MANAGER")))
                .andExpect(status().isOk()).andExpect(header().string(HttpHeaders.CACHE_CONTROL,containsString("no-store")));
        mvc.perform(get("/api/reports/imports").param("fromDate","2026-10-01").param("toDate","2026-10-15")
                .with(user("public-web-admin").roles("ADMIN"))).andExpect(status().isForbidden());
    }
}
