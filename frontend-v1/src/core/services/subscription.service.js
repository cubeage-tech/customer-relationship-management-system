import ApiService from './api.service';

export const getCurrentSubscription = () => ApiService.getCurrentSubscription();

export const getPlanOptions = () => ApiService.getSubscriptionPlans();

/** Validates the upgrade server-side and returns a payment order for the checkout widget. */
export const startUpgrade = (planId, billingCycle) =>
  ApiService.upgradeSubscription({ planId, billingCycle });

/** Checkout for renewing the current plan (not an upgrade) — the regular payment order endpoint. */
export const startRenewal = (plan, billingCycle) =>
  ApiService.createPaymentOrder({ plan, billingCycle });

/** Confirms a completed checkout; the backend activates the plan only after this succeeds. */
export const verifyPayment = (paymentResponse) =>
  ApiService.verifyPaymentOrder({
    razorpay_order_id: paymentResponse.razorpay_order_id,
    razorpay_payment_id: paymentResponse.razorpay_payment_id,
    razorpay_signature: paymentResponse.razorpay_signature,
  });
