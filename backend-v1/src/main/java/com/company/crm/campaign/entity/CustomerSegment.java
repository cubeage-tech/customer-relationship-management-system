package com.company.crm.campaign.entity;

import com.company.crm.tenant.entity.Tenant;
import com.company.crm.user.entity.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * FR-7.2: a saved group of active customers, defined by criteria that are evaluated live
 * (see CustomerSegmentQuery). Every criterion is optional; set ones must all match.
 */
@Entity
@Table(name = "customer_segments")
@Getter
@Setter
@NoArgsConstructor
public class CustomerSegment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(length = 500)
    private String description;

    /** Comma-separated customer industry codes; null = any industry. */
    @Column(length = 500)
    private String industries;

    /** Customers that came from a lead with this source (website, referral…); null = any. */
    @Column(name = "lead_source", length = 30)
    private String leadSource;

    /** Bought (won deal or approved quotation) within the last N days. */
    @Column(name = "purchased_within_days")
    private Integer purchasedWithinDays;

    /** Has not bought within the last N days. */
    @Column(name = "not_purchased_within_days")
    private Integer notPurchasedWithinDays;

    /** Lifetime purchase value (won deals + approved quotations) at least this much. */
    @Column(name = "min_purchase_value", precision = 14, scale = 2)
    private BigDecimal minPurchaseValue;

    /** Engagement: any communication within the last N days. */
    @Column(name = "contacted_within_days")
    private Integer contactedWithinDays;

    /** Engagement: no communication for N days. */
    @Column(name = "not_contacted_within_days")
    private Integer notContactedWithinDays;

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
