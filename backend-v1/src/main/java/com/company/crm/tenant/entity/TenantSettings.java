package com.company.crm.tenant.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * NFR-7.6b: a tenant's own business thresholds. A null value means "use the deployment default"
 * from application.properties.
 */
@Entity
@Table(name = "tenant_settings")
@Getter
@Setter
@NoArgsConstructor
public class TenantSettings {

    @Id
    @Column(name = "tenant_id")
    private Long tenantId;

    @Column(name = "sla_hours_critical")
    private Integer slaHoursCritical;

    @Column(name = "sla_hours_high")
    private Integer slaHoursHigh;

    @Column(name = "sla_hours_medium")
    private Integer slaHoursMedium;

    @Column(name = "sla_hours_low")
    private Integer slaHoursLow;

    @Column(name = "discount_approval_threshold", precision = 5, scale = 2)
    private BigDecimal discountApprovalThreshold;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    @PreUpdate
    protected void touch() {
        updatedAt = LocalDateTime.now();
    }
}
