package com.company.crm.customer.document;

import com.company.crm.common.exception.ApiException;
import com.company.crm.common.scope.DataScopeResolver;
import com.company.crm.customer.entity.Customer;
import com.company.crm.customer.repository.CustomerRepository;
import com.company.crm.user.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CustomerDocumentService {

    private static final long MAX_FILE_SIZE = 10 * 1024 * 1024;
    private static final Map<String, String> CONTENT_TYPES = Map.of(
            ".pdf", "application/pdf",
            ".png", "image/png",
            ".jpg", "image/jpeg",
            ".jpeg", "image/jpeg",
            ".txt", "text/plain",
            ".docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            ".xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    private final CustomerDocumentRepository documentRepository;
    private final CustomerRepository customerRepository;
    private final DataScopeResolver dataScopeResolver;
    private final CustomerDocumentStorage storage;

    @Transactional(readOnly = true)
    public List<CustomerDocumentResDto> list(User currentUser, Long customerId) {
        Long tenantId = requireTenantId(currentUser);
        Customer customer = findCustomer(customerId, tenantId);
        assertCanAccess(currentUser, customer);
        return documentRepository.findByTenantIdAndCustomerIdOrderByUploadedAtDescIdDesc(tenantId, customerId)
                .stream()
                .map(this::toDto)
                .toList();
    }

    @Transactional
    public CustomerDocumentResDto upload(User currentUser, Long customerId, MultipartFile file) {
        Long tenantId = requireTenantId(currentUser);
        Customer customer = findCustomer(customerId, tenantId);
        assertCanAccess(currentUser, customer);
        if (file == null || file.isEmpty()) {
            throw ApiException.badRequest("Choose a non-empty document to upload");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw ApiException.badRequest("Document exceeds the 10 MB upload limit");
        }

        String originalFilename = safeFilename(file.getOriginalFilename());
        String extension = extensionOf(originalFilename);
        String contentType = CONTENT_TYPES.get(extension);
        if (contentType == null || !hasValidSignature(extension, file)) {
            throw ApiException.badRequest("Supported documents are PDF, PNG, JPG, TXT, DOCX and XLSX files");
        }

        CustomerDocument document = new CustomerDocument();
        document.setTenant(customer.getTenant());
        document.setCustomer(customer);
        document.setUploadedBy(currentUser);
        document.setOriginalFilename(originalFilename);
        document.setStorageKey(UUID.randomUUID() + extension);
        document.setContentType(contentType);
        document.setFileSize(file.getSize());

        storage.store(document.getStorageKey(), file);
        try {
            return toDto(documentRepository.save(document));
        } catch (RuntimeException e) {
            try {
                storage.delete(document.getStorageKey());
            } catch (RuntimeException cleanupError) {
                e.addSuppressed(cleanupError);
            }
            throw e;
        }
    }

    @Transactional(readOnly = true)
    public CustomerDocumentDownload download(User currentUser, Long customerId, Long documentId) {
        CustomerDocument document = findDocument(currentUser, customerId, documentId);
        return new CustomerDocumentDownload(
                document.getOriginalFilename(),
                document.getContentType(),
                storage.read(document.getStorageKey()));
    }

    @Transactional
    public void delete(User currentUser, Long customerId, Long documentId) {
        CustomerDocument document = findDocument(currentUser, customerId, documentId);
        documentRepository.delete(document);
        storage.delete(document.getStorageKey());
    }

    private CustomerDocument findDocument(User currentUser, Long customerId, Long documentId) {
        Long tenantId = requireTenantId(currentUser);
        Customer customer = findCustomer(customerId, tenantId);
        assertCanAccess(currentUser, customer);
        return documentRepository.findByIdAndTenantIdAndCustomerId(documentId, tenantId, customerId)
                .orElseThrow(() -> ApiException.notFound("Customer document not found"));
    }

    private Customer findCustomer(Long customerId, Long tenantId) {
        return customerRepository.findByIdAndTenantId(customerId, tenantId)
                .orElseThrow(() -> ApiException.notFound("Customer not found"));
    }

    private void assertCanAccess(User currentUser, Customer customer) {
        dataScopeResolver.assertCanAccess(
                currentUser,
                customer.getOwner() == null ? null : customer.getOwner().getId(),
                "customer");
    }

    private Long requireTenantId(User currentUser) {
        if (currentUser.getTenant() == null) {
            throw ApiException.forbidden("Customer documents are scoped to a tenant");
        }
        return currentUser.getTenant().getId();
    }

    private CustomerDocumentResDto toDto(CustomerDocument document) {
        return new CustomerDocumentResDto(
                document.getId(),
                document.getOriginalFilename(),
                document.getContentType(),
                document.getFileSize(),
                document.getUploadedAt(),
                document.getUploadedBy().getId(),
                document.getUploadedBy().getFullName());
    }

    private String safeFilename(String filename) {
        if (filename == null || filename.isBlank()) {
            throw ApiException.badRequest("Document filename is required");
        }
        String basename = filename.replace('\\', '/');
        basename = basename.substring(basename.lastIndexOf('/') + 1).trim();
        if (basename.isBlank() || basename.length() > 255
                || basename.chars().anyMatch(Character::isISOControl)) {
            throw ApiException.badRequest("Document filename is invalid");
        }
        return basename;
    }

    private String extensionOf(String filename) {
        int dot = filename.lastIndexOf('.');
        return dot < 0 ? "" : filename.substring(dot).toLowerCase(Locale.ROOT);
    }

    private boolean hasValidSignature(String extension, MultipartFile file) {
        try {
            byte[] bytes = file.getBytes();
            return switch (extension) {
                case ".pdf" -> startsWith(bytes, "%PDF-".getBytes(StandardCharsets.US_ASCII));
                case ".png" -> startsWith(bytes, new byte[] {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A});
                case ".jpg", ".jpeg" -> bytes.length >= 3
                        && (bytes[0] & 0xFF) == 0xFF && (bytes[1] & 0xFF) == 0xD8 && (bytes[2] & 0xFF) == 0xFF;
                case ".docx", ".xlsx" -> bytes.length >= 4
                        && bytes[0] == 'P' && bytes[1] == 'K' && bytes[2] == 0x03 && bytes[3] == 0x04;
                case ".txt" -> isUtf8Text(bytes);
                default -> false;
            };
        } catch (java.io.IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Could not inspect the uploaded document", e);
        }
    }

    private boolean startsWith(byte[] content, byte[] prefix) {
        if (content.length < prefix.length) {
            return false;
        }
        for (int i = 0; i < prefix.length; i++) {
            if (content[i] != prefix[i]) {
                return false;
            }
        }
        return true;
    }

    private boolean isUtf8Text(byte[] bytes) {
        try {
            StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(bytes));
            return true;
        } catch (CharacterCodingException e) {
            return false;
        }
    }

    public record CustomerDocumentDownload(String filename, String contentType, byte[] content) {
    }
}
