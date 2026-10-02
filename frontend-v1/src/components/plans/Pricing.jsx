import { useState, useEffect } from 'react';
import { Check, Sparkles, Loader2 } from 'lucide-react';
import ApiService from '../../core/services/api.service';

const planConfigs = {
  starter: {
    title: 'Starter',
    description: 'For small teams getting organised',
    features: ['Up to 3 seats', '2,000 contacts', 'Pipeline & tasks', 'Email sync', '500 AI credits'],
    highlight: false,
  },
  business: {
    title: 'Business',
    description: 'Most popular for scaling sales teams',
    features: ['Up to 25 seats', '50,000 contacts', 'AI lead scoring', 'Campaign analytics', '5,000 AI credits'],
    highlight: true,
  },
  enterprise: {
    title: 'Enterprise',
    description: 'Security, residency and dedicated support',
    features: ['SSO & SCIM', 'Data residency', 'Audit logs', 'Dedicated CSM', 'Custom AI credits'],
    highlight: false,
  },
};

const defaultPlans = {
  starter: { monthly: 1500, annual: 1200, currency: 'INR' },
  business: { monthly: 3900, annual: 3100, currency: 'INR' },
  enterprise: { monthly: 7100, annual: 6300, currency: 'INR' },
};

const normalizePlanPrices = (planItems = []) => {
  const normalized = {};

  planItems.forEach((item) => {
    const planKey = String(item.plan || '').toLowerCase();
    normalized[planKey] = {
      monthly: Number(item.monthlyPrice ?? 0),
      annual: Number(item.annualPrice ?? 0),
      currency: item.currency || 'INR',
    };
  });

  return normalized;
};

const Pricing = () => {
  const [isAnnual, setIsAnnual] = useState(false);
  const [prices, setPrices] = useState(defaultPlans);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    const fetchPrices = async () => {
      try {
        const response = await ApiService.listPlanPrices();
        const normalized = normalizePlanPrices(response || []);

        if (Object.keys(normalized).length > 0) {
          setPrices({ ...defaultPlans, ...normalized });
        } else {
          setPrices(defaultPlans);
        }
      } catch (error) {
        console.error('Failed to fetch prices from backend, using fallbacks:', error);
        setPrices(defaultPlans);
      } finally {
        setLoading(false);
      }
    };

    fetchPrices();
  }, []);

  if (loading) {
    return (
      <div className="w-full h-96 flex flex-col items-center justify-center gap-4">
        <Loader2 className="w-8 h-8 text-indigo-500 animate-spin" />
        <p className="text-slate-500 font-medium">Loading pricing options...</p>
      </div>
    );
  }

  const planOrder = ['starter', 'business', 'enterprise'];

  return (
    <div className="w-full max-w-7xl mx-auto px-6 py-16">
      <div className="mb-12 flex flex-col items-center text-center md:items-start md:text-left">
        <div className="inline-flex items-center gap-2 px-3 py-1.5 rounded-full bg-indigo-50/80 text-indigo-600 text-xs font-semibold mb-6 border border-indigo-100">
          <Sparkles size={14} />
          PLANS & PRICING
        </div>
        <h2 className="text-4xl md:text-5xl font-bold text-slate-900 mb-4 tracking-tight">Simple per-seat pricing</h2>
        <p className="text-slate-500 max-w-2xl text-lg mb-8">
          Billed {isAnnual ? 'annually' : 'monthly'} per user. Every plan includes the dashboard, pipeline and unlimited reporting history.
        </p>

        <div className="flex items-center gap-2 bg-slate-50 p-1.5 rounded-xl border border-slate-200 self-center md:self-start">
          <button
            onClick={() => setIsAnnual(false)}
            className={`px-5 py-2 rounded-lg text-sm font-medium transition-all ${!isAnnual ? 'bg-white text-slate-900 shadow-[0_2px_8px_-4px_rgba(0,0,0,0.1)] border border-slate-200/50' : 'text-slate-500 hover:text-slate-900'}`}
          >
            Monthly
          </button>
          <button
            onClick={() => setIsAnnual(true)}
            className={`px-5 py-2 rounded-lg text-sm font-medium transition-all flex items-center gap-2 ${isAnnual ? 'bg-white text-slate-900 shadow-[0_2px_8px_-4px_rgba(0,0,0,0.1)] border border-slate-200/50' : 'text-slate-500 hover:text-slate-900'}`}
          >
            Annually <span className="px-2 py-0.5 rounded-md bg-emerald-100 text-emerald-700 text-[10px] font-bold tracking-wide">SAVE 20%</span>
          </button>
        </div>
      </div>

      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
        {planOrder.map((planKey) => {
          const plan = planConfigs[planKey];
          const planPrice = prices[planKey] || { monthly: 0, annual: 0, currency: 'INR' };
          const value = isAnnual ? planPrice.annual : planPrice.monthly;
          const hasPrice = Number(value) > 0;

          return (
            <div
              key={planKey}
              className={`bg-white rounded-2xl border p-6 flex flex-col relative shadow-[0_2px_10px_-4px_rgba(0,0,0,0.05)] transition-all duration-300 hover:shadow-xl hover:-translate-y-1 ${
                plan.highlight
                  ? 'border-indigo-100 shadow-[0_8px_30px_-12px_rgba(99,102,241,0.2)] hover:border-indigo-300'
                  : 'border-slate-200 hover:border-indigo-200'
              }`}
            >
              {plan.highlight && (
                <div className="absolute -top-3 left-6 bg-indigo-500 text-white text-[10px] font-bold uppercase tracking-wider py-1 px-3 rounded-full">
                  Most Popular
                </div>
              )}

              <h3 className="text-lg font-semibold text-slate-900 mb-2 mt-2">{plan.title}</h3>

              <div className="mb-4 flex items-end gap-1">
                <span className="text-4xl font-bold text-slate-900 tracking-tight">
                  {hasPrice ? `₹${Number(value).toLocaleString('en-IN')}` : 'Custom'}
                </span>
                {hasPrice && <span className="text-slate-500 text-sm font-medium pb-1">/user/mo</span>}
              </div>

              <p className="text-slate-500 text-sm mb-6 h-10">{plan.description}</p>

              <ul className="space-y-4 mb-8 flex-1">
                {plan.features.map((feature, index) => (
                  <li key={`${planKey}-${index}`} className="flex items-center gap-3 text-sm text-slate-600">
                    <Check size={16} className="text-emerald-500 flex-shrink-0" />
                    {feature}
                  </li>
                ))}
              </ul>

              <button
                className={`w-full py-2.5 px-4 rounded-xl font-medium transition-colors ${
                  plan.highlight
                    ? 'bg-indigo-500 hover:bg-indigo-600 text-white'
                    : 'border border-slate-200 text-slate-900 hover:border-violet-500 hover:bg-violet-50 hover:text-violet-700'
                }`}
              >
                {planKey === 'enterprise' ? 'Contact sales' : 'Buy now'}
              </button>
            </div>
          );
        })}
      </div>
    </div>
  );
};

export default Pricing;