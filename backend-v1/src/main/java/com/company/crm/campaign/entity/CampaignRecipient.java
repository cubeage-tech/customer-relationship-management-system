package com.company.crm.campaign.entity;

import com.company.crm.customer.entity.Customer;
import com.company.crm.tenant.entity.Tenant;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * FR-7.1c/7.4: one customer an email/SMS campaign is sent to — its delivery status and, via the
 * tracking token in the message, when it was opened and clicked.
 */
@Entity
@Table(name = "campaign_recipients")
@Getter
@Setter
@NoArgsConstructor
public class CampaignRecipient {

    public static final String QUEUED = "queued";
    public static final String SENT = "sent";
    public static final String FAILED = "failed";
    public static final String SKIPPED = "skipped";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "campaign_id", nullable = false)
    private Campaign campaign;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    /** The email address or phone number used. */
    @Column(length = 150)
    private String address;

    @Column(nullable = false, length = 20)
    private String status = QUEUED;

    @Column(name = "error_message", length = 500)
    private String errorMessage;

    @Column(name = "tracking_token", nullable = false, length = 64, updatable = false)
    private String trackingToken;

    @Column(name = "sent_at")
    private LocalDateTime sentAt;

    @Column(name = "opened_at")
    private LocalDateTime openedAt;

    @Column(name = "clicked_at")
    private LocalDateTime clickedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
