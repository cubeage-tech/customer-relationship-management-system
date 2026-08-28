package com.company.crm.support.entity;

import com.company.crm.common.enums.TicketPriority;
import com.company.crm.common.enums.TicketStatus;
import com.company.crm.customer.entity.Customer;
import com.company.crm.tenant.entity.Tenant;
import com.company.crm.user.entity.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "service_tickets")
@Getter
@Setter
@NoArgsConstructor
public class ServiceTicket {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    @Column(nullable = false)
    private String subject;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false)
    private TicketPriority priority;

    @Column(nullable = false)
    private TicketStatus status = TicketStatus.OPEN;

    /** FR-6.4: computed at creation from the priority's configured SLA window. */
    @Column(name = "sla_due_at", nullable = false)
    private LocalDateTime slaDueAt;

    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;

    /** FR-6.5: recorded on the customer's behalf by internal staff — no self-service portal yet. */
    @Column(name = "feedback_score")
    private Integer feedbackScore;

    @Column(name = "feedback_comment")
    private String feedbackComment;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_technician_id")
    private User assignedTechnician;

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
