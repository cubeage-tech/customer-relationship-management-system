import { useEffect, useState } from 'react';
import PageHeader from '../../components/common/PageHeader';
import ApiService from '../../core/services/api.service';

const planLabels = {
  starter: 'Starter',
  business: 'Business',
  enterprise: 'Enterprise',
};

const defaultPlanForm = {
  starter: { monthlyPrice: '', annualPrice: '', currency: 'INR' },
  business: { monthlyPrice: '', annualPrice: '', currency: 'INR' },
  enterprise: { monthlyPrice: '', annualPrice: '', currency: 'INR' },
};

const SubscriptionPlans = () => {
  const [planForm, setPlanForm] = useState(defaultPlanForm);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [feedback, setFeedback] = useState({ type: '', message: '' });

  const fetchPlanPrices = async () => {
    try {
      setLoading(true);
      const response = await ApiService.listPlanPrices();

      const nextState = { ...defaultPlanForm };
      (response || []).forEach((item) => {
        const key = String(item.plan || '').toLowerCase();
        if (nextState[key]) {
          nextState[key] = {
            monthlyPrice: Number(item.monthlyPrice ?? 0),
            annualPrice: Number(item.annualPrice ?? 0),
            currency: item.currency || 'INR',
          };
        }
      });

      setPlanForm(nextState);
    } catch (error) {
      console.error('Failed to fetch plan prices:', error);
      setFeedback({ type: 'error', message: 'Unable to load plan prices right now.' });
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchPlanPrices();
  }, []);

  const handleInputChange = (plan, field, value) => {
    setPlanForm((prev) => ({
      ...prev,
      [plan]: {
        ...prev[plan],
        [field]: value,
      },
    }));
  };

  const handleSave = async (plan) => {
    try {
      setSaving(true);
      setFeedback({ type: '', message: '' });

      const payload = {
        monthlyPrice: Number(planForm[plan].monthlyPrice),
        annualPrice: Number(planForm[plan].annualPrice),
        currency: planForm[plan].currency || 'INR',
      };

      await ApiService.updatePlanPrice(plan, payload);
      setFeedback({ type: 'success', message: `${planLabels[plan]} pricing updated.` });
    } catch (error) {
      console.error('Failed to update plan pricing:', error);
      setFeedback({ type: 'error', message: `Could not update ${planLabels[plan]} pricing.` });
    } finally {
      setSaving(false);
    }
  };

  return (
    <section className="space-y-6">
      <PageHeader
        title="Subscription Plans"
        subtitle="Manage the plans tenants can subscribe to — pricing, limits and feature gates."
      />

      {feedback.message && (
        <div
          className={`rounded-xl border px-4 py-3 text-sm ${
            feedback.type === 'success'
              ? 'border-emerald-200 bg-emerald-50 text-emerald-700'
              : 'border-red-200 bg-red-50 text-red-700'
          }`}
        >
          {feedback.message}
        </div>
      )}

      <div className="rounded-2xl border border-slate-200 bg-white p-4 sm:p-6">
        <div className="space-y-6">
          {Object.keys(planLabels).map((plan) => (
            <div key={plan} className="rounded-2xl border border-slate-200 p-4 sm:p-5">
              <div className="mb-4 flex items-center justify-between gap-3">
                <h3 className="text-lg font-semibold text-slate-900">{planLabels[plan]}</h3>
                <button
                  type="button"
                  onClick={() => handleSave(plan)}
                  disabled={saving || loading}
                  className="rounded-xl bg-indigo-600 px-4 py-2 text-sm font-medium text-white hover:bg-indigo-700 disabled:cursor-not-allowed disabled:opacity-60"
                >
                  {saving ? 'Saving...' : 'Save'}
                </button>
              </div>

              <div className="grid gap-4 md:grid-cols-3">
                <label className="block text-sm font-medium text-slate-700">
                  Monthly price
                  <input
                    type="number"
                    min="0"
                    step="0.01"
                    value={planForm[plan].monthlyPrice}
                    onChange={(e) => handleInputChange(plan, 'monthlyPrice', e.target.value)}
                    className="mt-2 w-full rounded-xl border border-slate-200 bg-slate-50 px-3 py-2.5 text-slate-900 outline-none focus:border-indigo-300 focus:ring-2 focus:ring-indigo-100"
                  />
                </label>

                <label className="block text-sm font-medium text-slate-700">
                  Annual price
                  <input
                    type="number"
                    min="0"
                    step="0.01"
                    value={planForm[plan].annualPrice}
                    onChange={(e) => handleInputChange(plan, 'annualPrice', e.target.value)}
                    className="mt-2 w-full rounded-xl border border-slate-200 bg-slate-50 px-3 py-2.5 text-slate-900 outline-none focus:border-indigo-300 focus:ring-2 focus:ring-indigo-100"
                  />
                </label>

                <label className="block text-sm font-medium text-slate-700">
                  Currency
                  <input
                    type="text"
                    value={planForm[plan].currency}
                    onChange={(e) => handleInputChange(plan, 'currency', e.target.value.toUpperCase())}
                    className="mt-2 w-full rounded-xl border border-slate-200 bg-slate-50 px-3 py-2.5 text-slate-900 uppercase outline-none focus:border-indigo-300 focus:ring-2 focus:ring-indigo-100"
                  />
                </label>
              </div>
            </div>
          ))}
        </div>
      </div>
    </section>
  );
};

export default SubscriptionPlans;
