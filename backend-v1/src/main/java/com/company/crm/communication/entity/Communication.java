package com.company.crm.communication.entity;

import com.company.crm.common.crypto.BlindIndexed;
import com.company.crm.common.crypto.DataEncryption;
import com.company.crm.common.crypto.EncryptedString;
import com.company.crm.common.enums.CallOutcome;
import com.company.crm.common.enums.CommunicationChannel;
import com.company.crm.common.enums.CommunicationDirection;
import com.company.crm.common.enums.CommunicationStatus;
import com.company.crm.customer.entity.Customer;
import com.company.crm.lead.entity.Lead;
import com.company.crm.tenant.entity.Tenant;
import com.company.crm.user.entity.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * DB-E09 Communication Log: one email, WhatsApp message, SMS or call, logged against a customer
 * and/or a lead (FR-5.1–5.4). The customer's rows, newest first, are its timeline (FR-5.6).
 */
@Entity
@EntityListeners(BlindIndexed.Listener.class)
@Table(name = "communication_logs")
@Getter
@Setter
@NoArgsConstructor
public class Communication implements BlindIndexed {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id")
    private Customer customer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lead_id")
    private Lead lead;

    @Column(nullable = false)
    private CommunicationChannel channel;

    @Column(nullable = false)
    private CommunicationDirection direction;

    @Column(nullable = false)
    private CommunicationStatus status;

    private String subject;

    @Column(columnDefinition = "TEXT")
    private String body;

    /** Email address or phone number on the customer's side (NFR-7.2b: encrypted). */
    @Convert(converter = EncryptedString.class)
    private String counterpart;

    /** NFR-7.2b: blind index of {@link #counterpart}, for "latest message to this address/number". */
    @Column(name = "counterpart_hash", length = 64)
    private String counterpartHash;

    @Override
    public void refreshBlindIndexes(DataEncryption encryption) {
        counterpartHash = encryption.counterpartIndex(counterpart);
    }

    /** Email Message-ID or provider SID — matches replies and delivery callbacks to this row. */
    @Column(name = "provider_message_id")
    private String providerMessageId;

    @Column(name = "error_message")
    private String errorMessage;

    /** NFR-7.4a: automatic resend attempts after a temporary SMS/WhatsApp failure, and when the next is due. */
    @Column(name = "retry_count", nullable = false)
    private int retryCount;

    @Column(name = "next_retry_at")
    private LocalDateTime nextRetryAt;

    @Column(name = "call_outcome")
    private CallOutcome callOutcome;

    @Column(name = "call_duration_seconds")
    private Integer callDurationSeconds;

    /** FR-AI-6.1: written only by SentimentService, so a save here never clears them. */
    @Column(name = "sentiment", length = 10, insertable = false, updatable = false)
    private String sentiment;

    @Column(name = "sentiment_score", precision = 3, scale = 2, insertable = false, updatable = false)
    private BigDecimal sentimentScore;

    @Column(name = "churn_risk", length = 10, insertable = false, updatable = false)
    private String churnRisk;

    @Column(name = "sentiment_reason", length = 300, insertable = false, updatable = false)
    private String sentimentReason;

    @Column(name = "sentiment_at", insertable = false, updatable = false)
    private LocalDateTime sentimentAt;

    @Column(name = "occurred_at", nullable = false)
    private LocalDateTime occurredAt;

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
        if (occurredAt == null) {
            occurredAt = now;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
