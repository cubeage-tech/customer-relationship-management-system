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

import java.math.BigDecimal;
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

    /** FR-6.5: given by the customer through their feedback link, or recorded by staff. */
    @Column(name = "feedback_score")
    private Integer feedbackScore;

    @Column(name = "feedback_comment")
    private String feedbackComment;

    /** FR-6.5: secret of the customer's feedback link (set when the ticket is first resolved). */
    @Column(name = "feedback_token", length = 64)
    private String feedbackToken;

    /** FR-6.5: when feedback was given. */
    @Column(name = "feedback_at")
    private LocalDateTime feedbackAt;

    /** FR-AI-6.1: the AI's reading of the feedback; written only by SentimentService. */
    @Column(name = "feedback_sentiment", length = 10, insertable = false, updatable = false)
    private String feedbackSentiment;

    @Column(name = "feedback_sentiment_score", precision = 3, scale = 2, insertable = false, updatable = false)
    private BigDecimal feedbackSentimentScore;

    @Column(name = "feedback_churn_risk", length = 10, insertable = false, updatable = false)
    private String feedbackChurnRisk;

    @Column(name = "feedback_sentiment_reason", length = 300, insertable = false, updatable = false)
    private String feedbackSentimentReason;

    @Column(name = "feedback_sentiment_at", insertable = false, updatable = false)
    private LocalDateTime feedbackSentimentAt;

    /** FR-6.1: the customer's equipment this ticket is about (optional). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "asset_id")
    private CustomerAsset asset;

    /** FR-6.1: "staff" (logged internally) or "customer" (public support form). */
    @Column(nullable = false, length = 20)
    private String source = SOURCE_STAFF;

    /** FR-6.1: the address the customer used on the support form. */
    @Column(name = "reporter_email", length = 150)
    private String reporterEmail;

    /** FR-6.4c: escalation sent when the ticket went at risk / breached its SLA (once each). */
    @Column(name = "sla_at_risk_notified_at")
    private LocalDateTime slaAtRiskNotifiedAt;

    @Column(name = "sla_breach_notified_at")
    private LocalDateTime slaBreachNotifiedAt;

    public static final String SOURCE_STAFF = "staff";
    public static final String SOURCE_CUSTOMER = "customer";

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
