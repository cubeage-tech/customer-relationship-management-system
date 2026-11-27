package com.company.crm.dashboard.dto.response;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class SalesManagerDashboardResDto {

    private DashboardSection<LeadDto> teamLeads;
    private DashboardSection<OpportunityDto> teamPipeline;
    private DashboardSection<QuotationDto> quotationsToApprove;
    private DashboardSection<OpportunityDto> closedThisMonth;

    @Getter
    @AllArgsConstructor
    public static class DashboardSection<T> {
        private long count;
        private List<T> items;
    }

    @Getter
    @AllArgsConstructor
    public static class LeadDto {
        private Long id;
        private String leadName;
        private String companyName;
        private String contactEmail;
        private String contactPhone;
        private String industry;
        private String source;
        private String stage;
        private java.time.LocalDateTime followUpDate;
        private String notes;
        private Long ownerId;
        private String ownerName;
        private Long campaignId;
        private Long convertedCustomerId;
        private Long createdById;
        private java.time.LocalDateTime createdAt;
        private java.time.LocalDateTime updatedAt;
    }

    @Getter
    @AllArgsConstructor
    public static class OpportunityDto {
        private Long id;
        private Long customerId;
        private Long leadId;
        private String productService;
        private java.math.BigDecimal dealValue;
        private java.time.LocalDate expectedClosingDate;
        private String stage;
        private String lossReason;
        private Long ownerId;
        private String ownerName;
        private java.time.LocalDateTime stageChangedAt;
        private Long stageChangedById;
        private Long createdById;
        private java.time.LocalDateTime createdAt;
        private java.time.LocalDateTime updatedAt;
    }

    @Getter
    @AllArgsConstructor
    public static class QuotationDto {
        private Long id;
        private Long customerId;
        private Long opportunityId;
        private String quotationNumber;
        private String status;
        private String discountApprovalStatus;
        private String discountReviewNote;
        private java.time.LocalDate validUntil;
        private String notes;
        private Long ownerId;
        private String ownerName;
        private Long createdById;
        private java.time.LocalDateTime createdAt;
        private java.time.LocalDateTime updatedAt;
        private java.math.BigDecimal subtotal;
        private java.math.BigDecimal grandTotal;
        private java.math.BigDecimal maxDiscountPercent;
    }
}