package com.company.crm.customfield.service;

import com.company.crm.common.audit.AuditAction;
import com.company.crm.common.audit.AuditService;
import com.company.crm.common.enums.RoleType;
import com.company.crm.common.exception.ApiException;
import com.company.crm.customer.entity.Customer;
import com.company.crm.customer.repository.CustomerRepository;
import com.company.crm.customer.service.CustomerService;
import com.company.crm.customfield.dto.CustomFieldDtos.ApplyResult;
import com.company.crm.customfield.dto.CustomFieldDtos.DefinitionReq;
import com.company.crm.customfield.dto.CustomFieldDtos.DefinitionRes;
import com.company.crm.customfield.dto.CustomFieldDtos.Template;
import com.company.crm.customfield.dto.CustomFieldDtos.TemplateField;
import com.company.crm.customfield.dto.CustomFieldDtos.ValuesRes;
import com.company.crm.customfield.entity.CustomFieldDefinition;
import com.company.crm.customfield.entity.CustomFieldValues;
import com.company.crm.customfield.repository.CustomFieldDefinitionRepository;
import com.company.crm.customfield.repository.CustomFieldValuesRepository;
import com.company.crm.lead.service.LeadService;
import com.company.crm.sales.service.OpportunityService;
import com.company.crm.user.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import static com.company.crm.common.enums.RoleType.*;

/**
 * ARC-9: industry-specific configuration on the common data model. Admins define extra fields per
 * record type (customer, lead, opportunity), for every industry or for one industry, or apply a
 * built-in industry template. A record shows the active fields of its industry (an opportunity
 * uses its customer's industry); values are checked against each field's type and choices.
 */
@Service
@RequiredArgsConstructor
public class CustomFieldService {

    private static final Set<RoleType> EDIT_ROLES = Set.of(ADMIN, SALES_MANAGER, SALES_EXECUTIVE);
    /** Who may see each record type: the same roles as the record's own GET endpoint. */
    private static final Map<String, Set<RoleType>> VIEW_ROLES = Map.of(
            "customer", Set.of(ADMIN, SALES_MANAGER, SALES_EXECUTIVE, MARKETING_EXECUTIVE, SERVICE_AGENT, FINANCE_APPROVER, EXECUTIVE_OWNER),
            "lead", Set.of(ADMIN, SALES_MANAGER, SALES_EXECUTIVE, MARKETING_EXECUTIVE, FINANCE_APPROVER, EXECUTIVE_OWNER),
            "opportunity", Set.of(ADMIN, SALES_MANAGER, SALES_EXECUTIVE, FINANCE_APPROVER, EXECUTIVE_OWNER));
    private static final int MAX_TEXT = 1000;

    private final CustomFieldDefinitionRepository definitions;
    private final CustomFieldValuesRepository valuesRepository;
    private final CustomerService customerService;
    private final LeadService leadService;
    private final OpportunityService opportunityService;
    private final CustomerRepository customerRepository;
    private final AuditService auditService;

    /** Active fields of a record type, for every industry; forms show those matching the chosen industry. */
    @Transactional(readOnly = true)
    public List<DefinitionRes> active(User user, String entityType) {
        requireEntityType(entityType);
        return definitions.findByTenantIdAndEntityTypeOrderByPositionAscIdAsc(tenantId(user), entityType).stream()
                .filter(CustomFieldDefinition::isActive).map(CustomFieldService::toRes).toList();
    }

    @Transactional(readOnly = true)
    public List<DefinitionRes> all(User admin) {
        return definitions.findByTenantIdOrderByEntityTypeAscPositionAscIdAsc(tenantId(admin)).stream()
                .map(CustomFieldService::toRes).toList();
    }

    @Transactional
    public DefinitionRes create(User admin, DefinitionReq req) {
        Long tenantId = tenantId(admin);
        CustomFieldDefinition d = new CustomFieldDefinition();
        d.setTenantId(tenantId);
        d.setEntityType(req.entityType());
        d.setFieldKey(uniqueKey(tenantId, req.entityType(), req.label()));
        d.setFieldType(req.fieldType());
        apply(d, req);
        CustomFieldDefinition saved = definitions.save(d);
        auditService.record(admin, AuditAction.CUSTOM_FIELD_SAVED, "custom_field", saved.getId(),
                "created " + saved.getEntityType() + "." + saved.getFieldKey());
        return toRes(saved);
    }

    @Transactional
    public DefinitionRes update(User admin, Long id, DefinitionReq req) {
        CustomFieldDefinition d = definitions.findByIdAndTenantId(id, tenantId(admin))
                .orElseThrow(() -> ApiException.notFound("Custom field not found"));
        if (!d.getEntityType().equals(req.entityType()) || !d.getFieldType().equals(req.fieldType())) {
            throw ApiException.badRequest("A field's record type and field type can't be changed; add a new field instead");
        }
        apply(d, req);
        CustomFieldDefinition saved = definitions.save(d);
        auditService.record(admin, AuditAction.CUSTOM_FIELD_SAVED, "custom_field", saved.getId(),
                "updated " + saved.getEntityType() + "." + saved.getFieldKey() + (saved.isActive() ? "" : " (inactive)"));
        return toRes(saved);
    }

