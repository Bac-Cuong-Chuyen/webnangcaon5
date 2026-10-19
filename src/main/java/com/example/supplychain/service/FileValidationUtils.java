package com.example.supplychain.service;

import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Map;

public class FileValidationUtils {

    // Định nghĩa Magic Bytes (chữ ký đầu file) cho các định dạng an toàn
    private static final Map<String, byte[]> MAGIC_BYTES = Map.of(
        "jpg", new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF},
        "png", new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47},
        "pdf", new byte[]{0x25, 0x50, 0x44, 0x46}
    );

    public static boolean isValidFile(MultipartFile file, String allowedExtension) {
        if (file.isEmpty()) return false;

        byte[] expectedHeader = MAGIC_BYTES.get(allowedExtension.toLowerCase());
        if (expectedHeader == null) return false;

        try (InputStream is = file.getInputStream()) {
            byte[] fileHeader = new byte[expectedHeader.length];
            int bytesRead = is.read(fileHeader);
            if (bytesRead < expectedHeader.length) return false;

            return Arrays.equals(expectedHeader, fileHeader);
        } catch (IOException e) {
            return false;
        }
    }
}