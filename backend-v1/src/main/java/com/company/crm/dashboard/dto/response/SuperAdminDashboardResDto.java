package com.company.crm.dashboard.dto.response;

import java.time.LocalDate;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class SuperAdminDashboardResDto {
    private long totalTenants;
    private long activeTenants;
    private long totalUsers;
    private List<PlanDistributionDto> planDistribution;
    private List<GrowthPointDto> tenantGrowth;
    private List<RecentTenantDto> recentTenants;

    @Getter
    @AllArgsConstructor
    public static class PlanDistributionDto {
        private String plan;
        private long count;
    }

    @Getter
    @AllArgsConstructor
    public static class GrowthPointDto {
        private LocalDate date;
        private long count;
    }

    @Getter
    @AllArgsConstructor
    public static class RecentTenantDto {
        private Long id;
        private String companyName;
        private String plan;
        private String status;
        private LocalDate createdAt;
    }
}