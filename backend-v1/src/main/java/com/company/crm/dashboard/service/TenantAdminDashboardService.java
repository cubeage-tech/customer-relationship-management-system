package com.company.crm.dashboard.service;

import java.util.List;

import org.springframework.stereotype.Service;

import com.company.crm.common.enums.OpportunityStage;
import com.company.crm.common.enums.TicketStatus;
import com.company.crm.customer.repository.CustomerRepository;
import com.company.crm.dashboard.dto.response.TenantAdminDashboardResDto;
import com.company.crm.sales.repository.OpportunityRepository;
import com.company.crm.support.repository.ServiceTicketRepository;
import com.company.crm.user.repository.UserRepository;

import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;

@Service 
@RequiredArgsConstructor 
public class TenantAdminDashboardService {
    
    private final UserRepository userRepository;
    private final CustomerRepository customerRepository;
    private final OpportunityRepository opportunityRepository;
    private final ServiceTicketRepository serviceTicketRepository;

    @Transactional(readOnly = true)
    public TenantAdminDashboardResDto getTenantAdminDashboardResDto(Long tenantId) {
        Long totalUsers = userRepository.countByTenantId(tenantId);
        Long totalCustomers = customerRepository.countByTenantId(tenantId);
        // Long openOpportunities = opportunityRepository.countByTenantIdAndStageNotIn(tenantId, List.of(OpportunityStage.WON, OpportunityStage.LOST));
        Long openServiceTickets = serviceTicketRepository.countByTenantIdAndStatus(tenantId, TicketStatus.OPEN);

        long openOpportunities = 0;
        return new TenantAdminDashboardResDto(totalUsers, totalCustomers, openOpportunities, openServiceTickets);
    }
}
