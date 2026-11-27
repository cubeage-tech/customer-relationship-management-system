package com.company.crm.attachment.service;

import com.company.crm.attachment.entity.Attachment;
import com.company.crm.attachment.repository.AttachmentRepository;
import com.company.crm.common.exception.ApiException;
import com.company.crm.common.storage.DocumentStorage;
import com.company.crm.common.storage.UploadPolicy;
import com.company.crm.quotation.service.QuotationService;
import com.company.crm.support.service.ServiceTicketService;
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
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * DB-E16: files on quotations (Module 4) and service tickets (Module 6). Who may see or change a
 * record's files follows the record itself — the quotation's data scope, the ticket's
 * technician rules. Same file rules as customer documents ({@link UploadPolicy}).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AttachmentService {

    public record AttachmentRes(Long id, String fileName, String contentType, long sizeBytes, String uploadedByName,
                                LocalDateTime uploadedAt) {}

    public record Download(String fileName, String contentType, long sizeBytes, InputStream content) {}

    private final AttachmentRepository repository;
    private final DocumentStorage storage;
    private final QuotationService quotationService;
    private final ServiceTicketService ticketService;

    @Transactional(readOnly = true)
    public List<AttachmentRes> list(User user, String entityType, Long entityId) {
        Long tenantId = requireReadable(user, entityType, entityId);
        return repository.findByTenantIdAndEntityTypeAndEntityIdOrderByCreatedAtDesc(tenantId, entityType, entityId)
                .stream().map(AttachmentService::toRes).toList();
    }

    @Transactional
    public AttachmentRes upload(User user, String entityType, Long entityId, MultipartFile file) {
        Long tenantId = requireEditable(user, entityType, entityId);
        UploadPolicy.Accepted accepted = UploadPolicy.check(file);
        String key = tenantId + "/" + entityType + "/" + entityId + "/" + UUID.randomUUID();
        try (InputStream in = file.getInputStream()) {
            storage.store(key, in);
        } catch (IOException e) {
            log.error("Could not store attachment for {} {}: {}", entityType, entityId, e.getMessage());
            throw new UncheckedIOException("Could not store the attachment", e);
        }
        Attachment a = new Attachment();
        a.setTenantId(tenantId);
        a.setEntityType(entityType);
        a.setEntityId(entityId);
        a.setStorageProvider(storage.provider());
        a.setStorageKey(key);
        a.setFileName(accepted.fileName());
        a.setContentType(accepted.contentType());
        a.setSizeBytes(file.getSize());
        a.setUploadedBy(user);
        afterCompletion(false, key); // a rolled-back row leaves no orphan file
        return toRes(repository.save(a));
    }

    @Transactional(readOnly = true)
    public Download open(User user, String entityType, Long entityId, Long attachmentId) {
        Long tenantId = requireReadable(user, entityType, entityId);
        Attachment a = find(tenantId, entityType, entityId, attachmentId);
        try {
            return new Download(a.getFileName(), a.getContentType(), a.getSizeBytes(), storage.open(a.getStorageKey()));
        } catch (IOException e) {
            log.error("Stored file missing for attachment {}: {}", attachmentId, e.getMessage());
            throw ApiException.notFound("The file for this attachment is no longer available");
        }
    }

    @Transactional
    public void delete(User user, String entityType, Long entityId, Long attachmentId) {
        Long tenantId = requireEditable(user, entityType, entityId);
        Attachment a = find(tenantId, entityType, entityId, attachmentId);
        repository.delete(a);
        afterCompletion(true, a.getStorageKey()); // remove the bytes only once the row is really gone
    }

    // ==================== Access follows the record ====================

    private Long requireReadable(User user, String entityType, Long entityId) {
        return switch (entityType) {
            case Attachment.QUOTATION -> quotationService.requireAccessible(user, entityId).getTenant().getId();
            case Attachment.SERVICE_TICKET -> ticketService.requireViewable(user, entityId).getTenant().getId();
            default -> throw ApiException.badRequest("Unknown record type");
        };
    }

    private Long requireEditable(User user, String entityType, Long entityId) {
        return switch (entityType) {
            case Attachment.QUOTATION -> quotationService.requireAccessible(user, entityId).getTenant().getId();
            case Attachment.SERVICE_TICKET -> ticketService.requireEditable(user, entityId).getTenant().getId();
            default -> throw ApiException.badRequest("Unknown record type");
        };
    }

    private Attachment find(Long tenantId, String entityType, Long entityId, Long attachmentId) {
        return repository.findByIdAndTenantIdAndEntityTypeAndEntityId(attachmentId, tenantId, entityType, entityId)
                .orElseThrow(() -> ApiException.notFound("Attachment not found"));
    }

    /** onCommit = true: delete the file after commit; false: delete it if the transaction rolls back. */
    private void afterCompletion(boolean onCommit, String key) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            if (onCommit) {
                deleteQuietly(key);
            }
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (onCommit ? status == STATUS_COMMITTED : status == STATUS_ROLLED_BACK) {
                    deleteQuietly(key);
                }
            }
        });
    }

    private void deleteQuietly(String key) {
        try {
            storage.delete(key);
        } catch (IOException e) {
            log.warn("Could not delete stored attachment {}: {}", key, e.getMessage());
        }
    }

    private static AttachmentRes toRes(Attachment a) {
        return new AttachmentRes(a.getId(), a.getFileName(), a.getContentType(), a.getSizeBytes(),
                a.getUploadedBy() != null ? a.getUploadedBy().getFullName() : null, a.getCreatedAt());
    }
}
