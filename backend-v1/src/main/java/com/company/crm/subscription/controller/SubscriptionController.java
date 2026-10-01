package com.company.crm.subscription.controller;

import com.company.crm.common.exception.ApiException;
import com.company.crm.common.response.Response;
import com.company.crm.payment.dto.response.PaymentOrderResponse;
import com.company.crm.subscription.dto.request.UpgradeReqDto;
import com.company.crm.subscription.dto.response.PlanOptionResDto;
import com.company.crm.subscription.dto.response.SubscriptionResDto;
import com.company.crm.subscription.service.SubscriptionOverviewService;
import com.company.crm.subscription.service.SubscriptionService;
import com.company.crm.tenant.entity.Tenant;
import com.company.crm.user.entity.User;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** The tenant owner's billing: current plan, plan comparison, upgrade and cancel. Tenant comes from the JWT. */
@RestController
@RequestMapping("/api/subscriptions")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
@Tag(name = "Subscription", description = "Tenant admin's plan, usage, upgrade and cancellation")
public class SubscriptionController {

    private final SubscriptionService subscriptionService;
    private final SubscriptionOverviewService subscriptionOverviewService;

    @GetMapping("/current")
    @Operation(summary = "Current plan, time remaining, usage against limits and included features",
            description = "Still returned when the subscription has expired. 404 SUBSCRIPTION_NOT_FOUND if the tenant has none.")
    public Response<SubscriptionResDto> current(@AuthenticationPrincipal User currentUser) {
        return Response.ok(subscriptionOverviewService.describe(requireTenant(currentUser)));
    }

    @GetMapping("/plans")
    @Operation(summary = "Active plans to compare, flagged as the current plan or an upgrade")
    public Response<List<PlanOptionResDto>> plans(@AuthenticationPrincipal User currentUser) {
        return Response.ok(subscriptionOverviewService.planOptions(requireTenant(currentUser)));
    }

    @PostMapping("/upgrade")
    @Operation(summary = "Start an upgrade to a higher plan",
            description = "Returns a payment order for the checkout widget. The plan changes only after the payment "
                    + "is verified (POST /api/payments/verify or the provider webhook). 400 INVALID_PLAN_CHANGE "
                    + "if the target is not a higher plan.")
    public Response<PaymentOrderResponse> upgrade(
            @AuthenticationPrincipal User currentUser,
            @Valid @RequestBody UpgradeReqDto request) {
        requireTenant(currentUser);
        return Response.ok("Upgrade checkout created", subscriptionOverviewService.startUpgrade(currentUser, request));
    }

    /** Cancel at period end — access continues until expires_at. */
    @PostMapping("/cancel")
    @Operation(summary = "Cancel at the end of the current paid period")
    public Response<SubscriptionResDto> cancel(@AuthenticationPrincipal User currentUser) {
        Tenant tenant = requireTenant(currentUser);
        subscriptionService.cancel(tenant);
        return Response.ok("Subscription cancelled. You keep access until the end of the current period.",
                subscriptionOverviewService.describe(tenant));
    }

    private Tenant requireTenant(User currentUser) {
        if (currentUser.getTenant() == null) {
            throw ApiException.forbidden("Subscriptions are scoped to a tenant");
        }
        return currentUser.getTenant();
    }
}
