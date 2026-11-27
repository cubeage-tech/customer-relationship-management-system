package com.company.crm.campaign.entity;

import com.company.crm.common.enums.CampaignChannel;
import com.company.crm.common.enums.CampaignStatus;
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
@Table(name = "campaigns")
@Getter
@Setter
@NoArgsConstructor
public class Campaign {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    @Column(nullable = false)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false)
    private CampaignChannel channel;

    @Column(nullable = false)
    private CampaignStatus status = CampaignStatus.DRAFT;

    @Column(name = "start_date")
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    @Column(nullable = false)
    private BigDecimal budget = BigDecimal.ZERO;

    @Column(name = "actual_cost", nullable = false)
    private BigDecimal actualCost = BigDecimal.ZERO;

    /** FR-7.1b: when a SCHEDULED campaign launches itself. */
    @Column(name = "scheduled_at")
    private LocalDateTime scheduledAt;

    /** FR-7.1c: when it was launched (and, for email/SMS, sent). */
    @Column(name = "launched_at")
    private LocalDateTime launchedAt;

    /** FR-7.2/7.1c: who an email/SMS campaign is sent to. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "segment_id")
    private CustomerSegment segment;

    /** FR-7.1c: email subject (email campaigns). */
    @Column(name = "message_subject", length = 200)
    private String messageSubject;

    /** FR-7.1c: the message (plain text; links in it are tracked for FR-7.4). */
    @Column(name = "message_body", columnDefinition = "TEXT")
    private String messageBody;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id")
    private User owner;

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
