package com.company.crm.sales.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * FR-3.2: one sales stage of a tenant's pipeline. 'won' and 'lost' are the fixed terminal stages;
 * every other code is an open stage. The code never changes.
 */
@Entity
@Table(name = "opportunity_stages")
@IdClass(PipelineStage.Key.class)
@Getter
@Setter
@NoArgsConstructor
public class PipelineStage {

    @Id
    @Column(name = "tenant_id")
    private Long tenantId;

    @Id
    @Column(nullable = false, length = 30)
    private String code;

    @Column(nullable = false, length = 60)
    private String label;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(nullable = false)
    private boolean active = true;

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

    @Getter
    @Setter
    @NoArgsConstructor
    public static class Key implements Serializable {
        private Long tenantId;
        private String code;

        public Key(Long tenantId, String code) {
            this.tenantId = tenantId;
            this.code = code;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Key k && Objects.equals(tenantId, k.tenantId) && Objects.equals(code, k.code);
        }

        @Override
        public int hashCode() {
            return Objects.hash(tenantId, code);
        }
    }
}
