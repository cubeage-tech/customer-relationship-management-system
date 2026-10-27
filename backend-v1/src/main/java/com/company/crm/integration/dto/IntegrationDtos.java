package com.company.crm.integration.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** API-G12 shapes: ERP API keys and the ERP sync payloads. */
public final class IntegrationDtos {

    private IntegrationDtos() {
    }

    // ==================== Keys (admin) ====================

    @Getter
    @Setter
    public static class KeyReq {
        @NotBlank
        @Size(max = 100)
        private String name;
    }

    public record KeyRes(Long id, String name, String keyPrefix, LocalDateTime createdAt, LocalDateTime lastUsedAt,
                         LocalDateTime revokedAt) {}

    /** Returned once, at creation: the key itself is never shown again. */
    public record CreatedKey(KeyRes key, String apiKey) {}

    // ==================== ERP pull ====================

    public record ErpCustomer(Long id, String erpCode, String companyName, String industry, String status, String email,
                              String phone, String address, LocalDateTime updatedAt) {}

    public record ErpWonDeal(Long id, Long customerId, String customerErpCode, String customerName, String productService,
                             BigDecimal dealValue, LocalDateTime wonAt, String ownerName) {}

    public record ErpQuotationLine(String productName, BigDecimal quantity, BigDecimal unitPrice, BigDecimal discountPercent) {}

    public record ErpQuotation(Long id, String quotationNumber, Long customerId, String customerErpCode, String customerName,
                               LocalDate validUntil, BigDecimal subtotal, BigDecimal grandTotal, LocalDateTime approvedAt,
                               List<ErpQuotationLine> lines) {}

    // ==================== ERP push ====================

    @Getter
    @Setter
    public static class CustomerCodeReq {
        @NotBlank
        @Size(max = 64)
        private String erpCode;
    }

    /** One price-list row from the ERP, matched to a CRM product by erpCode (created if new). */
    @Getter
    @Setter
    public static class ProductRow {
        @NotBlank
        @Size(max = 64)
        private String erpCode;
        @NotBlank
        @Size(max = 255)
        private String name;
        @Size(max = 500)
        private String description;
        @NotNull
        @DecimalMin("0")
        private BigDecimal unitPrice;
        @DecimalMin("0")
        private BigDecimal costPrice;
        private Boolean active;
    }

    @Getter
    @Setter
    public static class ProductSyncReq {
        @NotEmpty
        @Size(max = 1000)
        @Valid
        private List<ProductRow> products;
    }

    public record ProductSyncRes(int created, int updated) {}
}
