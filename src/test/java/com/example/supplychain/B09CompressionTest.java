package com.example.supplychain;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.zip.GZIPInputStream;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.io.ClassPathResource;
import static org.junit.jupiter.api.Assertions.*;

/** Real embedded HTTP server: MockMvc does not exercise servlet gzip compression. */
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={
    "spring.config.import=file:./src/main/resources/application.properties",
    "spring.datasource.url=jdbc:h2:mem:b09_compression;DB_CLOSE_DELAY=-1",
    "spring.jpa.hibernate.ddl-auto=create-drop", "spring.sql.init.mode=never",
    "spring.h2.console.enabled=false", "app.reporting.cache.enabled=false"
})
class B09CompressionTest {
    @LocalServerPort int port;
    private HttpResponse<byte[]> request(String path,String encoding) throws Exception {
        try(var client=HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build()){
            return client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+path))
                    .header("Accept-Encoding",encoding).GET().build(),HttpResponse.BodyHandlers.ofByteArray());
        }
    }

    @Test void gzipAndIdentityDeliverExactlyTheSameVendorBytes() throws Exception {
        byte[] expected;
        try(var input=new ClassPathResource("static/vendor/chart-4.4.1.umd.min.js").getInputStream()){
            expected=input.readAllBytes();
        }
        var zipped=request("/vendor/chart-4.4.1.umd.min.js","gzip");
        assertEquals(200,zipped.statusCode());
        assertEquals("gzip",zipped.headers().firstValue("Content-Encoding").orElse(""));
        assertTrue(zipped.headers().allValues("Vary").stream().anyMatch(v->v.toLowerCase().contains("accept-encoding")));
        try(var input=new GZIPInputStream(new ByteArrayInputStream(zipped.body()))){
            assertArrayEquals(expected,input.readAllBytes());
        }
        assertTrue(zipped.body().length<expected.length);
        var identity=request("/vendor/chart-4.4.1.umd.min.js","identity");
        assertEquals(200,identity.statusCode());
        assertTrue(identity.headers().firstValue("Content-Encoding").isEmpty());
        assertArrayEquals(expected,identity.body());
        System.out.printf("B09_GZIP PASS original=%d compressed=%d decoded=identical%n",expected.length,zipped.body().length);
    }

    @Test void smallRobotsAndUnauthorizedApiAreNotCompressed() throws Exception {
        var robots=request("/robots.txt","gzip");
        assertEquals(200,robots.statusCode());
        assertTrue(robots.headers().firstValue("Content-Encoding").isEmpty());
        var denied=request("/api/auth/me","gzip");
        assertEquals(401,denied.statusCode());
        assertTrue(denied.headers().firstValue("Content-Encoding").isEmpty());
        assertTrue(denied.headers().firstValue("Cache-Control").orElse("").contains("no-store"));
    }
}
