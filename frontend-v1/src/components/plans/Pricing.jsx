import { useState, useEffect } from 'react';
import { Check, Sparkles, Loader2 } from 'lucide-react';
import axios from 'axios';

const Pricing = () => {
  const [isAnnual, setIsAnnual] = useState(false);
  const [prices, setPrices] = useState(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    const fetchPrices = async () => {
      try {
        // TODO: Replace with your actual backend endpoint configuration
        const response = await axios.get('/api/plans/prices');
        
        // Validate the response to ensure it's the expected JSON structure
        if (response.data && typeof response.data === 'object' && response.data.starter) {
          setPrices(response.data);
        } else {
          throw new Error("Invalid response format from backend");
        }
      } catch (error) {
        console.error("Failed to fetch prices from backend, using fallbacks:", error);
        // Fallback prices so the UI still works during development
        setPrices({
          starter: { monthly: 1500, annual: 1200 },
          growth: { monthly: 3900, annual: 3100 },
          scale: { monthly: 7100, annual: 6300 }
        });
      } finally {
        setLoading(false);
      }
    };

    fetchPrices();
  }, []);

  if (loading || !prices) {
    return (
      <div className="w-full h-96 flex flex-col items-center justify-center gap-4">
        <Loader2 className="w-8 h-8 text-indigo-500 animate-spin" />
        <p className="text-slate-500 font-medium">Loading pricing options...</p>
      </div>
    );
  }

  return (
    <div className="w-full max-w-6xl mx-auto px-4 py-16">
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

      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-6">
        {/* Starter */}
        <div className="bg-white rounded-2xl border border-slate-200 p-6 flex flex-col relative shadow-[0_2px_10px_-4px_rgba(0,0,0,0.05)] transition-all duration-300 hover:shadow-xl hover:-translate-y-1 hover:border-indigo-200">
          <h3 className="text-lg font-semibold text-slate-900 mb-2">Starter</h3>
          <div className="mb-4 flex items-end gap-1">
            <span className="text-4xl font-bold text-slate-900 tracking-tight">₹{(isAnnual ? prices.starter.annual : prices.starter.monthly).toLocaleString('en-IN')}</span>
            <span className="text-slate-500 text-sm font-medium pb-1">/user/mo</span>
          </div>
          <p className="text-slate-500 text-sm mb-6 h-10">For small teams getting organised</p>
          
          <ul className="space-y-4 mb-8 flex-1">
            {['Up to 3 seats', '2,000 contacts', 'Pipeline & tasks', 'Email sync', '500 AI credits'].map((feature, i) => (
              <li key={i} className="flex items-center gap-3 text-sm text-slate-600">
                <Check size={16} className="text-emerald-500 flex-shrink-0" />
                {feature}
              </li>
            ))}
          </ul>
          <button className="w-full py-2.5 px-4 rounded-xl border border-slate-200 text-slate-900 font-medium hover:border-violet-500 hover:bg-violet-50 hover:text-violet-700 transition-colors">
            Buy now
          </button>
        </div>

        {/* Growth */}
        <div className="bg-white rounded-2xl border border-indigo-100 p-6 flex flex-col relative shadow-[0_8px_30px_-12px_rgba(99,102,241,0.2)] transition-all duration-300 hover:shadow-2xl hover:shadow-indigo-500/10 hover:-translate-y-1 hover:border-indigo-300">
          <div className="absolute -top-3 left-6 bg-indigo-500 text-white text-[10px] font-bold uppercase tracking-wider py-1 px-3 rounded-full">
            Most Popular
          </div>
          <h3 className="text-lg font-semibold text-slate-900 mb-2 mt-2">Growth</h3>
          <div className="mb-4 flex items-end gap-1">
            <span className="text-4xl font-bold text-slate-900 tracking-tight">₹{(isAnnual ? prices.growth.annual : prices.growth.monthly).toLocaleString('en-IN')}</span>
            <span className="text-slate-500 text-sm font-medium pb-1">/user/mo</span>
          </div>
          <p className="text-slate-500 text-sm mb-6 h-10">Most popular for scaling sales teams</p>
          
          <ul className="space-y-4 mb-8 flex-1">
            {['Up to 25 seats', '50,000 contacts', 'AI lead scoring', 'Campaign analytics', '5,000 AI credits'].map((feature, i) => (
              <li key={i} className="flex items-center gap-3 text-sm text-slate-600">
                <Check size={16} className="text-emerald-500 flex-shrink-0" />
                {feature}
              </li>
            ))}
          </ul>
          <button className="w-full py-2.5 px-4 rounded-xl bg-indigo-500 hover:bg-indigo-600 text-white font-medium transition-colors">
            Buy now
          </button>
        </div>

        {/* Scale */}
        <div className="bg-white rounded-2xl border border-slate-200 p-6 flex flex-col relative shadow-[0_2px_10px_-4px_rgba(0,0,0,0.05)] transition-all duration-300 hover:shadow-xl hover:-translate-y-1 hover:border-indigo-200">
          <h3 className="text-lg font-semibold text-slate-900 mb-2">Scale</h3>
          <div className="mb-4 flex items-end gap-1">
            <span className="text-4xl font-bold text-slate-900 tracking-tight">₹{(isAnnual ? prices.scale.annual : prices.scale.monthly).toLocaleString('en-IN')}</span>
            <span className="text-slate-500 text-sm font-medium pb-1">/user/mo</span>
          </div>
          <p className="text-slate-500 text-sm mb-6 h-10">Advanced automation and forecasting</p>
          
          <ul className="space-y-4 mb-8 flex-1">
            {['Unlimited seats', 'Unlimited contacts', 'Revenue forecasting', 'Workflow automation', '25,000 AI credits'].map((feature, i) => (
              <li key={i} className="flex items-center gap-3 text-sm text-slate-600">
                <Check size={16} className="text-emerald-500 flex-shrink-0" />
                {feature}
              </li>
            ))}
          </ul>
          <button className="w-full py-2.5 px-4 rounded-xl border border-slate-200 text-slate-900 font-medium hover:border-violet-500 hover:bg-violet-50 hover:text-violet-700 transition-colors">
            Buy now
          </button>
        </div>

        {/* Enterprise */}
        <div className="bg-white rounded-2xl border border-slate-200 p-6 flex flex-col relative shadow-[0_2px_10px_-4px_rgba(0,0,0,0.05)] transition-all duration-300 hover:shadow-xl hover:-translate-y-1 hover:border-indigo-200">
          <h3 className="text-lg font-semibold text-slate-900 mb-2">Enterprise</h3>
          <div className="mb-4 flex items-end gap-1">
            <span className="text-4xl font-bold text-slate-900 tracking-tight">Custom</span>
          </div>
          <p className="text-slate-500 text-sm mb-6 h-10">Security, residency and dedicated support</p>
          
          <ul className="space-y-4 mb-8 flex-1">
            {['SSO & SCIM', 'Data residency', 'Audit logs', 'Dedicated CSM', 'Custom AI credits'].map((feature, i) => (
              <li key={i} className="flex items-center gap-3 text-sm text-slate-600">
                <Check size={16} className="text-emerald-500 flex-shrink-0" />
                {feature}
              </li>
            ))}
          </ul>
          <button className="w-full py-2.5 px-4 rounded-xl border border-slate-200 text-slate-900 font-medium hover:border-violet-500 hover:bg-violet-50 hover:text-violet-700 transition-colors">
            Contact sales
          </button>
        </div>
      </div>
    </div>
  );
};

export default Pricing;