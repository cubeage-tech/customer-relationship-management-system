package com.company.crm.customfield.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/** ARC-9: one record's custom field values, keyed by field key. */
@Entity
@Table(name = "custom_field_values")
@Getter
@Setter
@NoArgsConstructor
public class CustomFieldValues {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Column(name = "entity_type", nullable = false, length = 20)
    private String entityType;

    @Column(name = "record_id", nullable = false)
    private Long recordId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "field_values", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> values = new LinkedHashMap<>();

    @Column(name = "updated_by")
    private Long updatedBy;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    @PreUpdate
    protected void touch() {
        updatedAt = LocalDateTime.now();
    }
}
