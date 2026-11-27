package com.company.crm.dashboard.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class TenantAdminDashboardResDto {
    
    private Long totalUsers;
    private Long totalCustomers;
    private Long openOppotunities;
    private Long openServiceTickets;
}
