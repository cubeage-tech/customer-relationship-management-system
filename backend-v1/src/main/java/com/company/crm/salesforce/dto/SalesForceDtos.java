package com.company.crm.salesforce.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** Module 8 request and response shapes. */
public final class SalesForceDtos {

    private SalesForceDtos() {
    }

    // ==================== FR-8.1/8.2 targets ====================

    /** A target for one salesperson (userId) or one team (teamId). */
    @Getter
    @Setter
    public static class TargetReq {
        private Long userId;
        private Long teamId;
        /** revenue | units (units = number of won deals) */
        @NotBlank
        private String metric;
        @NotNull
        @DecimalMin(value = "0", inclusive = false)
        private BigDecimal targetValue;
        @NotNull
        private LocalDate periodStart;
        @NotNull
        private LocalDate periodEnd;
    }

    /** A target with its live attainment (AC-8.1: from won deals). */
    public record TargetRes(Long id, Long userId, String userName, Long teamId, String teamName, String metric,
                            BigDecimal targetValue, LocalDate periodStart, LocalDate periodEnd,
                            BigDecimal actual, long wonDeals, double attainmentPercent) {}

    // ==================== FR-8.3 activities ====================

    /** A field visit to a customer or lead (or neither, e.g. a prospecting round). */
    @Getter
    @Setter
    public static class VisitReq {
        private Long customerId;
        private Long leadId;
        /** Defaults to now. */
        private LocalDateTime occurredAt;
        @Size(max = 2000)
        private String notes;
        @DecimalMin("-90") @DecimalMax("90")
        private BigDecimal latitude;
        @DecimalMin("-180") @DecimalMax("180")
        private BigDecimal longitude;
        /** FR-M.1 */
        @Size(max = 200)
        private String purpose;
        @Size(max = 500)
        private String outcome;
        /** FR-M.6: the app's own id for an offline visit; sending it again returns the visit already logged. */
        @Size(max = 64)
        private String clientRef;
    }

    public record VisitRes(Long id, Long userId, String userName, Long customerId, String customerName, Long leadId, String leadName,
                           LocalDateTime occurredAt, String notes, BigDecimal latitude, BigDecimal longitude,
                           String purpose, String outcome) {}

    /** One salesperson's activity counts over a period. */
    public record ActivitySummary(Long userId, String userName, String role, long calls, long meetings, long quotationsSent,
                                  long visits, long total) {}

    // ==================== FR-8.4 attendance ====================

    /** Optional device location for a check-in or check-out. */
    @Getter
    @Setter
    public static class LocationReq {
        @DecimalMin("-90") @DecimalMax("90")
        private BigDecimal latitude;
        @DecimalMin("-180") @DecimalMax("180")
        private BigDecimal longitude;
        /** FR-M.6: when it happened, for a check-in/out recorded offline and synced later (defaults to now). */
        private LocalDateTime at;
    }

    public record AttendanceRes(Long id, Long userId, String userName, LocalDateTime checkInAt, LocalDateTime checkOutAt,
                                BigDecimal checkInLat, BigDecimal checkInLng, BigDecimal checkOutLat, BigDecimal checkOutLng,
                                Double hours) {}

    /** The caller's own attendance: whether they're checked in now, and recent days. */
    public record MyAttendance(AttendanceRes current, List<AttendanceRes> recent) {}

    // ==================== FR-8.5 / AC-8.2 commission ====================

    @Getter
    @Setter
    public static class CommissionRuleReq {
        @NotBlank
        @Size(max = 100)
        private String name;
        /** Lower runs first; defaults to 100. */
        private Integer priority;
        /** Defaults to true. */
        private Boolean active;
        @NotNull
        @DecimalMin(value = "0", inclusive = false)
        @DecimalMax("100")
        private BigDecimal ratePercent;
        /** Only for this salesperson (optional). */
        private Long userId;
        /** Only for this team's members (optional; not together with userId). */
        private Long teamId;
        @DecimalMin("0")
        private BigDecimal minDealValue;
    }

    public record CommissionRuleRes(Long id, String name, int priority, boolean active, BigDecimal ratePercent,
                                    Long userId, String userName, Long teamId, String teamName, BigDecimal minDealValue) {}

    public record CommissionEntryRes(Long id, Long opportunityId, String customerName, String productService,
                                     Long userId, String userName, String ruleName, BigDecimal dealValue, BigDecimal ratePercent,
                                     BigDecimal amount, String status, LocalDateTime wonAt, LocalDateTime approvedAt, LocalDateTime paidAt) {}
}