    public List<Template> templates() {
        return IndustryTemplates.ALL;
    }

    /** Adds the template's fields for its industry; a field whose key already exists is skipped. */
    @Transactional
    public ApplyResult applyTemplate(User admin, String industry) {
        Template template = IndustryTemplates.forIndustry(industry)
                .orElseThrow(() -> ApiException.notFound("No template for industry " + industry));
        Long tenantId = tenantId(admin);
        int created = 0;
        int position = 100;
        for (TemplateField f : template.fields()) {
            position += 10;
            if (definitions.existsByTenantIdAndEntityTypeAndFieldKey(tenantId, f.entityType(), f.key())) {
                continue;
            }
            CustomFieldDefinition d = new CustomFieldDefinition();
            d.setTenantId(tenantId);
            d.setEntityType(f.entityType());
            d.setFieldKey(f.key());
            d.setLabel(f.label());
            d.setFieldType(f.fieldType());
            d.setOptions(f.options());
            d.setRequired(f.required());
            d.setIndustry(industry);
            d.setPosition(position);
            definitions.save(d);
            created++;
        }
        int skipped = template.fields().size() - created;
        auditService.record(admin, AuditAction.CUSTOM_FIELD_TEMPLATE_APPLIED, "industry", null,
                "industry=" + industry + " created=" + created + " skipped=" + skipped);
        return new ApplyResult(industry, created, skipped);
    }

    @Transactional(readOnly = true)
    public ValuesRes values(User user, String entityType, Long recordId) {
        requireRole(user, VIEW_ROLES.get(requireEntityType(entityType)));
        String industry = recordIndustry(user, entityType, recordId);
        return valuesRes(user, entityType, recordId, industry, stored(user, entityType, recordId).getValues());
    }

    /**
     * Saves the submitted values of the fields that apply to the record. Values of other fields
     * (switched off, or for another industry) are kept as they are. An empty value clears the field.
     */
    @Transactional
    public ValuesRes save(User user, String entityType, Long recordId, Map<String, Object> submitted) {
        requireEntityType(entityType);
        requireRole(user, EDIT_ROLES);
        String industry = recordIndustry(user, entityType, recordId);
        Map<String, CustomFieldDefinition> applicable = new LinkedHashMap<>();
        definitions.findByTenantIdAndEntityTypeOrderByPositionAscIdAsc(tenantId(user), entityType).stream()
                .filter(d -> d.isActive() && d.appliesTo(industry))
                .forEach(d -> applicable.put(d.getFieldKey(), d));

        Map<String, Object> input = submitted == null ? Map.of() : submitted;
        List<String> errors = new ArrayList<>();
        input.keySet().stream().filter(k -> !applicable.containsKey(k)).forEach(k -> errors.add(k + ": not a field of this record"));

        CustomFieldValues row = stored(user, entityType, recordId);
        Map<String, Object> before = row.getValues();
        Map<String, Object> next = new LinkedHashMap<>(before);
        for (CustomFieldDefinition d : applicable.values()) {
            Object raw = input.containsKey(d.getFieldKey()) ? input.get(d.getFieldKey()) : next.get(d.getFieldKey());
            try {
                Object clean = normalise(d, raw);
                if (clean == null) {
                    if (d.isRequired()) {
                        errors.add(d.getLabel() + " is required");
                    }
                    next.remove(d.getFieldKey());
                } else {
                    next.put(d.getFieldKey(), clean);
                }
            } catch (IllegalArgumentException e) {
                errors.add(d.getLabel() + " " + e.getMessage());
            }
        }
        if (!errors.isEmpty()) {
            throw ApiException.badRequest(String.join("; ", errors));
        }
        List<String> changed = new ArrayList<>(next.keySet().stream()
                .filter(k -> !Objects.equals(String.valueOf(next.get(k)), String.valueOf(before.get(k)))).toList());
        before.keySet().stream().filter(k -> !next.containsKey(k)).forEach(changed::add);
        row.setValues(next);
        row.setUpdatedBy(user.getId());
        valuesRepository.save(row);
        if (!changed.isEmpty()) {
            // names of the changed fields only, never their values (as AC-1.2)
            auditService.record(user, AuditAction.CUSTOM_FIELD_VALUES_UPDATED, entityType, recordId, "changed=" + String.join(",", changed));
        }
        return valuesRes(user, entityType, recordId, industry, next);
    }

    // ---- helpers ----

