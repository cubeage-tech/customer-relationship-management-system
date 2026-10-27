package com.company.crm.salesforce.entity;

import com.company.crm.sales.entity.Opportunity;
import com.company.crm.tenant.entity.Tenant;
import com.company.crm.user.entity.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * AC-8.2: the commission recorded when a deal was won — pending → approved → paid, or void when the
 * deal is re-opened. One per opportunity; the rule's name and rate are kept as they were.
 */
@Entity
@Table(name = "commission_entries")
@Getter
@Setter
@NoArgsConstructor
public class CommissionEntry {

    public static final String PENDING = "pending";
    public static final String APPROVED = "approved";
    public static final String PAID = "paid";
    public static final String VOID = "void";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "opportunity_id", nullable = false, unique = true)
    private Opportunity opportunity;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "rule_id")
    private CommissionRule rule;

    @Column(name = "rule_name", nullable = false, length = 100)
    private String ruleName;

    @Column(name = "deal_value", nullable = false, precision = 14, scale = 2)
    private BigDecimal dealValue;

    @Column(name = "rate_percent", nullable = false, precision = 5, scale = 2)
    private BigDecimal ratePercent;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 10)
    private String status = PENDING;

    @Column(name = "won_at", nullable = false)
    private LocalDateTime wonAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "approved_by")
    private User approvedBy;

    @Column(name = "approved_at")
    private LocalDateTime approvedAt;

    @Column(name = "paid_at")
    private LocalDateTime paidAt;

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
