package com.company.crm.quotation.entity;

import com.company.crm.common.enums.DiscountApprovalStatus;
import com.company.crm.common.enums.QuotationStatus;
import com.company.crm.customer.entity.Customer;
import com.company.crm.sales.entity.Opportunity;
import com.company.crm.tenant.entity.Tenant;
import com.company.crm.user.entity.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "quotations")
@Getter
@Setter
@NoArgsConstructor
public class Quotation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "opportunity_id")
    private Opportunity opportunity;

    @Column(name = "quotation_number", nullable = false)
    private String quotationNumber;

    /** FR-4.5: the customer's response to the quotation. */
    @Column(nullable = false)
    private QuotationStatus status = QuotationStatus.DRAFT;

    /** BR-3/FR-4.3: derived from the line items' discount %, checked before sending. */
    @Column(name = "discount_approval_status", nullable = false)
    private DiscountApprovalStatus discountApprovalStatus = DiscountApprovalStatus.NOT_REQUIRED;

    @Column(name = "discount_review_note")
    private String discountReviewNote;

    @Column(name = "valid_until")
    private LocalDate validUntil;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id")
    private User owner;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    private User createdBy;

    @OneToMany(mappedBy = "quotation", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<QuotationLineItem> lineItems = new ArrayList<>();

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

    @Transient
    public BigDecimal getSubtotal() {
        return lineItems.stream()
                .map(item -> item.getQuantity().multiply(item.getUnitPrice()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    @Transient
    public BigDecimal getGrandTotal() {
        return lineItems.stream()
                .map(QuotationLineItem::getLineTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /** Highest per-line discount — what BR-3's approval threshold is checked against. */
    @Transient
    public BigDecimal getMaxDiscountPercent() {
        return lineItems.stream()
                .map(QuotationLineItem::getDiscountPercent)
                .max(BigDecimal::compareTo)
                .orElse(BigDecimal.ZERO);
    }
}
