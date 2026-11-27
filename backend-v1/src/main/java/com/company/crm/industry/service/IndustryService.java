package com.company.crm.industry.service;

import com.company.crm.common.exception.ApiException;
import com.company.crm.industry.dto.request.IndustryReqDto;
import com.company.crm.industry.dto.response.IndustryResDto;
import com.company.crm.industry.entity.Industry;
import com.company.crm.industry.repository.IndustryRepository;
import com.company.crm.user.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;

/**
 * FR-1.6: each tenant's configurable customer industry list. Starts as the SRS list (seeded by
 * V24, or on first use for newer tenants). Admins add, rename and (de)activate entries; codes
 * never change, so existing customers keep a valid industry even when it is deactivated.
 */
@Service
@RequiredArgsConstructor
public class IndustryService {

    /** SRS 6.1 default list — same codes as V24 and the lead industry list. */
    private static final List<String[]> DEFAULTS = List.of(
            new String[]{"manufacturing", "Manufacturing"}, new String[]{"engineering", "Engineering"},
            new String[]{"real_estate", "Real Estate"}, new String[]{"construction", "Construction"},
            new String[]{"automobile", "Automobile"}, new String[]{"industrial_equipment", "Industrial Equipment"},
            new String[]{"software", "Software"}, new String[]{"trading", "Trading"},
            new String[]{"services", "Services"}, new String[]{"healthcare", "Healthcare"},
            new String[]{"other", "Other"});

    private final IndustryRepository industryRepository;

    @Transactional
    public List<IndustryResDto> list(User currentUser, boolean includeInactive) {
        Long tenantId = requireTenantId(currentUser);
        ensureDefaults(tenantId);
        return industryRepository.findByTenantIdOrderBySortOrderAscLabelAsc(tenantId).stream()
                .filter(i -> includeInactive || i.isActive())
                .map(this::toDto)
                .toList();
    }

    @Transactional
    public IndustryResDto create(User currentUser, IndustryReqDto dto) {
        Long tenantId = requireTenantId(currentUser);
        ensureDefaults(tenantId);
        String label = cleanLabel(dto.getLabel());
        if (industryRepository.existsByTenantIdAndLabelIgnoreCase(tenantId, label)) {
            throw ApiException.conflict("An industry with this name already exists");
        }
        List<Industry> existing = industryRepository.findByTenantIdOrderBySortOrderAscLabelAsc(tenantId);
        Industry industry = new Industry();
        industry.setTenantId(tenantId);
        industry.setCode(uniqueCode(label, existing));
        industry.setLabel(label);
        industry.setActive(true);
        industry.setSortOrder(existing.stream().mapToInt(Industry::getSortOrder).max().orElse(0) + 1);
        return toDto(industryRepository.save(industry));
    }

    /** Rename and/or (de)activate. At least one industry must stay active. */
    @Transactional
    public IndustryResDto update(User currentUser, String code, IndustryReqDto dto) {
        Long tenantId = requireTenantId(currentUser);
        ensureDefaults(tenantId);
        Industry industry = industryRepository.findById(new Industry.Key(tenantId, code))
                .orElseThrow(() -> ApiException.notFound("Industry not found"));
        if (dto.getLabel() != null) {
            String label = cleanLabel(dto.getLabel());
            if (!label.equalsIgnoreCase(industry.getLabel()) && industryRepository.existsByTenantIdAndLabelIgnoreCase(tenantId, label)) {
                throw ApiException.conflict("An industry with this name already exists");
            }
            industry.setLabel(label);
        }
        if (dto.getActive() != null && dto.getActive() != industry.isActive()) {
            if (!dto.getActive() && industryRepository.countByTenantIdAndActiveTrue(tenantId) <= 1) {
                throw ApiException.badRequest("At least one industry must stay active");
            }
            industry.setActive(dto.getActive());
        }
        return toDto(industryRepository.save(industry));
    }

    /** Throws 400 unless {@code code} is an active industry of the tenant (for new/changed customers). */
    @Transactional
    public void requireActive(Long tenantId, String code) {
        ensureDefaults(tenantId);
        boolean ok = code != null && industryRepository.findById(new Industry.Key(tenantId, code)).map(Industry::isActive).orElse(false);
        if (!ok) {
            throw ApiException.badRequest("Unknown or inactive industry: " + code);
        }
    }

    // ==================== Helpers ====================

    private void ensureDefaults(Long tenantId) {
        if (industryRepository.existsByTenantId(tenantId)) {
            return;
        }
        for (int i = 0; i < DEFAULTS.size(); i++) {
            industryRepository.insertIfAbsent(tenantId, DEFAULTS.get(i)[0], DEFAULTS.get(i)[1], i + 1);
        }
    }

    private static String cleanLabel(String raw) {
        String label = raw == null ? "" : raw.trim().replaceAll("\\s+", " ");
        if (label.isEmpty()) {
            throw ApiException.badRequest("Enter an industry name");
        }
        if (label.length() > 60) {
            throw ApiException.badRequest("Industry name must be at most 60 characters");
        }
        return label;
    }

    /** "Food & Beverage" → food_beverage (+ _2, _3… if taken), max 30 chars. */
    private static String uniqueCode(String label, List<Industry> existing) {
        String base = Normalizer.normalize(label, Normalizer.Form.NFKD).toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "_").replaceAll("^_+|_+$", "");
        if (base.isEmpty()) {
            base = "industry";
        }
        base = base.length() > 26 ? base.substring(0, 26) : base;
        String code = base;
        for (int n = 2; codeTaken(code, existing); n++) {
            code = base + "_" + n;
        }
        return code;
    }

    private static boolean codeTaken(String code, List<Industry> existing) {
        return existing.stream().anyMatch(i -> i.getCode().equals(code));
    }

    private IndustryResDto toDto(Industry i) {
        return new IndustryResDto(i.getCode(), i.getLabel(), i.isActive(), i.getSortOrder());
    }

    private Long requireTenantId(User currentUser) {
        if (currentUser.getTenant() == null) {
            throw ApiException.forbidden("Industries are configured per tenant");
        }
        return currentUser.getTenant().getId();
    }
}
