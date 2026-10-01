package com.company.crm.subscription;

import com.company.crm.common.exception.InvalidPlanChangeException;
import com.company.crm.common.exception.SubscriptionNotFoundException;
import com.company.crm.payment.dto.request.PaymentOrderRequest;
import com.company.crm.payment.dto.response.PaymentOrderResponse;
import com.company.crm.payment.service.PaymentService;
import com.company.crm.plan.entity.Plan;
import com.company.crm.plan.repository.PlanPriceRepository;
import com.company.crm.plan.repository.PlanRepository;
import com.company.crm.subscription.dto.request.UpgradeReqDto;
import com.company.crm.subscription.mapper.SubscriptionMapper;
import com.company.crm.subscription.repository.SubscriptionRepository;
import com.company.crm.subscription.service.PlanLimitService;
import com.company.crm.subscription.service.SubscriptionOverviewService;
import com.company.crm.subscription.service.SubscriptionService;
import com.company.crm.tenant.entity.Tenant;
import com.company.crm.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SubscriptionOverviewServiceTest {

    @Mock private SubscriptionService subscriptionService;
    @Mock private SubscriptionRepository subscriptionRepository;
    @Mock private PlanLimitService planLimitService;
    @Mock private PlanRepository planRepository;
    @Mock private PlanPriceRepository planPriceRepository;
    @Mock private PaymentService paymentService;
    @Mock private SubscriptionMapper subscriptionMapper;

    @InjectMocks private SubscriptionOverviewService service;

    private Tenant tenant;
    private User admin;
    private Plan starter;
    private Plan business;

    @BeforeEach
    void setUp() {
        tenant = new Tenant();
        tenant.setId(7L);
        admin = new User();
        admin.setTenant(tenant);

        starter = plan(1L, "starter", "Starter", 1);
        business = plan(2L, "business", "Business", 2);
    }

    @Test
    void assertIsUpgrade_allowsOnlyStrictlyHigherTiers() {
        assertThatCode(() -> SubscriptionOverviewService.assertIsUpgrade(starter, business))
                .doesNotThrowAnyException();

        assertThatThrownBy(() -> SubscriptionOverviewService.assertIsUpgrade(business, business))
                .isInstanceOf(InvalidPlanChangeException.class)
                .hasMessage("Business is not an upgrade from your current Business plan");

        assertThatThrownBy(() -> SubscriptionOverviewService.assertIsUpgrade(business, starter))
                .isInstanceOf(InvalidPlanChangeException.class)
                .extracting("status", "errorCode")
                .containsExactly(HttpStatus.BAD_REQUEST, "INVALID_PLAN_CHANGE");
    }

    @Test
    void startUpgrade_toHigherPlan_opensCheckoutForThatPlan() {
        PaymentOrderResponse order = new PaymentOrderResponse("order_1", "key", "INR", 390000L, "business", "monthly");
        when(planRepository.findById(2L)).thenReturn(Optional.of(business));
        when(subscriptionService.currentPlan(tenant)).thenReturn(starter);
        when(paymentService.createOrder(admin, new PaymentOrderRequest("business", "monthly"))).thenReturn(order);

        assertThat(service.startUpgrade(admin, new UpgradeReqDto(2L, "monthly"))).isSameAs(order);
    }

    @Test
    void startUpgrade_toSameOrLowerPlan_neverReachesPayment() {
        when(planRepository.findById(1L)).thenReturn(Optional.of(starter));
        when(subscriptionService.currentPlan(tenant)).thenReturn(business);

        assertThatThrownBy(() -> service.startUpgrade(admin, new UpgradeReqDto(1L, "monthly")))
                .isInstanceOf(InvalidPlanChangeException.class);
        verify(paymentService, never()).createOrder(any(), any());
    }

    @Test
    void startUpgrade_toInactiveOrUnknownPlan_isRejected() {
        business.setActive(false);
        when(planRepository.findById(2L)).thenReturn(Optional.of(business));
        when(planRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.startUpgrade(admin, new UpgradeReqDto(2L, "monthly")))
                .isInstanceOf(InvalidPlanChangeException.class);
        assertThatThrownBy(() -> service.startUpgrade(admin, new UpgradeReqDto(99L, "monthly")))
                .isInstanceOf(InvalidPlanChangeException.class);
        verify(paymentService, never()).createOrder(any(), any());
    }

    @Test
    void describe_withoutSubscription_is404() {
        when(subscriptionRepository.findByTenantId(7L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.describe(tenant))
                .isInstanceOf(SubscriptionNotFoundException.class)
                .extracting("status", "errorCode")
                .containsExactly(HttpStatus.NOT_FOUND, "SUBSCRIPTION_NOT_FOUND");
    }

    private Plan plan(Long id, String code, String name, int order) {
        Plan plan = new Plan();
        plan.setId(id);
        plan.setCode(code);
        plan.setName(name);
        plan.setDisplayOrder(order);
        return plan;
    }
}
