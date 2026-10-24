package com.company.crm.analytics.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/** FR-9.1: a user's dashboard configuration — which headline tiles show, and the default period. */
@Entity
@Table(name = "dashboard_preferences")
@Getter
@Setter
@NoArgsConstructor
public class DashboardPreference {

    @Id
    @Column(name = "user_id")
    private Long userId;

    /** Comma-separated widget keys, in display order. */
    @Column(nullable = false, length = 500)
    private String widgets;

    /** month | quarter | year | last30 */
    @Column(nullable = false, length = 20)
    private String period;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    @PreUpdate
    protected void touch() {
        updatedAt = LocalDateTime.now();
    }
}
