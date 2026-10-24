package com.company.crm.integration.service;

import com.company.crm.common.audit.AuditAction;
import com.company.crm.common.audit.AuditService;
import com.company.crm.common.crypto.DataEncryption;
import com.company.crm.common.exception.ApiException;
import com.company.crm.integration.dto.IntegrationDtos.CreatedKey;
import com.company.crm.integration.dto.IntegrationDtos.KeyReq;
import com.company.crm.integration.dto.IntegrationDtos.KeyRes;
import com.company.crm.integration.entity.IntegrationApiKey;
import com.company.crm.integration.repository.IntegrationApiKeyRepository;
import com.company.crm.user.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;

/**
 * API-G12: API keys an ERP uses to call /api/integrations/erp. A key is shown once when created;
 * only its SHA-256 is kept. Admins create and revoke keys; a revoked key stops working at once.
 */
@Service
@RequiredArgsConstructor
public class IntegrationKeyService {

    public static final String KEY_PREFIX = "crm_erp_";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final IntegrationApiKeyRepository repository;
    private final AuditService auditService;

    @Transactional(readOnly = true)
    public List<KeyRes> list(User admin) {
        return repository.findByTenantIdOrderByCreatedAtDesc(tenantId(admin)).stream().map(IntegrationKeyService::toRes).toList();
    }

    @Transactional
    public CreatedKey create(User admin, KeyReq dto) {
        byte[] secret = new byte[32];
        RANDOM.nextBytes(secret);
        String raw = KEY_PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(secret);
        IntegrationApiKey key = new IntegrationApiKey();
        key.setTenantId(tenantId(admin));
        key.setName(dto.getName().trim());
        key.setKeyPrefix(raw.substring(0, 12));
        key.setKeyHash(DataEncryption.sha256(raw));
        key.setCreatedBy(admin.getId());
        IntegrationApiKey saved = repository.save(key);
        auditService.record(admin, AuditAction.INTEGRATION_KEY_CREATED, "integration_key", saved.getId(), "name=" + saved.getName());
        return new CreatedKey(toRes(saved), raw);
    }

    @Transactional
    public void revoke(User admin, Long keyId) {
        IntegrationApiKey key = repository.findByIdAndTenantId(keyId, tenantId(admin))
                .orElseThrow(() -> ApiException.notFound("API key not found"));
        if (key.getRevokedAt() == null) {
            key.setRevokedAt(LocalDateTime.now());
            repository.save(key);
            auditService.record(admin, AuditAction.INTEGRATION_KEY_REVOKED, "integration_key", key.getId(), "name=" + key.getName());
        }
    }

    /** The tenant a request's X-Api-Key belongs to; 401 when missing, unknown or revoked. */
    @Transactional
    public Long authenticate(String rawKey) {
        if (rawKey == null || rawKey.isBlank()) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Send the integration API key in the X-Api-Key header", "API_KEY_REQUIRED");
        }
        IntegrationApiKey key = repository.findByKeyHashAndRevokedAtIsNull(DataEncryption.sha256(rawKey.trim()))
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "This API key is not valid", "API_KEY_INVALID"));
        key.setLastUsedAt(LocalDateTime.now());
        repository.save(key);
        return key.getTenantId();
    }

    private static Long tenantId(User user) {
        if (user.getTenant() == null) {
            throw ApiException.forbidden("Integrations are per tenant");
        }
        return user.getTenant().getId();
    }

    private static KeyRes toRes(IntegrationApiKey k) {
        return new KeyRes(k.getId(), k.getName(), k.getKeyPrefix(), k.getCreatedAt(), k.getLastUsedAt(), k.getRevokedAt());
    }
}
