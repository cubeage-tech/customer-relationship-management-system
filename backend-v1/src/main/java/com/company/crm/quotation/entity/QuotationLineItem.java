package com.company.crm.quotation.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Entity
@Table(name = "quotation_line_items")
@Getter
@Setter
@NoArgsConstructor
public class QuotationLineItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "quotation_id", nullable = false)
    private Quotation quotation;

    @Column(name = "product_name", nullable = false)
    private String productName;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal quantity;

    @Column(name = "unit_price", nullable = false, precision = 14, scale = 2)
    private BigDecimal unitPrice;

    @Column(name = "discount_percent", nullable = false, precision = 5, scale = 2)
    private BigDecimal discountPercent = BigDecimal.ZERO;

    /** Line total after its own discount — not persisted, always derived. */
    @Transient
    public BigDecimal getLineTotal() {
        BigDecimal gross = quantity.multiply(unitPrice);
        BigDecimal discountFraction = discountPercent.divide(BigDecimal.valueOf(100));
        return gross.subtract(gross.multiply(discountFraction));
    }
}