    private ValuesRes valuesRes(User user, String entityType, Long recordId, String industry, Map<String, Object> values) {
        List<DefinitionRes> fields = definitions.findByTenantIdAndEntityTypeOrderByPositionAscIdAsc(tenantId(user), entityType).stream()
                .filter(d -> d.isActive() && d.appliesTo(industry)).map(CustomFieldService::toRes).toList();
        Map<String, Object> shown = new LinkedHashMap<>();
        fields.stream().filter(f -> values.containsKey(f.key())).forEach(f -> shown.put(f.key(), values.get(f.key())));
        return new ValuesRes(entityType, recordId, industry, fields, shown);
    }

    private CustomFieldValues stored(User user, String entityType, Long recordId) {
        Long tenantId = tenantId(user);
        return valuesRepository.findByTenantIdAndEntityTypeAndRecordId(tenantId, entityType, recordId).orElseGet(() -> {
            CustomFieldValues v = new CustomFieldValues();
            v.setTenantId(tenantId);
            v.setEntityType(entityType);
            v.setRecordId(recordId);
            return v;
        });
    }

    /** The record's industry. Loading it through its own service applies that record's access rules (404/403). */
    private String recordIndustry(User user, String entityType, Long recordId) {
        return switch (entityType) {
            case "customer" -> customerService.getCustomer(user, recordId).getIndustry();
            case "lead" -> leadService.getLead(user, recordId).getIndustry();
            default -> {
                Long customerId = opportunityService.getOpportunity(user, recordId).getCustomerId();
                yield customerId == null ? null : customerRepository.findById(customerId).map(Customer::getIndustry).orElse(null);
            }
        };
    }

    static Object normalise(CustomFieldDefinition d, Object raw) {
        if (raw == null || (raw instanceof String s && s.isBlank())) {
            return null;
        }
        String s = raw.toString().trim();
        return switch (d.getFieldType()) {
            case "number" -> {
                try {
                    yield new BigDecimal(s);
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException("must be a number");
                }
            }
            case "date" -> {
                try {
                    yield LocalDate.parse(s).toString();
                } catch (DateTimeParseException e) {
                    throw new IllegalArgumentException("must be a date (YYYY-MM-DD)");
                }
            }
            case "checkbox" -> {
                if (!s.equals("true") && !s.equals("false")) {
                    throw new IllegalArgumentException("must be true or false");
                }
                yield Boolean.valueOf(s);
            }
            case "select" -> {
                if (d.getOptions() == null || !d.getOptions().contains(s)) {
                    throw new IllegalArgumentException("must be one of " + String.join(", ", d.getOptions() == null ? List.of() : d.getOptions()));
                }
                yield s;
            }
            default -> {
                if (s.length() > MAX_TEXT) {
                    throw new IllegalArgumentException("must be at most " + MAX_TEXT + " characters");
                }
                yield s;
            }
        };
    }

    private static void apply(CustomFieldDefinition d, DefinitionReq req) {
        d.setLabel(req.label().trim());
        List<String> options = req.options() == null ? List.of()
                : req.options().stream().map(String::trim).filter(o -> !o.isEmpty()).distinct().toList();
        if ("select".equals(d.getFieldType())) {
            if (options.isEmpty()) {
                throw ApiException.badRequest("A select field needs at least one choice");
            }
            d.setOptions(options);
        } else {
            d.setOptions(null);
        }
        d.setRequired(Boolean.TRUE.equals(req.required()));
        d.setIndustry(req.industry() == null || req.industry().isBlank() ? null : req.industry().trim());
        if (req.position() != null) {
            d.setPosition(req.position());
        }
        if (req.active() != null) {
            d.setActive(req.active());
        }
    }

    private String uniqueKey(Long tenantId, String entityType, String label) {
        String base = label.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "_").replaceAll("^_+|_+$", "");
        if (base.isEmpty() || !Character.isLetter(base.charAt(0))) {
            base = "field_" + base;
        }
        base = base.length() > 44 ? base.substring(0, 44) : base;
        String key = base;
        for (int n = 2; definitions.existsByTenantIdAndEntityTypeAndFieldKey(tenantId, entityType, key); n++) {
            key = base + "_" + n;
        }
        return key;
    }

    private static String requireEntityType(String entityType) {
        if (!VIEW_ROLES.containsKey(entityType)) {
            throw ApiException.badRequest("Record type must be customer, lead or opportunity");
        }
        return entityType;
    }

    private static void requireRole(User user, Set<RoleType> roles) {
        if (!roles.contains(user.getRole().getName())) {
            throw ApiException.forbidden("Your role can't do this for this record type");
        }
    }

    private static Long tenantId(User user) {
        if (user.getTenant() == null) {
            throw ApiException.forbidden("Custom fields are per tenant");
        }
        return user.getTenant().getId();
    }

    private static DefinitionRes toRes(CustomFieldDefinition d) {
        return new DefinitionRes(d.getId(), d.getEntityType(), d.getFieldKey(), d.getLabel(), d.getFieldType(),
                d.getOptions(), d.isRequired(), d.getIndustry(), d.getPosition(), d.isActive());
    }
}
