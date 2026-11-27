package com.company.crm.lead.entity;

import com.company.crm.common.enums.IndustryType;
import com.company.crm.common.enums.LeadSource;
import com.company.crm.sales_team.entity.SalesTeam;
import com.company.crm.tenant.entity.Tenant;
import com.company.crm.user.entity.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * FR-2.3: auto-assignment rule for new leads. Matches on source and/or industry (null = any) and
 * assigns either {@link #assignUser} or {@link #assignTeam} (exactly one); for a team the owner
 * rotates round-robin among its sales executives. Lowest priority wins.
 */
@Entity
@Table(name = "lead_assignment_rules")
@Getter
@Setter
@NoArgsConstructor
public class LeadAssignmentRule {

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

    @Column(name = "match_source")
    private LeadSource matchSource;

    @Column(name = "match_industry")
    private IndustryType matchIndustry;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assign_user_id")
    private User assignUser;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assign_team_id")
    private SalesTeam assignTeam;

    /** Round-robin cursor for team rules: the member who got the previous lead. */
    @Column(name = "last_assigned_user_id")
    private Long lastAssignedUserId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public boolean matches(Lead lead) {
        return active
                && (matchSource == null || matchSource == lead.getSource())
                && (matchIndustry == null || matchIndustry == lead.getIndustry());
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
