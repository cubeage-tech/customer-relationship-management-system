package com.company.crm.salesforce.entity;

import com.company.crm.sales_team.entity.SalesTeam;
import com.company.crm.tenant.entity.Tenant;
import com.company.crm.user.entity.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * FR-8.5: commission = {@link #ratePercent} of the won deal's value, for one salesperson, one team's
 * members, or everyone (both null), optionally only from {@link #minDealValue}. Lowest priority wins.
 */
@Entity
@Table(name = "commission_rules")
@Getter
@Setter
@NoArgsConstructor
public class CommissionRule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false)
    private int priority = 100;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "rate_percent", nullable = false, precision = 5, scale = 2)
    private BigDecimal ratePercent;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "team_id")
    private SalesTeam team;

    @Column(name = "min_deal_value", precision = 14, scale = 2)
    private BigDecimal minDealValue;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    /** Applies to a deal of {@code dealValue} owned by {@code owner}. */
    public boolean matches(User owner, BigDecimal dealValue) {
        return active
                && (user == null || user.getId().equals(owner.getId()))
                && (team == null || team.getId().equals(owner.getTeamId()))
                && (minDealValue == null || dealValue.compareTo(minDealValue) >= 0);
    }

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
