package com.example.supplychain;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import com.example.supplychain.dto.FileUploadResponse;
import com.example.supplychain.service.FileUploadService;

/** B07 V4: additional upload regression tests; original 25 API tests are retained. */
class FileUploadHardeningTest {

    @TempDir
    Path temp;

    private FileUploadService service() {
        return new FileUploadService(temp.resolve("uploads").toString(), 1_048_576);
    }

    private MockMultipartFile file(String name, byte[] bytes) {
        return new MockMultipartFile("file", name, "application/octet-stream", bytes);
    }

    @Test
    void csvIsStoredWithRandomServerGeneratedName() throws Exception {
        byte[] content = "sku,quantity\nSKU-01,10\n".getBytes(StandardCharsets.UTF_8);
        FileUploadResponse result = service().store(file("../../unsafe.csv", content));
        assertTrue(result.fileName().matches("[0-9a-fA-F-]{36}\\.csv"));
        assertFalse(result.fileName().contains(".."));
        assertEquals("text/csv", result.fileType());
        assertArrayEquals(content, Files.readAllBytes(temp.resolve("uploads").resolve(result.fileName())));
    }

    @Test
    void emptyUploadIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> service().store(file("blank.csv", new byte[0])));
    }

    @Test
    void unsupportedExecutableExtensionIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> service().store(file("payload.jsp", new byte[]{1, 2, 3})));
    }

    @Test
    void fakePngIsRejectedEvenWhenNamedAsImage() {
        assertThrows(IllegalArgumentException.class,
                () -> service().store(file("not-really-image.png", "hello".getBytes(StandardCharsets.UTF_8))));
    }

    @Test
    void truncatedPngIsRejected() throws Exception {
        byte[] original = png();
        assertThrows(IllegalArgumentException.class,
                () -> service().store(file("partial.png", Arrays.copyOf(original, 24))));
    }

    @Test
    void pngWithJpegFileExtensionIsRejected() throws Exception {
        assertThrows(IllegalArgumentException.class, () -> service().store(file("mismatch.jpg", png())));
    }

    @Test
    void genuinePngIsAccepted() throws Exception {
        FileUploadResponse result = service().store(file("image.png", png()));
        assertEquals("image/png", result.fileType());
        assertTrue(Files.exists(temp.resolve("uploads").resolve(result.fileName())));
    }

    @Test
    void invalidUtf8CsvIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> service().store(file("bad.csv", new byte[]{(byte) 0xff, (byte) 0xfe, 0} )));
    }

    @Test
    void exceedingConfiguredSizeDoesNotCreateFile() throws Exception {
        FileUploadService limited = new FileUploadService(temp.resolve("uploads").toString(), 10);
        assertThrows(IllegalArgumentException.class,
                () -> limited.store(file("large.csv", new byte[11])));
        assertFalse(Files.exists(temp.resolve("uploads")));
    }

    @Test
    void actualStreamSizeIsCheckedEvenIfMetadataUnderstatesIt() throws Exception {
        byte[] oversized = new byte[2_048];
        MockMultipartFile dishonest = new MockMultipartFile("file", "fake-size.csv", "text/csv", oversized) {
            @Override
            public long getSize() { return 1; }
        };
        FileUploadService limited = new FileUploadService(temp.resolve("uploads").toString(), 1_024);
        assertThrows(IllegalArgumentException.class, () -> limited.store(dishonest));
        assertFalse(Files.exists(temp.resolve("uploads")));
    }

    @Test
    void invalidWorkbookWithoutRequiredXmlIsRejected() throws Exception {
        assertThrows(IllegalArgumentException.class,
                () -> service().store(file("fake.xlsx", xlsx(false, false))));
    }

    @Test
    void validWorkbookIsAccepted() throws Exception {
        FileUploadResponse result = service().store(file("test.xlsx", xlsx(true, false)));
        assertEquals("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", result.fileType());
    }

    @Test
    void workbookWithMacroPayloadIsRejected() throws Exception {
        assertThrows(IllegalArgumentException.class,
                () -> service().store(file("macro-disguised.xlsx", xlsx(true, true))));
    }

    private static byte[] png() throws IOException {
        BufferedImage image = new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB);
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            assertTrue(ImageIO.write(image, "png", output));
            return output.toByteArray();
        }
    }

    private static byte[] xlsx(boolean withXml, boolean withMacro) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(out)) {
            zip.putNextEntry(new ZipEntry("[Content_Types].xml"));
            zip.write("<Types/>".getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
            if (withXml) {
                zip.putNextEntry(new ZipEntry("xl/workbook.xml"));
                zip.write("<workbook/>".getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
            if (withMacro) {
                zip.putNextEntry(new ZipEntry("xl/vbaProject.bin"));
                zip.write(new byte[]{1, 2, 3});
                zip.closeEntry();
            }
        }
        return out.toByteArray();
    }
}
