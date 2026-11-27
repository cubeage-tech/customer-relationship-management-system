package com.company.crm.dashboard.service;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.company.crm.common.enums.AccountStatus;
import com.company.crm.common.enums.SubscriptionPlan;
import com.company.crm.dashboard.dto.response.SuperAdminDashboardResDto;
import com.company.crm.tenant.entity.Tenant;
import com.company.crm.tenant.repository.TenantRepository;
import com.company.crm.user.repository.UserRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class SuperAdminDashboardService {

    private final TenantRepository tenantRepository;
    private final UserRepository userRepository;

    public SuperAdminDashboardResDto getDashboard() {
        List<Tenant> tenants = tenantRepository.findAll();
        LocalDate today = LocalDate.now();
        LocalDate startDate = today.minusDays(29);

        Map<SubscriptionPlan, Long> plans = tenants.stream()
                .collect(Collectors.groupingBy(Tenant::getPlan, Collectors.counting()));
        Map<LocalDate, Long> createdByDate = tenants.stream()
                .filter(tenant -> tenant.getCreatedAt() != null)
                .map(tenant -> tenant.getCreatedAt().toLocalDate())
                .filter(date -> !date.isBefore(startDate) && !date.isAfter(today))
                .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));

        List<SuperAdminDashboardResDto.PlanDistributionDto> planDistribution = Arrays.stream(SubscriptionPlan.values())
                .map(plan -> new SuperAdminDashboardResDto.PlanDistributionDto(plan.getDbValue(), plans.getOrDefault(plan, 0L)))
                .toList();
        List<SuperAdminDashboardResDto.GrowthPointDto> tenantGrowth = startDate.datesUntil(today.plusDays(1))
                .map(date -> new SuperAdminDashboardResDto.GrowthPointDto(date, createdByDate.getOrDefault(date, 0L)))
                .toList();
        List<SuperAdminDashboardResDto.RecentTenantDto> recentTenants = tenants.stream()
                .sorted(Comparator.comparing(Tenant::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(4)
                .map(tenant -> new SuperAdminDashboardResDto.RecentTenantDto(
                        tenant.getId(), tenant.getCompanyName(), tenant.getPlan().getDbValue(),
                        tenant.getStatus().getDbValue(), tenant.getCreatedAt() == null ? null : tenant.getCreatedAt().toLocalDate()))
                .toList();

        return new SuperAdminDashboardResDto(
                tenants.size(),
                tenants.stream().filter(tenant -> tenant.getStatus() == AccountStatus.ACTIVE).count(),
                userRepository.count(),
                planDistribution,
                tenantGrowth,
                recentTenants);
    }
}