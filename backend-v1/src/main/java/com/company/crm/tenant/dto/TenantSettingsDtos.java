package com.company.crm.tenant.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/** NFR-7.6b: a tenant's thresholds. In a request, null = go back to the deployment default. */
public final class TenantSettingsDtos {

    private TenantSettingsDtos() {
    }

    /** SLA resolution window per ticket priority, in hours (1 hour to 90 days). */
    @Getter
    @Setter
    public static class SettingsReq {
        @Min(1) @Max(2160)
        private Integer slaHoursCritical;
        @Min(1) @Max(2160)
        private Integer slaHoursHigh;
        @Min(1) @Max(2160)
        private Integer slaHoursMedium;
        @Min(1) @Max(2160)
        private Integer slaHoursLow;
        /** Line-item discount % above which a quotation needs approval (BR-3). */
        @DecimalMin("0") @DecimalMax("100")
        private BigDecimal discountApprovalThreshold;
    }

    /** One value: what applies now, and the deployment default it falls back to. */
    public record Setting<T>(T value, T defaultValue, boolean custom) {}

    public record SettingsRes(Setting<Long> slaHoursCritical, Setting<Long> slaHoursHigh, Setting<Long> slaHoursMedium,
                              Setting<Long> slaHoursLow, Setting<BigDecimal> discountApprovalThreshold) {}
}
