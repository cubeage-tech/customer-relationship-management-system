package com.company.crm.sales.entity;

import com.company.crm.common.enums.OpportunityStage;
import com.company.crm.customer.entity.Customer;
import com.company.crm.lead.entity.Lead;
import com.company.crm.tenant.entity.Tenant;
import com.company.crm.user.entity.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "opportunities")
@Getter
@Setter
@NoArgsConstructor
public class Opportunity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    /** Set when this opportunity was auto-created by converting a lead (BR-2). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lead_id")
    private Lead lead;

    @Column(name = "product_service")
    private String productService;

    @Column(name = "deal_value", nullable = false, precision = 14, scale = 2)
    private BigDecimal dealValue = BigDecimal.ZERO;

    @Column(name = "expected_closing_date")
    private LocalDate expectedClosingDate;

    @Column(nullable = false)
    private OpportunityStage stage = OpportunityStage.QUALIFICATION;

    /** FR-3.4: required once stage = LOST. */
    @Column(name = "loss_reason")
    private String lossReason;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id")
    private User owner;

    @Column(name = "stage_changed_at")
    private LocalDateTime stageChangedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "stage_changed_by")
    private User stageChangedBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    private User createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
