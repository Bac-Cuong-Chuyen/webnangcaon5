package com.example.supplychain.service;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.IOException;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.example.supplychain.dto.FileUploadResponse;

@Service
public class FileUploadService {

    private static final long MAX_EXPANDED_XLSX_BYTES = 10L * 1024L * 1024L;
    private static final long MAX_IMAGE_PIXELS = 8_000_000L;
    private static final int MAX_XLSX_ENTRIES = 2_000;
    private static final int BUFFER_SIZE = 8_192;

    private final Path storageDirectory;
    private final long maxBytes;

    public FileUploadService(
            @Value("${app.upload.directory:uploads}") String uploadDirectory,
            @Value("${app.upload.max-bytes:1048576}") long maxBytes) {
        this.storageDirectory = Path.of(uploadDirectory).toAbsolutePath().normalize();
        this.maxBytes = maxBytes;

        if (maxBytes <= 0) {
            throw new IllegalStateException("Gioi han upload khong hop le");
        }
    }

    public FileUploadResponse store(MultipartFile file) {
        if (file == null || file.isEmpty()) throw new IllegalArgumentException("Tep tai len khong duoc rong");
        if (file.getSize() > maxBytes) throw new IllegalArgumentException("Tep vuot qua gioi han dung luong cho phep");

        String originalName = file.getOriginalFilename() == null ? "" : file.getOriginalFilename();
        originalName = originalName.replace('\\', '/');
        originalName = originalName.substring(originalName.lastIndexOf('/') + 1);
        int dot = originalName.lastIndexOf('.');
        if (dot < 0 || dot == originalName.length() - 1) {
            throw new IllegalArgumentException("Chi chap nhan tep CSV, XLSX, PNG hoac JPG");
        }
        String extension = originalName.substring(dot + 1).toLowerCase(Locale.ROOT);
        if (!Set.of("csv", "xlsx", "png", "jpg", "jpeg").contains(extension)) {
            throw new IllegalArgumentException("Chi chap nhan tep CSV, XLSX, PNG hoac JPG");
        }

        // Never rely only on MultipartFile.getSize(): cap the actual stream, too.
        final byte[] bytes;
        try (InputStream input = file.getInputStream();
                ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[BUFFER_SIZE];
            long total = 0;
            int count;
            while ((count = input.read(buffer)) != -1) {
                total += count;
                if (total > maxBytes) {
                    throw new IllegalArgumentException("Tep vuot qua gioi han dung luong cho phep");
                }
                output.write(buffer, 0, count);
            }
            bytes = output.toByteArray();
        } catch (IOException ex) {
            throw new IllegalArgumentException("Khong the doc tep tai len");
        }
        if (bytes.length == 0) {
            throw new IllegalArgumentException("Tep tai len khong duoc rong");
        }
        boolean valid = switch (extension) {
            case "csv" -> isValidCsv(bytes);
            case "xlsx" -> isValidXlsx(bytes);
            case "png", "jpg", "jpeg" -> isValidImage(bytes, extension);
            default -> false;
        };
        if (!valid) throw new IllegalArgumentException("Noi dung tep khong dung dinh dang " + extension.toUpperCase(Locale.ROOT));

        String generatedName = UUID.randomUUID() + "." + extension;
        Path destination = storageDirectory.resolve(generatedName).normalize();
        if (!destination.getParent().equals(storageDirectory)) throw new IllegalArgumentException("Ten tep khong hop le");

        try {
            Files.createDirectories(storageDirectory);
            if (Files.isSymbolicLink(storageDirectory)) {
                throw new IllegalArgumentException("Thu muc upload khong duoc la symbolic link");
            }
            Files.write(destination, bytes, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
        } catch (IOException ex) {
            throw new IllegalArgumentException("Khong the luu tep tai len");
        }
        String mediaType = switch (extension) {
            case "csv" -> "text/csv";
            case "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
            case "png" -> "image/png";
            default -> "image/jpeg";
        };
        return new FileUploadResponse(generatedName, bytes.length, mediaType);
    }

    private boolean isValidCsv(byte[] bytes) {
        if (bytes.length == 0) return false;
        try {
            String text = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes)).toString();
            if (text.startsWith("\uFEFF")) text = text.substring(1);
            if (text.isBlank()) return false;
            for (int i = 0; i < text.length(); i++) {
                char c = text.charAt(i);
                if (c == '\0' || (Character.isISOControl(c) && c != '\r' && c != '\n' && c != '\t')) return false;
            }
            String probe = text.stripLeading().toLowerCase(Locale.ROOT);
            return !probe.startsWith("<html") && !probe.startsWith("<!doctype html") && !probe.startsWith("<?php");
        } catch (CharacterCodingException ex) {
            return false;
        }
    }

    private boolean isValidImage(byte[] bytes, String extension) {
        if (bytes.length < 8) return false;
        boolean signatureMatches;
        if (extension.equals("png")) {
            byte[] signature = new byte[] {(byte) 0x89, 'P', 'N', 'G', 13, 10, 26, 10};
            signatureMatches = true;
            for (int i = 0; i < signature.length; i++) {
                if (bytes[i] != signature[i]) { signatureMatches = false; break; }
            }
        } else {
            signatureMatches = bytes.length >= 3
                    && (bytes[0] & 0xff) == 0xff && (bytes[1] & 0xff) == 0xd8
                    && (bytes[2] & 0xff) == 0xff;
        }
        if (!signatureMatches) return false;

        try (ImageInputStream imageStream = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            if (imageStream == null) return false;
            var readers = ImageIO.getImageReaders(imageStream);
            if (!readers.hasNext()) return false;
            ImageReader reader = readers.next();
            try {
                reader.setInput(imageStream, true, true);
                String format = reader.getFormatName().toLowerCase(Locale.ROOT);
                if (extension.equals("png") && !format.equals("png")) return false;
                if (!extension.equals("png") && !format.equals("jpeg")) return false;
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                if (width <= 0 || height <= 0 || (long) width * height > MAX_IMAGE_PIXELS) {
                    return false;
                }
                BufferedImage decoded = reader.read(0);
                return decoded != null && decoded.getWidth() == width && decoded.getHeight() == height;
            } finally {
                reader.dispose();
            }
        } catch (IOException | RuntimeException ex) {
            return false;
        }
    }

    private boolean isValidXlsx(byte[] bytes) {
        if (bytes.length < 4 || bytes[0] != 'P' || bytes[1] != 'K'
                || !((bytes[2] == 3 && bytes[3] == 4)
                || (bytes[2] == 5 && bytes[3] == 6)
                || (bytes[2] == 7 && bytes[3] == 8))) return false;

        Set<String> required = new HashSet<>();
        long expandedBytes = 0;
        int count = 0;
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(bytes))) {
            ZipEntry entry;
            byte[] buffer = new byte[BUFFER_SIZE];
            while ((entry = zip.getNextEntry()) != null) {
                if (++count > MAX_XLSX_ENTRIES) return false;
                String name = entry.getName().replace('\\', '/');
                if (name.startsWith("/") || name.equals("..") || name.startsWith("../")
                        || name.contains("/../") || name.matches("^[A-Za-z]:.*")) return false;
                String lower = name.toLowerCase(Locale.ROOT);
                if (lower.contains("vbaproject") || lower.startsWith("xl/externallinks/")
                        || lower.startsWith("xl/embeddings/") || lower.startsWith("xl/activex/")) return false;
                if (name.equals("[Content_Types].xml") || name.equals("xl/workbook.xml")) required.add(name);
                int read;
                while ((read = zip.read(buffer)) != -1) {
                    expandedBytes += read;
                    if (expandedBytes > MAX_EXPANDED_XLSX_BYTES) return false;
                }
                zip.closeEntry();
            }
        } catch (IOException ex) {
            return false;
        }
        return required.contains("[Content_Types].xml") && required.contains("xl/workbook.xml");
    }
}