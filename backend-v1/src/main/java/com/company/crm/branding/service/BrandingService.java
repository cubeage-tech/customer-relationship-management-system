package com.company.crm.branding.service;

import com.company.crm.branding.dto.BrandingReqDto;
import com.company.crm.branding.dto.BrandingResDto;
import com.company.crm.common.audit.AuditAction;
import com.company.crm.common.audit.AuditService;
import com.company.crm.common.exception.ApiException;
import com.company.crm.common.storage.DocumentStorage;
import com.company.crm.tenant.entity.Tenant;
import com.company.crm.tenant.repository.TenantRepository;
import com.company.crm.user.entity.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Arrays;
import java.util.UUID;

/**
 * AC-4.2: the tenant admin's company branding — logo, colour and contact details — printed on
 * quotation PDFs. The logo is a PNG or JPEG up to 1 MB, kept in document storage.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BrandingService {

    public static final long MAX_LOGO_BYTES = 1024 * 1024;

    private static final byte[] PNG = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};
    private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};

    private final TenantRepository tenantRepository;
    private final DocumentStorage documentStorage;
    private final AuditService auditService;

    /** A logo ready to send to the browser. */
    public record Logo(String contentType, byte[] content) {}

    @Transactional(readOnly = true)
    public BrandingResDto get(User currentUser) {
        return toDto(findTenant(currentUser));
    }

    @Transactional
    public BrandingResDto update(User currentUser, BrandingReqDto dto) {
        Tenant tenant = findTenant(currentUser);
        tenant.setBrandAddress(blankToNull(dto.getAddress()));
        tenant.setBrandPhone(blankToNull(dto.getPhone()));
        tenant.setBrandEmail(blankToNull(dto.getEmail()));
        tenant.setBrandTaxId(blankToNull(dto.getTaxId()));
        tenant.setBrandColor(blankToNull(dto.getColor()));
        auditService.record(currentUser, AuditAction.RECORD_UPDATED, "branding", tenant.getId(), "company details for documents");
        return toDto(tenantRepository.save(tenant));
    }

    @Transactional
    public BrandingResDto uploadLogo(User currentUser, MultipartFile file) {
        Tenant tenant = findTenant(currentUser);
        if (file == null || file.isEmpty()) {
            throw ApiException.badRequest("Choose an image to upload");
        }
        if (file.getSize() > MAX_LOGO_BYTES) {
            throw ApiException.badRequest("The logo is too large — the limit is 1 MB");
        }
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read the uploaded logo", e);
        }
        String contentType = startsWith(bytes, PNG) ? "image/png" : startsWith(bytes, JPEG) ? "image/jpeg" : null;
        if (contentType == null || !isDecodableImage(bytes)) {
            throw ApiException.badRequest("The logo must be a PNG or JPG image");
        }

        String key = "branding/" + tenant.getId() + "/logo-" + UUID.randomUUID();
        try {
            documentStorage.store(key, new ByteArrayInputStream(bytes));
        } catch (IOException e) {
            log.error("Could not store logo for tenant {}: {}", tenant.getId(), e.getMessage());
            throw new UncheckedIOException("Could not store the logo", e);
        }
        String previousKey = tenant.getLogoKey();
        tenant.setLogoKey(key);
        tenant.setLogoContentType(contentType);
        afterCommit(() -> deleteQuietly(previousKey));
        auditService.record(currentUser, AuditAction.RECORD_UPDATED, "branding", tenant.getId(), "logo uploaded");
        return toDto(tenantRepository.save(tenant));
    }

    @Transactional
    public BrandingResDto removeLogo(User currentUser) {
        Tenant tenant = findTenant(currentUser);
        String previousKey = tenant.getLogoKey();
        tenant.setLogoKey(null);
        tenant.setLogoContentType(null);
        afterCommit(() -> deleteQuietly(previousKey));
        auditService.record(currentUser, AuditAction.RECORD_UPDATED, "branding", tenant.getId(), "logo removed");
        return toDto(tenantRepository.save(tenant));
    }

    @Transactional(readOnly = true)
    public Logo logo(User currentUser) {
        Tenant tenant = findTenant(currentUser);
        byte[] bytes = logoBytes(tenant);
        if (bytes == null) {
            throw ApiException.notFound("No logo uploaded");
        }
        return new Logo(tenant.getLogoContentType(), bytes);
    }

    /** The tenant's logo bytes, or null when there is none (or the file has gone missing). */
    public byte[] logoBytes(Tenant tenant) {
        if (tenant.getLogoKey() == null) {
            return null;
        }
        try (InputStream in = documentStorage.open(tenant.getLogoKey())) {
            return in.readAllBytes();
        } catch (IOException e) {
            log.warn("Logo file missing for tenant {}: {}", tenant.getId(), e.getMessage());
            return null;
        }
    }

    // ==================== Helpers ====================

    private static boolean startsWith(byte[] bytes, byte[] signature) {
        return bytes.length >= signature.length && Arrays.equals(Arrays.copyOf(bytes, signature.length), signature);
    }

    private static boolean isDecodableImage(byte[] bytes) {
        try {
            return ImageIO.read(new ByteArrayInputStream(bytes)) != null;
        } catch (IOException e) {
            return false;
        }
    }

    private void afterCommit(Runnable action) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    action.run();
                }
            });
        }
    }

    private void deleteQuietly(String key) {
        if (key == null) {
            return;
        }
        try {
            documentStorage.delete(key);
        } catch (IOException e) {
            log.warn("Could not delete old logo {}: {}", key, e.getMessage());
        }
    }

    private BrandingResDto toDto(Tenant t) {
        return new BrandingResDto(t.getCompanyName(), t.getBrandAddress(), t.getBrandPhone(), t.getBrandEmail(),
                t.getBrandTaxId(), t.getBrandColor(), t.getLogoKey() != null);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private Tenant findTenant(User currentUser) {
        if (currentUser.getTenant() == null) {
            throw ApiException.forbidden("Branding is configured per tenant");
        }
        return tenantRepository.findById(currentUser.getTenant().getId())
                .orElseThrow(() -> ApiException.notFound("Tenant not found"));
    }
}
