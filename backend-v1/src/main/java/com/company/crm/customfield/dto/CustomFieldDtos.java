package com.company.crm.customfield.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.Map;

/** ARC-9: request and response shapes for custom fields and industry templates. */
public final class CustomFieldDtos {

    private CustomFieldDtos() {
    }

    /** Create or edit a field. The key and type are fixed once the field exists. */
    public record DefinitionReq(
            @NotBlank @Pattern(regexp = "customer|lead|opportunity") String entityType,
            @NotBlank @Size(max = 100) String label,
            @NotBlank @Pattern(regexp = "text|number|date|select|checkbox") String fieldType,
            @Size(max = 50) List<@NotBlank @Size(max = 100) String> options,
            Boolean required,
            @Size(max = 50) String industry,
            Integer position,
            Boolean active) {
    }

    public record DefinitionRes(Long id, String entityType, String key, String label, String fieldType,
                                List<String> options, boolean required, String industry, int position, boolean active) {
    }

    public record TemplateField(String entityType, String key, String label, String fieldType, List<String> options, boolean required) {
    }

    public record Template(String industry, String label, List<TemplateField> fields) {
    }

    public record ApplyResult(String industry, int created, int skipped) {
    }

    /** A record's fields (those that apply to its industry) and their values. */
    public record ValuesRes(String entityType, Long recordId, String industry, List<DefinitionRes> fields, Map<String, Object> values) {
    }
}
