package com.company.crm.plan.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.company.crm.common.enums.BillingCycle;
import com.company.crm.user.entity.User;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "plan_prices")
@Getter
@Setter
@NoArgsConstructor
public class PlanPrice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "plan_id", nullable = false)
    private Plan plan;

    @Column(name = "monthly_price", nullable = false, precision = 10, scale = 2)
    private BigDecimal monthlyPrice;

    @Column(name = "annual_price", nullable = false, precision = 10, scale = 2)
    private BigDecimal annualPrice;

    @Column(nullable = false, length = 3)
    private String currency = "INR";

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "updated_by")
    private User updatedBy;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    @PreUpdate
    protected void touch() {
        updatedAt = LocalDateTime.now();
    }

    /**
     * What one billing cycle costs. Prices are stored as a per-month rate (the annual rate is
     * the discounted per-month price when billed yearly), so annual = 12 × annual rate.
     * Billing is per tenant — change this one method if you move to per-seat pricing.
     */
    public BigDecimal amountFor(BillingCycle billingCycle) {
        return billingCycle == BillingCycle.ANNUAL
                ? annualPrice.multiply(BigDecimal.valueOf(12))
                : monthlyPrice;
    }
}
