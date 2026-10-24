package com.company.crm.customer.service;

import com.company.crm.common.exception.ApiException;
import com.company.crm.common.scope.DataScopeResolver;
import com.company.crm.common.storage.DocumentStorage;
import com.company.crm.common.storage.UploadPolicy;
import com.company.crm.customer.dto.response.CustomerDocumentResDto;
import com.company.crm.customer.entity.Customer;
import com.company.crm.customer.entity.CustomerDocument;
import com.company.crm.customer.repository.CustomerDocumentRepository;
import com.company.crm.customer.repository.CustomerRepository;
import com.company.crm.user.entity.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.UUID;

/**
 * FR-1.5: upload, list, view/download and delete documents on a customer record. What may be
 * uploaded is set by {@link UploadPolicy} (SRS §15).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CustomerDocumentService {

    public static final long MAX_BYTES = UploadPolicy.MAX_BYTES;

    private final CustomerDocumentRepository documentRepository;
    private final CustomerRepository customerRepository;
    private final DataScopeResolver dataScopeResolver;
    private final DocumentStorage documentStorage;

    /** A stored document ready to stream back to the browser. */
    public record DocumentDownload(String fileName, String contentType, long sizeBytes, InputStream content) {}

    @Transactional(readOnly = true)
    public List<CustomerDocumentResDto> list(User currentUser, Long customerId) {
        findAccessibleCustomer(currentUser, customerId);
        return documentRepository.findByTenantIdAndCustomerIdOrderByCreatedAtDesc(requireTenantId(currentUser), customerId)
                .stream().map(this::toDto).toList();
    }

    @Transactional
    public CustomerDocumentResDto upload(User currentUser, Long customerId, MultipartFile file) {
        Customer customer = findAccessibleCustomer(currentUser, customerId);
        UploadPolicy.Accepted accepted = UploadPolicy.check(file);

        String key = customer.getTenant().getId() + "/" + customer.getId() + "/" + UUID.randomUUID();
        try (InputStream in = file.getInputStream()) {
            documentStorage.store(key, in);
        } catch (IOException e) {
            log.error("Could not store document for customer {}: {}", customerId, e.getMessage());
            throw new UncheckedIOException("Could not store the document", e);
        }

        CustomerDocument document = new CustomerDocument();
        document.setTenant(customer.getTenant());
        document.setCustomer(customer);
        document.setStorageKey(key);
        document.setStorageProvider(documentStorage.provider());
        document.setFileName(accepted.fileName());
        document.setContentType(accepted.contentType());
        document.setSizeBytes(file.getSize());
        document.setUploadedBy(currentUser);
        // If the row is rolled back, don't leave an orphan file behind.
        afterRollback(() -> deleteQuietly(key));
        return toDto(documentRepository.save(document));
    }

    @Transactional(readOnly = true)
    public DocumentDownload open(User currentUser, Long customerId, Long documentId) {
        findAccessibleCustomer(currentUser, customerId);
        CustomerDocument document = findDocument(currentUser, customerId, documentId);
        try {
            return new DocumentDownload(document.getFileName(), document.getContentType(), document.getSizeBytes(),
                    documentStorage.open(document.getStorageKey()));
        } catch (IOException e) {
            log.error("Stored file missing for document {}: {}", documentId, e.getMessage());
            throw ApiException.notFound("The file for this document is no longer available");
        }
    }

    @Transactional
    public void delete(User currentUser, Long customerId, Long documentId) {
        findAccessibleCustomer(currentUser, customerId);
        CustomerDocument document = findDocument(currentUser, customerId, documentId);
        documentRepository.delete(document);
        String key = document.getStorageKey();
        // Remove the bytes only once the row is really gone.
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    deleteQuietly(key);
                }
            });
        } else {
            deleteQuietly(key);
        }
    }

    // ==================== Helpers ====================

    private Customer findAccessibleCustomer(User currentUser, Long customerId) {
        Customer customer = customerRepository.findByIdAndTenantId(customerId, requireTenantId(currentUser))
                .orElseThrow(() -> ApiException.notFound("Customer not found"));
        dataScopeResolver.assertCanAccess(currentUser, customer.getOwner() != null ? customer.getOwner().getId() : null, "customer");
        return customer;
    }

    private CustomerDocument findDocument(User currentUser, Long customerId, Long documentId) {
        return documentRepository.findByIdAndTenantIdAndCustomerId(documentId, requireTenantId(currentUser), customerId)
                .orElseThrow(() -> ApiException.notFound("Document not found"));
    }

    private void afterRollback(Runnable action) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCompletion(int status) {
                    if (status == STATUS_ROLLED_BACK) {
                        action.run();
                    }
                }
            });
        }
    }

    private void deleteQuietly(String key) {
        try {
            documentStorage.delete(key);
        } catch (IOException e) {
            log.warn("Could not delete stored document {}: {}", key, e.getMessage());
        }
    }

    private CustomerDocumentResDto toDto(CustomerDocument d) {
        return new CustomerDocumentResDto(d.getId(), d.getFileName(), d.getContentType(), d.getSizeBytes(),
                d.getUploadedBy() != null ? d.getUploadedBy().getFullName() : null, d.getCreatedAt());
    }

    private Long requireTenantId(User currentUser) {
        if (currentUser.getTenant() == null) {
            throw ApiException.forbidden("Customers are scoped to a tenant");
        }
        return currentUser.getTenant().getId();
    }
}
