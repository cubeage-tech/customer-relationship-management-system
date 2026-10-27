package com.company.crm.notification.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * FR-M.4: something a user should know about — a lead or opportunity assigned to them, a follow-up
 * falling due, or a message from their manager. Kept as an in-app inbox and pushed to their devices.
 */
@Entity
@Table(name = "notifications")
@Getter
@Setter
@NoArgsConstructor
public class Notification {

    public static final String ASSIGNMENT = "assignment";
    public static final String FOLLOW_UP = "follow_up";
    public static final String MESSAGE = "message";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false, length = 20)
    private String type;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(length = 2000)
    private String body;

    /** lead | opportunity, or null */
    @Column(name = "entity_type", length = 20)
    private String entityType;

    @Column(name = "entity_id")
    private Long entityId;

    @Column(name = "sender_id")
    private Long senderId;

    @Column(name = "read_at")
    private LocalDateTime readAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
