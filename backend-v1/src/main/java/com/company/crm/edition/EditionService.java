package com.company.crm.edition;

import com.company.crm.common.audit.AuditAction;
import com.company.crm.common.audit.AuditService;
import com.company.crm.common.exception.ApiException;
import com.company.crm.customfield.service.CustomFieldService;
import com.company.crm.sales.dto.request.PipelineStageReqDto;
import com.company.crm.sales.service.PipelineStageService;
import com.company.crm.tenant.entity.Tenant;
import com.company.crm.tenant.repository.TenantRepository;
import com.company.crm.user.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * BIZ-2 (SRS 2.1 / 25.3): industry editions on one common core. An edition is a ready-made setup —
 * its industries' custom fields (ARC-9 templates) and the pipeline stages that industry sells
 * through. Choosing one adds what is missing and never removes anything, so a tenant can switch,
 * or add a second edition's fields, safely. Chosen at sign-up or later in Settings.
 */
@Service
@RequiredArgsConstructor
public class EditionService {

    public record Edition(String key, String label, String description, List<String> industries, List<String> stages) {}

    public record EditionsRes(String current, List<Edition> editions) {}

    public record Applied(String edition, int fieldsAdded, int stagesAdded) {}

    public static final List<Edition> EDITIONS = List.of(
            new Edition("standard", "Standard CRM", "The common CRM for any business.", List.of(), List.of()),
            new Edition("manufacturing", "Manufacturing CRM",
                    "Plant capacity and certifications on customers, order volumes and delivery schedules on deals, and sample/trial and technical review stages.",
                    List.of("manufacturing", "engineering"), List.of("Sample / trial", "Technical review")),
            new Edition("real_estate", "Real Estate CRM",
                    "Budget, property type and location on leads, possession dates on deals, and site visit and booking stages.",
                    List.of("real_estate", "construction"), List.of("Site visit", "Booking")),
            new Edition("healthcare", "Healthcare CRM",
                    "Facility type and beds on customers, accreditation on leads, and product demo and procurement review stages.",
                    List.of("healthcare"), List.of("Product demo", "Procurement review")),
            new Edition("industrial_sales", "Industrial Sales CRM",
                    "Installed equipment, credit limits and payment terms on customers, AMC and warranty on deals, and site survey and technical offer stages.",
                    List.of("industrial_equipment", "trading", "automobile"), List.of("Site survey", "Technical offer")));

    private final TenantRepository tenantRepository;
    private final CustomFieldService customFieldService;
    private final PipelineStageService pipelineStageService;
    private final AuditService auditService;

    @Transactional(readOnly = true)
    public EditionsRes list(User user) {
        return new EditionsRes(tenant(user).getEdition(), EDITIONS);
    }

    /** Sets the tenant's edition and adds its fields and stages (idempotent). */
    @Transactional
    public Applied apply(User admin, String key) {
        Edition edition = EDITIONS.stream().filter(e -> e.key().equals(key)).findFirst()
                .orElseThrow(() -> ApiException.badRequest("Unknown edition: " + key));
        int fields = 0;
        for (String industry : edition.industries()) {
            fields += customFieldService.applyTemplate(admin, industry).created();
        }
        Set<String> existing = pipelineStageService.list(admin, true).stream()
                .map(s -> s.label().toLowerCase(Locale.ROOT)).collect(Collectors.toSet());
        int stages = 0;
        for (String label : edition.stages()) {
            if (!existing.contains(label.toLowerCase(Locale.ROOT))) {
                PipelineStageReqDto dto = new PipelineStageReqDto();
                dto.setLabel(label);
                pipelineStageService.create(admin, dto);
                stages++;
            }
        }
        Tenant t = tenant(admin);
        t.setEdition(edition.key());
        tenantRepository.save(t);
        auditService.record(admin, AuditAction.EDITION_APPLIED, "tenant", t.getId(),
                "edition=" + edition.key() + ", fields+" + fields + ", stages+" + stages);
        return new Applied(edition.key(), fields, stages);
    }

    private Tenant tenant(User user) {
        if (user.getTenant() == null) {
            throw ApiException.forbidden("Editions are per tenant");
        }
        return tenantRepository.findById(user.getTenant().getId()).orElseThrow();
    }
}
