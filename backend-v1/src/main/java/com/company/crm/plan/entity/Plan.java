package com.company.crm.plan.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "plans")
@Getter
@Setter
@NoArgsConstructor
public class Plan {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 30)
    private String code;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(length = 500)
    private String description;

    // Plan limits — null means unlimited.
    @Column(name = "max_users")
    private Integer maxUsers;

    @Column(name = "max_customers")
    private Integer maxCustomers;

    @Column(name = "max_campaigns")
    private Integer maxCampaigns;

    @Column(nullable = false)
    private Boolean active = true;

    /** Tier rank — a plan with a higher value is an upgrade. */
    @Column(name = "display_order", nullable = false)
    private Integer displayOrder = 0;

    /** What the plan includes, in display order. */
    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "plan_features", joinColumns = @JoinColumn(name = "plan_id"))
    @OrderColumn(name = "sort_order")
    @Column(name = "feature", nullable = false, length = 200)
    private List<String> features = new ArrayList<>();

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