package com.company.crm.support.entity;

import com.company.crm.common.enums.TicketPriority;
import com.company.crm.tenant.entity.Tenant;
import com.company.crm.user.entity.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * FR-6.3b: auto-assignment rule for new tickets. Matches on priority and/or the customer's industry
 * (null = any); assigns {@link #assignUser}, or — when that is null — rotates round-robin over the
 * tenant's active service agents. Lowest priority number wins.
 */
@Entity
@Table(name = "ticket_assignment_rules")
@Getter
@Setter
@NoArgsConstructor
public class TicketAssignmentRule {

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

    @Column(name = "match_priority")
    private TicketPriority matchPriority;

    /** A customer industry code (FR-1.6 list), or null for any. */
    @Column(name = "match_industry", length = 30)
    private String matchIndustry;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assign_user_id")
    private User assignUser;

    /** Round-robin cursor: the agent who got the previous ticket. */
    @Column(name = "last_assigned_user_id")
    private Long lastAssignedUserId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public boolean matches(ServiceTicket ticket) {
        return active
                && (matchPriority == null || matchPriority == ticket.getPriority())
                && (matchIndustry == null || matchIndustry.equals(ticket.getCustomer().getIndustry()));
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
