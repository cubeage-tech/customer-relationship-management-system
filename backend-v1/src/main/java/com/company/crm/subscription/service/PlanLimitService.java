package com.company.crm.subscription.service;

import com.company.crm.campaign.repository.CampaignRepository;
import com.company.crm.common.enums.AccountStatus;
import com.company.crm.common.exception.PlanLimitExceededException;
import com.company.crm.customer.repository.CustomerRepository;
import com.company.crm.plan.entity.Plan;
import com.company.crm.tenant.entity.Tenant;
import com.company.crm.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Enforces the per-plan caps on users, customers and campaigns (null limit = unlimited). */
@Service
@RequiredArgsConstructor
public class PlanLimitService {

    public enum LimitedResource {
        USERS("users"),
        CUSTOMERS("customers"),
        CAMPAIGNS("campaigns");

        private final String label;

        LimitedResource(String label) {
            this.label = label;
        }
    }

    public record Usage(long users, long customers, long campaigns) {}

    private final SubscriptionService subscriptionService;
    private final UserRepository userRepository;
    private final CustomerRepository customerRepository;
    private final CampaignRepository campaignRepository;

    /** Throws if adding one more {@code resource} would exceed the tenant's plan limit. */
    @Transactional(readOnly = true)
    public void assertCanAdd(Tenant tenant, LimitedResource resource) {
        Plan plan = subscriptionService.currentPlan(tenant);
        Integer limit = limitFor(plan, resource);
        if (limit == null) {
            return;
        }

        long used = count(tenant.getId(), resource);
        if (used >= limit) {
            throw new PlanLimitExceededException(String.format(
                    "Your %s plan allows up to %d %s. Upgrade your plan to add more.",
                    plan.getName(), limit, resource.label));
        }
    }

    @Transactional(readOnly = true)
    public Usage usage(Long tenantId) {
        return new Usage(
                count(tenantId, LimitedResource.USERS),
                count(tenantId, LimitedResource.CUSTOMERS),
                count(tenantId, LimitedResource.CAMPAIGNS));
    }

    private Integer limitFor(Plan plan, LimitedResource resource) {
        return switch (resource) {
            case USERS -> plan.getMaxUsers();
            case CUSTOMERS -> plan.getMaxCustomers();
            case CAMPAIGNS -> plan.getMaxCampaigns();
        };
    }

    private long count(Long tenantId, LimitedResource resource) {
        return switch (resource) {
            // Deactivated users free up their seat.
            case USERS -> userRepository.countByTenantIdAndStatusNot(tenantId, AccountStatus.DEACTIVATED);
            case CUSTOMERS -> customerRepository.countByTenantId(tenantId);
            case CAMPAIGNS -> campaignRepository.countByTenantId(tenantId);
        };
    }
}
