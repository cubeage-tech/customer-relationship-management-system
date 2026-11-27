package com.company.crm.customfield.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.List;

/** ARC-9: an extra field a tenant adds to customers, leads or opportunities (optionally for one industry). */
@Entity
@Table(name = "custom_field_definitions")
@Getter
@Setter
@NoArgsConstructor
public class CustomFieldDefinition {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Column(name = "entity_type", nullable = false, length = 20)
    private String entityType;

    @Column(name = "field_key", nullable = false, length = 50, updatable = false)
    private String fieldKey;

    @Column(nullable = false, length = 100)
    private String label;

    @Column(name = "field_type", nullable = false, length = 20, updatable = false)
    private String fieldType;

    /** Choices of a select field. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private List<String> options;

    @Column(nullable = false)
    private boolean required;

    /** Industry code the field applies to; null = every industry. */
    @Column(length = 50)
    private String industry;

    @Column(nullable = false)
    private int position;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    /** Whether the field shows for a record of {@code recordIndustry}. */
    public boolean appliesTo(String recordIndustry) {
        return industry == null || industry.equals(recordIndustry);
    }
}
