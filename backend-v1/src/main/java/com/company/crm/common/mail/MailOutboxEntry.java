package com.company.crm.common.mail;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/** NFR-7.4a: an email the mail server didn't accept, kept whole (MIME) and retried with backoff. */
@Entity
@Table(name = "mail_outbox")
@Getter
@Setter
@NoArgsConstructor
public class MailOutboxEntry {

    public static final String PENDING = "pending";
    public static final String SENT = "sent";
    /** Gave up after the last attempt. */
    public static final String DEAD = "dead";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** The email's Message-ID — links it to its customer-timeline entry, if it has one. */
    @Column(name = "message_id")
    private String messageId;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String recipients;

    @Column(length = 500)
    private String subject;

    @Column(nullable = false)
    private byte[] mime;

    @Column(nullable = false, length = 10)
    private String status = PENDING;

    @Column(nullable = false)
    private int attempts = 1;

    @Column(name = "next_attempt_at", nullable = false)
    private LocalDateTime nextAttemptAt;

    @Column(name = "last_error", length = 500)
    private String lastError;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "sent_at")
    private LocalDateTime sentAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
