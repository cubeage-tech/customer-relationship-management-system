package com.company.crm.common.storage;

import com.company.crm.common.exception.ApiException;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Locale;
import java.util.Map;

/**
 * SRS §15: what may be uploaded as a document or attachment — PDF, DOCX, XLSX, JPG and PNG up to
 * 25 MB. Both the extension and the file's leading bytes must match, so a renamed executable is
 * rejected. Shared by customer documents (FR-1.5) and quotation/ticket attachments (DB-E16).
 */
public final class UploadPolicy {

    public static final long MAX_BYTES = 25L * 1024 * 1024;

    private record FileKind(String contentType, byte[] signature) {}

    /** An accepted upload: its cleaned file name and the content type it will be served with. */
    public record Accepted(String fileName, String contentType) {}

    private static final byte[] ZIP = {0x50, 0x4B, 0x03, 0x04};
    private static final Map<String, FileKind> ALLOWED = Map.of(
            "pdf", new FileKind("application/pdf", "%PDF-".getBytes()),
            "docx", new FileKind("application/vnd.openxmlformats-officedocument.wordprocessingml.document", ZIP),
            "xlsx", new FileKind("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", ZIP),
            "jpg", new FileKind("image/jpeg", new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF}),
            "jpeg", new FileKind("image/jpeg", new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF}),
            "png", new FileKind("image/png", new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A}));

    private UploadPolicy() {
    }

    /** Throws a 400 explaining what's wrong, or returns what to store. */
    public static Accepted check(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw ApiException.badRequest("Choose a file to upload");
        }
        if (file.getSize() > MAX_BYTES) {
            throw ApiException.badRequest("File is too large — the limit is 25 MB");
        }
        String fileName = cleanFileName(file.getOriginalFilename());
        String extension = fileName.contains(".") ? fileName.substring(fileName.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT) : "";
        FileKind kind = ALLOWED.get(extension);
        if (kind == null || !startsWith(file, kind.signature())) {
            throw ApiException.badRequest("Only PDF, DOCX, XLSX, JPG and PNG files can be uploaded");
        }
        return new Accepted(fileName, kind.contentType());
    }

    private static boolean startsWith(MultipartFile file, byte[] signature) {
        try (InputStream in = file.getInputStream()) {
            byte[] head = in.readNBytes(signature.length);
            return Arrays.equals(head, signature);
        } catch (IOException e) {
            return false;
        }
    }

    /** Keeps only the base name, without control characters, at most 255 chars. */
    static String cleanFileName(String original) {
        String name = original == null ? "" : original.replace('\\', '/');
        name = name.substring(name.lastIndexOf('/') + 1).replaceAll("[\\p{Cntrl}\"]", "").trim();
        if (name.isEmpty()) {
            name = "document";
        }
        return name.length() <= 255 ? name : name.substring(name.length() - 255);
    }
}
