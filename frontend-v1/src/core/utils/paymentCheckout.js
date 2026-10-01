// Browser side of the payment provider's checkout (Razorpay today). The backend creates the
// order and verifies the result; this only loads the widget and reports what the user did.

const CHECKOUT_SCRIPT_URL = 'https://checkout.razorpay.com/v1/checkout.js';

const loadCheckoutScript = () =>
  new Promise((resolve) => {
    if (window.Razorpay) {
      resolve(true);
      return;
    }
    const script = document.createElement('script');
    script.src = CHECKOUT_SCRIPT_URL;
    script.onload = () => resolve(true);
    script.onerror = () => resolve(false);
    document.body.appendChild(script);
  });

export class CheckoutDismissedError extends Error {
  constructor() {
    super('Payment was cancelled');
    this.name = 'CheckoutDismissedError';
  }
}

/**
 * Opens the checkout for a backend payment order ({ orderId, keyId, amount, currency }).
 * Resolves with the provider's payment response (to send to /api/payments/verify); rejects
 * with CheckoutDismissedError if the user closes it, or an Error if the payment fails.
 */
export const openCheckout = async (order, { name = 'SmartCRM', description } = {}) => {
  if (!(await loadCheckoutScript())) {
    throw new Error('Unable to load the payment checkout. Check your connection and try again.');
  }

  return new Promise((resolve, reject) => {
    const checkout = new window.Razorpay({
      key: order.keyId,
      amount: order.amount,
      currency: order.currency,
      order_id: order.orderId,
      name,
      description,
      handler: resolve,
      modal: { ondismiss: () => reject(new CheckoutDismissedError()) },
    });
    checkout.on('payment.failed', (response) =>
      reject(new Error(response.error?.description || 'Payment failed'))
    );
    checkout.open();
  });
};
