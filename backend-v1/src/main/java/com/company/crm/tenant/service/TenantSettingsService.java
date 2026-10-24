package com.company.crm.tenant.service;

import com.company.crm.common.audit.AuditAction;
import com.company.crm.common.audit.AuditService;
import com.company.crm.common.enums.TicketPriority;
import com.company.crm.common.exception.ApiException;
import com.company.crm.tenant.dto.TenantSettingsDtos.Setting;
import com.company.crm.tenant.dto.TenantSettingsDtos.SettingsReq;
import com.company.crm.tenant.dto.TenantSettingsDtos.SettingsRes;
import com.company.crm.tenant.entity.TenantSettings;
import com.company.crm.tenant.repository.TenantSettingsRepository;
import com.company.crm.user.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * NFR-7.6b: per-tenant business thresholds — the SLA window per ticket priority (FR-6.4) and the
 * discount % that needs approval (BR-3). Each falls back to the deployment default
 * (application.properties) until a tenant admin sets its own. Pipeline stages (FR-3.x) and
 * commission rules (FR-8.5) are configured per tenant in their own settings.
 */
@Service
@RequiredArgsConstructor
public class TenantSettingsService {

    private final TenantSettingsRepository repository;
    private final AuditService auditService;

    @Value("${app.ticket.sla-hours.critical}")
    private long defaultSlaCritical;

    @Value("${app.ticket.sla-hours.high}")
    private long defaultSlaHigh;

    @Value("${app.ticket.sla-hours.medium}")
    private long defaultSlaMedium;

    @Value("${app.ticket.sla-hours.low}")
    private long defaultSlaLow;

    @Value("${app.quotation.discount-approval-threshold-percent}")
    private BigDecimal defaultDiscountThreshold;

    /** The SLA window that applies to a new (or re-prioritised) ticket of this tenant. */
    @Transactional(readOnly = true)
    public long slaHours(Long tenantId, TicketPriority priority) {
        Optional<TenantSettings> s = find(tenantId);
        return switch (priority) {
            case CRITICAL -> s.map(TenantSettings::getSlaHoursCritical).map(Integer::longValue).orElse(defaultSlaCritical);
            case HIGH -> s.map(TenantSettings::getSlaHoursHigh).map(Integer::longValue).orElse(defaultSlaHigh);
            case MEDIUM -> s.map(TenantSettings::getSlaHoursMedium).map(Integer::longValue).orElse(defaultSlaMedium);
            case LOW -> s.map(TenantSettings::getSlaHoursLow).map(Integer::longValue).orElse(defaultSlaLow);
        };
    }

    @Transactional(readOnly = true)
    public BigDecimal discountApprovalThreshold(Long tenantId) {
        return find(tenantId).map(TenantSettings::getDiscountApprovalThreshold).orElse(defaultDiscountThreshold);
    }

    @Transactional(readOnly = true)
    public SettingsRes get(User user) {
        return toRes(find(tenantId(user)).orElse(null));
    }

    /** Admin only (controller). Changes apply to tickets and quotations from now on; existing ones keep theirs. */
    @Transactional
    public SettingsRes update(User admin, SettingsReq dto) {
        Long tenantId = tenantId(admin);
        TenantSettings s = repository.findById(tenantId).orElseGet(() -> {
            TenantSettings fresh = new TenantSettings();
            fresh.setTenantId(tenantId);
            return fresh;
        });
        List<String> changed = new ArrayList<>();
        track(changed, "slaHoursCritical", s.getSlaHoursCritical(), dto.getSlaHoursCritical());
        track(changed, "slaHoursHigh", s.getSlaHoursHigh(), dto.getSlaHoursHigh());
        track(changed, "slaHoursMedium", s.getSlaHoursMedium(), dto.getSlaHoursMedium());
        track(changed, "slaHoursLow", s.getSlaHoursLow(), dto.getSlaHoursLow());
        track(changed, "discountApprovalThreshold", s.getDiscountApprovalThreshold(), dto.getDiscountApprovalThreshold());
        s.setSlaHoursCritical(dto.getSlaHoursCritical());
        s.setSlaHoursHigh(dto.getSlaHoursHigh());
        s.setSlaHoursMedium(dto.getSlaHoursMedium());
        s.setSlaHoursLow(dto.getSlaHoursLow());
        s.setDiscountApprovalThreshold(dto.getDiscountApprovalThreshold());
        TenantSettings saved = repository.save(s);
        if (!changed.isEmpty()) {
            auditService.record(admin, AuditAction.TENANT_SETTINGS_UPDATED, "tenant", tenantId, String.join("; ", changed));
        }
        return toRes(saved);
    }

    private SettingsRes toRes(TenantSettings s) {
        return new SettingsRes(
                setting(s != null ? s.getSlaHoursCritical() : null, defaultSlaCritical),
                setting(s != null ? s.getSlaHoursHigh() : null, defaultSlaHigh),
                setting(s != null ? s.getSlaHoursMedium() : null, defaultSlaMedium),
                setting(s != null ? s.getSlaHoursLow() : null, defaultSlaLow),
                new Setting<>(s != null && s.getDiscountApprovalThreshold() != null ? s.getDiscountApprovalThreshold() : defaultDiscountThreshold,
                        defaultDiscountThreshold, s != null && s.getDiscountApprovalThreshold() != null));
    }

    private static Setting<Long> setting(Integer custom, long fallback) {
        return new Setting<>(custom != null ? custom.longValue() : fallback, fallback, custom != null);
    }

    private static void track(List<String> changed, String name, Object before, Object after) {
        boolean same = before instanceof BigDecimal b && after instanceof BigDecimal a ? b.compareTo(a) == 0 : Objects.equals(before, after);
        if (!same) {
            changed.add(name + ": " + (before == null ? "default" : before) + " → " + (after == null ? "default" : after));
        }
    }

    private Optional<TenantSettings> find(Long tenantId) {
        return tenantId == null ? Optional.empty() : repository.findById(tenantId);
    }

    private static Long tenantId(User user) {
        if (user.getTenant() == null) {
            throw ApiException.forbidden("Settings are per tenant");
        }
        return user.getTenant().getId();
    }
}
