package com.company.crm.subscription;

import com.company.crm.campaign.repository.CampaignRepository;
import com.company.crm.common.enums.AccountStatus;
import com.company.crm.common.exception.PlanLimitExceededException;
import com.company.crm.customer.repository.CustomerRepository;
import com.company.crm.plan.entity.Plan;
import com.company.crm.subscription.service.PlanLimitService;
import com.company.crm.subscription.service.PlanLimitService.LimitedResource;
import com.company.crm.subscription.service.PlanLimitService.Usage;
import com.company.crm.subscription.service.SubscriptionService;
import com.company.crm.tenant.entity.Tenant;
import com.company.crm.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PlanLimitServiceTest {

    @Mock private SubscriptionService subscriptionService;
    @Mock private UserRepository userRepository;
    @Mock private CustomerRepository customerRepository;
    @Mock private CampaignRepository campaignRepository;

    @InjectMocks private PlanLimitService planLimitService;

    private Tenant tenant;
    private Plan starter;

    @BeforeEach
    void setUp() {
        tenant = new Tenant();
        tenant.setId(7L);

        starter = new Plan();
        starter.setCode("starter");
        starter.setName("Starter");
        starter.setMaxUsers(5);
        starter.setMaxCustomers(500);
        starter.setMaxCampaigns(5);
    }

    @Test
    void customers_belowLimit_isAllowed() {
        onStarterPlan();
        when(customerRepository.countByTenantId(7L)).thenReturn(499L);

        assertThatCode(() -> planLimitService.assertCanAdd(tenant, LimitedResource.CUSTOMERS))
                .doesNotThrowAnyException();
    }

    @Test
    void customers_atLimit_isRejected() {
        onStarterPlan();
        when(customerRepository.countByTenantId(7L)).thenReturn(500L);

        assertThatThrownBy(() -> planLimitService.assertCanAdd(tenant, LimitedResource.CUSTOMERS))
                .isInstanceOf(PlanLimitExceededException.class)
                .hasMessage("Your Starter plan allows up to 500 customers. Upgrade your plan to add more.")
                .extracting("status", "errorCode")
                .containsExactly(HttpStatus.FORBIDDEN, "PLAN_LIMIT_EXCEEDED");
    }

    @Test
    void campaigns_atLimit_isRejected() {
        onStarterPlan();
        when(campaignRepository.countByTenantId(7L)).thenReturn(5L);

        assertThatThrownBy(() -> planLimitService.assertCanAdd(tenant, LimitedResource.CAMPAIGNS))
                .isInstanceOf(PlanLimitExceededException.class)
                .hasMessageContaining("5 campaigns");
    }

    @Test
    void users_countOnlyNonDeactivatedSeats() {
        onStarterPlan();
        when(userRepository.countByTenantIdAndStatusNot(7L, AccountStatus.DEACTIVATED)).thenReturn(4L);

        assertThatCode(() -> planLimitService.assertCanAdd(tenant, LimitedResource.USERS))
                .doesNotThrowAnyException();
    }

    @Test
    void users_atLimit_isRejected() {
        onStarterPlan();
        when(userRepository.countByTenantIdAndStatusNot(7L, AccountStatus.DEACTIVATED)).thenReturn(5L);

        assertThatThrownBy(() -> planLimitService.assertCanAdd(tenant, LimitedResource.USERS))
                .isInstanceOf(PlanLimitExceededException.class);
    }

    @Test
    void unlimitedPlan_neverCounts() {
        onStarterPlan();
        starter.setMaxCustomers(null);

        assertThatCode(() -> planLimitService.assertCanAdd(tenant, LimitedResource.CUSTOMERS))
                .doesNotThrowAnyException();
        verifyNoInteractions(customerRepository);
    }

    @Test
    void usage_countsEachResourceForTheTenant() {
        when(userRepository.countByTenantIdAndStatusNot(7L, AccountStatus.DEACTIVATED)).thenReturn(2L);
        when(customerRepository.countByTenantId(7L)).thenReturn(10L);
        when(campaignRepository.countByTenantId(7L)).thenReturn(1L);

        assertThat(planLimitService.usage(7L)).isEqualTo(new Usage(2, 10, 1));
    }

    private void onStarterPlan() {
        when(subscriptionService.currentPlan(tenant)).thenReturn(starter);
    }
}
