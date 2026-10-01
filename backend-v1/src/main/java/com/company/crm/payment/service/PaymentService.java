package com.company.crm.payment.service;

import com.company.crm.common.enums.BillingCycle;
import com.company.crm.common.enums.PaymentStatus;
import com.company.crm.common.exception.ApiException;
import com.company.crm.payment.dto.request.PaymentOrderRequest;
import com.company.crm.payment.dto.request.PaymentVerificationRequest;
import com.company.crm.payment.dto.response.PaymentOrderResponse;
import com.company.crm.payment.entity.Payment;
import com.company.crm.payment.gateway.PaymentGateway;
import com.company.crm.payment.gateway.PaymentGateway.GatewayOrder;
import com.company.crm.payment.gateway.PaymentGateway.WebhookEvent;
import com.company.crm.payment.repository.PaymentRepository;
import com.company.crm.plan.entity.Plan;
import com.company.crm.plan.entity.PlanPrice;
import com.company.crm.plan.repository.PlanPriceRepository;
import com.company.crm.plan.repository.PlanRepository;
import com.company.crm.subscription.service.SubscriptionService;
import com.company.crm.tenant.entity.Tenant;
import com.company.crm.user.entity.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/**
 * Checkout flow: create an order with the provider → the browser pays → the payment is
 * confirmed (by /verify or the provider webhook, whichever arrives first) → the tenant's
 * subscription is activated. Confirmation is idempotent.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentService {

    private final PaymentGateway paymentGateway;
    private final PaymentRepository paymentRepository;
    private final PlanRepository planRepository;
    private final PlanPriceRepository planPriceRepository;
    private final SubscriptionService subscriptionService;

    @Transactional
    public PaymentOrderResponse createOrder(User currentUser, PaymentOrderRequest request) {
        Tenant tenant = requireTenant(currentUser);

        Plan plan = planRepository.findByCodeAndActiveTrue(request.plan())
                .orElseThrow(() -> ApiException.badRequest("Invalid plan: " + request.plan()));
        BillingCycle billingCycle = parseBillingCycle(request.billingCycle());

        PlanPrice price = planPriceRepository.findByPlanCode(plan.getCode())
                .orElseThrow(() -> ApiException.notFound("Plan price not found for: " + plan.getCode()));

        BigDecimal amount = chargeAmount(price, billingCycle);
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw ApiException.badRequest("Payment amount must be greater than zero");
        }

        long amountInMinorUnits = amount.multiply(BigDecimal.valueOf(100)).longValueExact();
        GatewayOrder order = paymentGateway.createOrder(
                amountInMinorUnits,
                price.getCurrency(),
                "CRM_T" + tenant.getId() + "_" + System.currentTimeMillis());

        Payment payment = new Payment();
        payment.setTenant(tenant);
        payment.setUser(currentUser);
        payment.setPlan(plan);
        payment.setProvider(paymentGateway.provider());
        payment.setBillingCycle(billingCycle);
        payment.setAmount(amount);
        payment.setCurrency(price.getCurrency());
        payment.setStatus(PaymentStatus.CREATED);
        payment.setGatewayOrderId(order.orderId());
        paymentRepository.save(payment);

        return new PaymentOrderResponse(
                order.orderId(),
                paymentGateway.checkoutKey(),
                order.currency(),
                order.amountInMinorUnits(),
                plan.getCode(),
                billingCycle.getDbValue()
        );
    }

    /**
     * Confirms a payment the browser reports as successful. Returns false (and records the
     * failure) if the signature doesn't check out.
     */
    @Transactional
    public boolean verifyPayment(User currentUser, PaymentVerificationRequest request) {
        Tenant tenant = requireTenant(currentUser);

        Payment payment = paymentRepository.findForUpdateByGatewayOrderId(request.razorpay_order_id())
                .filter(p -> p.getTenant().getId().equals(tenant.getId()))
                .orElseThrow(() -> ApiException.notFound("Payment order not found"));

        if (payment.getStatus() == PaymentStatus.PAID) {
            // Already confirmed (e.g. by the webhook) — only accept a replay of the same payment.
            return request.razorpay_payment_id().equals(payment.getGatewayPaymentId());
        }

        boolean valid = paymentGateway.isPaymentSignatureValid(
                request.razorpay_order_id(),
                request.razorpay_payment_id(),
                request.razorpay_signature());

        if (!valid) {
            payment.setStatus(PaymentStatus.FAILED);
            payment.setFailureReason("Signature verification failed");
            paymentRepository.save(payment);
            log.warn("Payment signature check failed for order {} (tenant {})", payment.getGatewayOrderId(), tenant.getId());
            return false;
        }

        payment.setGatewaySignature(request.razorpay_signature());
        markPaidAndActivate(payment, request.razorpay_payment_id());
        return true;
    }

    /** Provider webhook — the server-to-server confirmation that doesn't depend on the browser. */
    @Transactional
    public void handleWebhook(String payload, String signature) {
        WebhookEvent event = paymentGateway.parseWebhook(payload, signature).orElse(null);
        if (event == null || event.orderId() == null) {
            return;
        }

        Payment payment = paymentRepository.findForUpdateByGatewayOrderId(event.orderId()).orElse(null);
        if (payment == null) {
            log.warn("Webhook for unknown order {}", event.orderId());
            return;
        }
        if (payment.getStatus() == PaymentStatus.PAID) {
            return;
        }

        if (event.type() == WebhookEvent.Type.PAYMENT_CAPTURED) {
            markPaidAndActivate(payment, event.paymentId());
        } else {
            payment.setStatus(PaymentStatus.FAILED);
            payment.setGatewayPaymentId(event.paymentId());
            payment.setFailureReason(event.failureReason());
            paymentRepository.save(payment);
        }
    }

    /** What a billing cycle costs — see {@link PlanPrice#amountFor}. */
    static BigDecimal chargeAmount(PlanPrice price, BillingCycle billingCycle) {
        return price.amountFor(billingCycle);
    }

    private void markPaidAndActivate(Payment payment, String gatewayPaymentId) {
        payment.setStatus(PaymentStatus.PAID);
        payment.setGatewayPaymentId(gatewayPaymentId);
        payment.setFailureReason(null);
        paymentRepository.save(payment);

        subscriptionService.activate(payment.getTenant(), payment.getPlan(), payment.getBillingCycle(), payment);
        log.info("Payment {} confirmed — tenant {} now on {} ({})", payment.getGatewayOrderId(),
                payment.getTenant().getId(), payment.getPlan().getCode(), payment.getBillingCycle().getDbValue());
    }

    private BillingCycle parseBillingCycle(String raw) {
        try {
            return BillingCycle.fromDbValue(raw);
        } catch (IllegalArgumentException ex) {
            throw ApiException.badRequest("Invalid billing cycle: " + raw);
        }
    }

    private Tenant requireTenant(User currentUser) {
        if (currentUser.getTenant() == null) {
            throw ApiException.forbidden("Payments are made on behalf of a tenant");
        }
        return currentUser.getTenant();
    }
}
