package com.example.supplychain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import javax.imageio.ImageIO;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import com.example.supplychain.entity.Role;
import com.example.supplychain.entity.UserAccount;
import com.example.supplychain.repository.AuditLogRepository;
import com.example.supplychain.repository.UserAccountRepository;

@SpringBootTest
@AutoConfigureMockMvc
class SecurityHardeningTest {

    private static final String VALID_IMPORT_BODY =
            "{\"receiptCode\":\"SEC-001\",\"productId\":1,\"warehouseId\":1,\"quantity\":1}";

    @Autowired MockMvc mvc;
    @Autowired UserAccountRepository userAccountRepository;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired AuditLogRepository auditLogRepository;
    @Value("${app.upload.directory}") String uploadDirectory;

    @AfterEach
    void cleanupUploadedFiles() throws IOException {
        Path directory = Path.of(uploadDirectory).toAbsolutePath().normalize();
        if (Files.isDirectory(directory)) {
            try (Stream<Path> paths = Files.list(directory)) {
                paths.filter(Files::isRegularFile).forEach(path -> {
                    try { Files.deleteIfExists(path); } catch (IOException ignored) { }
                });
            }
        }
    }

    @Test // TC16
    void tc16_validCsvIsRenamedAndStoredOutsideApplicationDirectory() throws Exception {
        byte[] csv = "sku,quantity\nSKU-A,10\n".getBytes(StandardCharsets.UTF_8);
        MockMultipartFile file = new MockMultipartFile(
                "file", "../../../../evil.csv", "text/csv", csv);

        String response = mvc.perform(multipart("/api/files/upload").file(file).with(csrf())
                        .with(user("warehouse-staff").roles("WAREHOUSE_STAFF")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.fileType").value("text/csv"))
                .andReturn().getResponse().getContentAsString();

        var json = new com.fasterxml.jackson.databind.ObjectMapper().readTree(response);
        String generatedName = json.get("fileName").asText();
        Path directory = Path.of(uploadDirectory).toAbsolutePath().normalize();
        Path savedFile = directory.resolve(generatedName).normalize();
        Path applicationDirectory = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();

        assertTrue(generatedName.matches("[0-9a-fA-F-]{36}\\.csv"));
        assertFalse(generatedName.contains(".."));
        assertTrue(savedFile.startsWith(directory));
        assertFalse(savedFile.startsWith(applicationDirectory));
        assertTrue(Files.exists(savedFile));
        assertEquals(csv.length, Files.size(savedFile));
    }

    @Test // TC17
    void tc17_unsupportedExtensionIsRejected() throws Exception {
        for (String name : new String[] {"payload.jsp", "payload.gif", "payload.webp", "payload.svg"}) {
            MockMultipartFile file = new MockMultipartFile(
                    "file", name, "application/octet-stream", "not an approved upload".getBytes(StandardCharsets.UTF_8));
            mvc.perform(multipart("/api/files/upload").file(file).with(csrf())
                            .with(user("warehouse-staff").roles("WAREHOUSE_STAFF")))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        }
    }

    @Test // TC18
    void tc18UploadChecksAllSupportedImageTypesAndRealSignatures() throws Exception {
        MockMultipartFile fakeImage = new MockMultipartFile("file", "payload.png", "image/png",
                "this is a script, not an image".getBytes(StandardCharsets.UTF_8));
        mvc.perform(multipart("/api/files/upload").file(fakeImage).with(csrf())
                        .with(user("warehouse-staff").roles("WAREHOUSE_STAFF")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        BufferedImage sourceImage = new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream pngBytes = new ByteArrayOutputStream();
        assertTrue(ImageIO.write(sourceImage, "png", pngBytes));
        byte[] validPng = pngBytes.toByteArray();
        MockMultipartFile validPngFile = new MockMultipartFile("file", "one-pixel.png", "image/png", validPng);
        mvc.perform(multipart("/api/files/upload").file(validPngFile).with(csrf())
                        .with(user("warehouse-staff").roles("WAREHOUSE_STAFF")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.fileType").value("image/png"));

        MockMultipartFile truncatedPng = new MockMultipartFile("file", "truncated.png", "image/png",
                java.util.Arrays.copyOf(validPng, Math.min(validPng.length, 24)));
        mvc.perform(multipart("/api/files/upload").file(truncatedPng).with(csrf())
                        .with(user("warehouse-staff").roles("WAREHOUSE_STAFF")))
                .andExpect(status().isBadRequest());

        ByteArrayOutputStream jpegBytes = new ByteArrayOutputStream();
        assertTrue(ImageIO.write(sourceImage, "jpeg", jpegBytes));
        byte[] validJpeg = jpegBytes.toByteArray();
        for (String filename : new String[] {"inventory.jpg", "inventory.jpeg"}) {
            MockMultipartFile validJpegFile = new MockMultipartFile("file", filename, "image/jpeg", validJpeg);
            mvc.perform(multipart("/api/files/upload").file(validJpegFile).with(csrf())
                            .with(user("warehouse-staff").roles("WAREHOUSE_STAFF")))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.fileType").value("image/jpeg"));
        }

        MockMultipartFile fakeJpeg = new MockMultipartFile("file", "fake.jpg", "image/jpeg",
                "not really a jpeg".getBytes(StandardCharsets.UTF_8));
        mvc.perform(multipart("/api/files/upload").file(fakeJpeg).with(csrf())
                        .with(user("warehouse-staff").roles("WAREHOUSE_STAFF")))
                .andExpect(status().isBadRequest());

        MockMultipartFile extensionMismatch = new MockMultipartFile("file", "picture.png", "image/png", validJpeg);
        mvc.perform(multipart("/api/files/upload").file(extensionMismatch).with(csrf())
                        .with(user("warehouse-staff").roles("WAREHOUSE_STAFF")))
                .andExpect(status().isBadRequest());

        MockMultipartFile fakeWorkbook = new MockMultipartFile("file", "fake.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                new byte[] {'P', 'K', 3, 4, 0, 0, 0, 0});
        mvc.perform(multipart("/api/files/upload").file(fakeWorkbook).with(csrf())
                        .with(user("warehouse-staff").roles("WAREHOUSE_STAFF")))
                .andExpect(status().isBadRequest());

        MockMultipartFile validWorkbook = new MockMultipartFile("file", "inventory.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", validXlsxBytes());
        mvc.perform(multipart("/api/files/upload").file(validWorkbook).with(csrf())
                        .with(user("warehouse-staff").roles("WAREHOUSE_STAFF")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.fileType").value(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
    }

    @Test // TC19
    void tc19_oversizedUploadIsRejectedAndNotSaved() throws Exception {
        byte[] tooLarge = new byte[1_048_577];
        java.util.Arrays.fill(tooLarge, (byte) 'A');
        MockMultipartFile file = new MockMultipartFile("file", "large.csv", "text/csv", tooLarge);
        mvc.perform(multipart("/api/files/upload").file(file).with(csrf())
                        .with(user("warehouse-staff").roles("WAREHOUSE_STAFF")))
                .andExpect(status().is4xxClientError());
        Path directory = Path.of(uploadDirectory).toAbsolutePath().normalize();
        if (Files.exists(directory)) {
            try (Stream<Path> paths = Files.list(directory)) {
                assertEquals(0, paths.filter(Files::isRegularFile).count());
            }
        }
    }

    @Test // TC20
    void tc20_anonymousCannotUploadOrUseImportAlias() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "inventory.csv", "text/csv", "sku,quantity\nA,1\n".getBytes(StandardCharsets.UTF_8));
        mvc.perform(multipart("/api/files/upload").file(file).with(csrf()))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/import").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_IMPORT_BODY))
                .andExpect(status().isUnauthorized());
    }

    @Test // TC21
    void tc21StateChangingRequestRequiresCsrfToken() throws Exception {
        mvc.perform(post("/api/imports")
                        .with(user("warehouse-staff").roles("WAREHOUSE_STAFF"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_IMPORT_BODY))
                .andExpect(status().isForbidden());
    }

    @Test // TC22
    void tc22RegistrationCannotEscalateRoleAndPasswordIsHashed() throws Exception {
        String username = "v4-role-" + UUID.randomUUID().toString().substring(0, 8);
        String rawPassword = "SafePass123!";
        mvc.perform(post("/api/auth/register").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\""
                                + rawPassword + "\",\"role\":\"ADMIN\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("WAREHOUSE_STAFF"));

        UserAccount saved = userAccountRepository.findByUsername(username).orElseThrow();
        assertEquals(Role.WAREHOUSE_STAFF, saved.getRole());
        assertTrue(passwordEncoder.matches(rawPassword, saved.getPassword()));
        assertFalse(rawPassword.equals(saved.getPassword()));

        MockHttpSession preLoginSession = new MockHttpSession();
        String preLoginSessionId = preLoginSession.getId();
        mvc.perform(post("/api/auth/login").session(preLoginSession).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"" + rawPassword + "\"}"))
                .andExpect(status().isOk());
        assertNotEquals(preLoginSessionId, preLoginSession.getId(),
                "Session identifier must change after successful login");
        mvc.perform(post("/api/auth/logout").session(preLoginSession).with(csrf()))
                .andExpect(status().isOk());
        assertTrue(preLoginSession.isInvalid(), "Logout must invalidate the session");
    }

    @Test // TC23
    void tc23RepeatedLoginFailuresAreThrottledAndAudited() throws Exception {
        String username = "missing-" + UUID.randomUUID().toString().substring(0, 8);
        long logCountBefore = auditLogRepository.count();
        String body = "{\"username\":\"" + username + "\",\"password\":\"WrongPass123!\"}";

        for (int i = 0; i < 5; i++) {
            mvc.perform(post("/api/auth/login").with(csrf())
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isUnauthorized());
        }
        mvc.perform(post("/api/auth/login").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isTooManyRequests());
        assertTrue(auditLogRepository.count() >= logCountBefore + 6);
    }

    @Test // TC24
    void tc24RoleBoundariesAndObjectLevelAccessAreEnforced() throws Exception {
        mvc.perform(get("/api/user-accounts").with(user("staff").roles("WAREHOUSE_STAFF")))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/test").with(user("director").roles("DIRECTOR")))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/audit-logs").with(user("staff").roles("WAREHOUSE_STAFF")))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/users/other-user").with(user("alice").roles("WAREHOUSE_STAFF")))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/user-accounts").with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk());
        mvc.perform(get("/api/audit-logs").with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk());
    }

    @Test // TC25
    void tc25SecurityHeadersAndGenericParameterErrorsArePresent() throws Exception {
        mvc.perform(get("/api/test/protected").secure(true)
                        .with(user("staff").roles("WAREHOUSE_STAFF")))
                .andExpect(status().isOk())
                .andExpect(header().exists("Content-Security-Policy"))
                .andExpect(header().exists("Referrer-Policy"))
                .andExpect(header().exists("X-Content-Type-Options"))
                .andExpect(header().string("X-Frame-Options", "SAMEORIGIN"))
                .andExpect(header().exists("Strict-Transport-Security"));

        mvc.perform(get("/api/products/{id}", "' OR '1'='1")
                        .with(user("staff").roles("WAREHOUSE_STAFF")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PARAMETER"))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("org.springframework"))))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("SQLException"))));
    }
    private byte[] validXlsxBytes() throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(output)) {
            zip.putNextEntry(new ZipEntry("[Content_Types].xml"));
            zip.write("<?xml version=\"1.0\"?><Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\"></Types>".getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
            zip.putNextEntry(new ZipEntry("xl/workbook.xml"));
            zip.write("<?xml version=\"1.0\"?><workbook xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\"></workbook>".getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        return output.toByteArray();
    }

}
