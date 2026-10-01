import { useEffect, useState } from "react";
import ApiService from "../../core/services/api.service";

const PLAN_NAMES = {
  starter: "Starter",
  business: "Business",
  enterprise: "Enterprise"
};

const UpgradePopup = ({
  isOpen,
  onClose,
  currentPlan = "starter",
  onPaymentSuccess
}) => {
  const [selectedPlan, setSelectedPlan] = useState("business");
  const [billingCycle, setBillingCycle] = useState("monthly");
  const [loading, setLoading] = useState(false);
  const [plans, setPlans] = useState({});

  // Prices come from the backend (super admin can change them) — the amount actually
  // charged is always computed server-side; this is display only.
  useEffect(() => {
    if (!isOpen) return;

    ApiService.listPlanPrices()
      .then((prices) => {
        const byCode = {};
        (prices || []).forEach((price) => {
          byCode[price.plan] = {
            name: PLAN_NAMES[price.plan] || price.plan,
            monthly: Number(price.monthlyPrice),
            // annualPrice is the per-month rate when billed yearly; a year costs 12×.
            annual: Number(price.annualPrice) * 12
          };
        });
        setPlans(byCode);
      })
      .catch((error) => console.error("Failed to load plan prices:", error));
  }, [isOpen]);

  if (!isOpen) {
    return null;
  }

  const loadPaymentScript = () => {
    return new Promise((resolve) => {
      if (window.Razorpay) {
        resolve(true);
        return;
      }

      const script = document.createElement("script");

      script.src =
        "https://checkout.razorpay.com/v1/checkout.js";

      script.onload = () => resolve(true);
      script.onerror = () => resolve(false);

      document.body.appendChild(script);
    });
  };

  const handlePayment = async () => {
    try {
      setLoading(true);

      // Load payment checkout
      const loaded = await loadPaymentScript();

      if (!loaded) {
        throw new Error(
          "Unable to load payment checkout"
        );
      }

      // Create payment order
      const order =
        await ApiService.createPaymentOrder({
          plan: selectedPlan,
          billingCycle
        });

      if (!order?.orderId) {
        throw new Error(
          "Payment order was not created"
        );
      }

      const options = {
        key: order.keyId,
        amount: order.amount,
        currency: order.currency,
        name: "CRM",
        description:
          `${PLAN_NAMES[selectedPlan]} Plan - ${billingCycle}`,
        order_id: order.orderId,

        handler: async (paymentResponse) => {
          try {
            setLoading(true);

            const verification =
              await ApiService.verifyPaymentOrder({
                razorpay_order_id:
                  paymentResponse.razorpay_order_id,

                razorpay_payment_id:
                  paymentResponse.razorpay_payment_id,

                razorpay_signature:
                  paymentResponse.razorpay_signature
              });

            // apipost() already unwraps the envelope — the backend returns data: true.
            if (verification === true) {

              alert("Payment successful!");

              onPaymentSuccess?.();

              onClose();

            } else {

              alert(
                "Payment verification failed"
              );
            }

          } catch (error) {

            console.error(
              "Payment verification failed:",
              error
            );

            alert(
              "Payment completed but verification failed"
            );

          } finally {

            setLoading(false);
          }
        },

        modal: {
          ondismiss: () => {
            setLoading(false);
          }
        },

        theme: {
          color: "#2563eb"
        }
      };

      const razorpay =
        new window.Razorpay(options);

      razorpay.on(
        "payment.failed",
        (response) => {

          console.error(
            "Payment failed:",
            response.error
          );

          alert(
            response.error?.description ||
            "Payment failed"
          );

          setLoading(false);
        }
      );

      razorpay.open();

    } catch (error) {

      console.error(
        "Payment error:",
        error
      );

      alert(
        error?.response?.data?.message ||
        error.message ||
        "Unable to start payment"
      );

      setLoading(false);
    }
  };

  return (
    <div className="fixed inset-0 z-[9999] flex items-center justify-center bg-black/60 p-4">

      <div className="relative w-full max-w-4xl rounded-2xl bg-white p-6 shadow-2xl sm:p-8">

        {/* Close */}
        <button
          type="button"
          onClick={onClose}
          disabled={loading}
          className="absolute right-4 top-3 text-3xl font-light text-gray-500 transition hover:text-gray-900 disabled:cursor-not-allowed disabled:opacity-50"
        >
          ×
        </button>

        {/* Header */}
        <div className="mb-6 text-center">

          <h2 className="text-2xl font-bold text-gray-900 sm:text-3xl">
            Your Plan Has Expired
          </h2>

          <p className="mt-2 text-sm text-gray-500 sm:text-base">
            Choose a plan to continue using the CRM.
          </p>

        </div>

        {/* Billing Cycle */}
        <div className="mb-8 flex justify-center">

          <div className="inline-flex rounded-xl bg-gray-100 p-1">

            <button
              type="button"
              onClick={() =>
                setBillingCycle("monthly")
              }
              className={`rounded-lg px-5 py-2 text-sm font-medium transition ${
                billingCycle === "monthly"
                  ? "bg-white text-blue-600 shadow"
                  : "text-gray-500 hover:text-gray-900"
              }`}
            >
              Monthly
            </button>

            <button
              type="button"
              onClick={() =>
                setBillingCycle("annual")
              }
              className={`rounded-lg px-5 py-2 text-sm font-medium transition ${
                billingCycle === "annual"
                  ? "bg-white text-blue-600 shadow"
                  : "text-gray-500 hover:text-gray-900"
              }`}
            >
              Annual
            </button>

          </div>

        </div>

        {/* Plans */}
        <div className="grid gap-4 md:grid-cols-3">

          {Object.entries(plans).map(
            ([key, plan]) => {

              const isSelected =
                selectedPlan === key;

              const isCurrent =
                currentPlan?.toLowerCase() === key;

              return (
                <button
                  type="button"
                  key={key}
                  disabled={loading}
                  onClick={() =>
                    setSelectedPlan(key)
                  }
                  className={`relative rounded-xl border-2 p-6 text-left transition ${
                    isSelected
                      ? "border-blue-600 bg-blue-50 shadow-md"
                      : "border-gray-200 bg-white hover:border-blue-300"
                  }`}
                >

                  {isCurrent && (
                    <span className="absolute right-3 top-3 rounded-full bg-gray-200 px-2 py-1 text-xs font-medium text-gray-600">
                      Current Plan
                    </span>
                  )}

                  <h3 className="text-xl font-bold text-gray-900">
                    {plan.name}
                  </h3>

                  <div className="mt-4">

                    <span className="text-3xl font-bold text-gray-900">
                      ₹
                      {(billingCycle === "monthly"
                        ? plan.monthly
                        : plan.annual
                      ).toLocaleString("en-IN")}
                    </span>

                    <span className="ml-1 text-sm text-gray-500">
                      /
                      {billingCycle === "monthly"
                        ? "month"
                        : "year"}
                    </span>

                  </div>

                  <div className="mt-5 flex items-center gap-2 text-sm">

                    <span
                      className={`h-4 w-4 rounded-full border-2 ${
                        isSelected
                          ? "border-blue-600 bg-blue-600"
                          : "border-gray-300"
                      }`}
                    />

                    <span className="text-gray-600">
                      {isSelected
                        ? "Selected"
                        : "Select plan"}
                    </span>

                  </div>

                </button>
              );
            }
          )}

        </div>

        {/* Upgrade Button */}
        <button
          type="button"
          onClick={handlePayment}
          disabled={loading}
          className="mt-8 w-full rounded-xl bg-blue-600 px-6 py-3.5 font-semibold text-white transition hover:bg-blue-700 disabled:cursor-not-allowed disabled:opacity-60"
        >
          {loading
            ? "Processing..."
            : `Continue with ${
                PLAN_NAMES[selectedPlan]
              }`}
        </button>

      </div>

    </div>
  );
};

export default UpgradePopup;