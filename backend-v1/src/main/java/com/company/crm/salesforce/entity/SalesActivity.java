package com.company.crm.salesforce.entity;

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

/** FR-8.3: a manually logged field visit (calls, meetings and quotations are counted from their own records). */
@Entity
@Table(name = "sales_activities")
@Getter
@Setter
@NoArgsConstructor
public class SalesActivity {

    public static final String VISIT = "visit";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, length = 20)
    private String type = VISIT;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id")
    private Customer customer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lead_id")
    private Lead lead;

    @Column(name = "occurred_at", nullable = false)
    private LocalDateTime occurredAt;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column(precision = 9, scale = 6)
    private BigDecimal latitude;

    @Column(precision = 9, scale = 6)
    private BigDecimal longitude;

    /** FR-M.1: why the visit was made, and what came of it. */
    @Column(length = 200)
    private String purpose;

    @Column(length = 500)
    private String outcome;

    /** FR-M.6: the mobile app's id for a visit recorded offline (unique per user), so a retried sync is a no-op. */
    @Column(name = "client_ref", length = 64)
    private String clientRef;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
