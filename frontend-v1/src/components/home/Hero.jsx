import { Link } from 'react-router-dom';
import { Sparkles } from 'lucide-react';
import RoutePath from '../../core/constants/routes.constant';

/** Sample pipeline preview shown on the marketing hero — mirrors real Opportunity stages. */
const PIPELINE_PREVIEW = [
  { account: 'Northwind Group', stage: 'Negotiation', amount: '$84,500', progress: 85 },
  { account: 'Vertex Labs', stage: 'Proposal', amount: '$52,000', progress: 60 },
  { account: 'Halden Retail', stage: 'Qualified', amount: '$31,200', progress: 35 },
  { account: 'Orbit Health', stage: 'Discovery', amount: '$18,900', progress: 15 },
];

const STATS = [
  { value: '38%', label: 'Higher win rate' },
  { value: '6.2h', label: 'Saved per rep weekly' },
  { value: '2,400+', label: 'Revenue teams' },
  { value: '99.98%', label: 'Uptime' },
];

const Hero = () => {
  return (
    <section className="relative overflow-hidden bg-background">
      {/* soft ambient backdrop */}
      <div
        aria-hidden="true"
        className="pointer-events-none absolute -top-40 right-[-10%] h-[520px] w-[520px] rounded-full bg-indigo-600/10 blur-3xl"
      />
      <div
        aria-hidden="true"
        className="pointer-events-none absolute -bottom-32 left-[-10%] h-[420px] w-[420px] rounded-full bg-indigo-600/5 blur-3xl"
      />

      <div className="relative max-w-7xl mx-auto px-6 py-20 lg:py-28 grid lg:grid-cols-2 gap-16 items-center">
        {/* Left — message */}
        <div>
          <span className="inline-flex items-center gap-2 rounded-full border border-indigo-200 bg-indigo-50 px-3 py-1 text-xs font-semibold tracking-wide text-indigo-700 uppercase">
            <Sparkles className="h-3.5 w-3.5" aria-hidden="true" />
            AI-Native CRM
          </span>

          <h1 className="mt-6 text-4xl sm:text-5xl font-bold tracking-tight text-[#0F172A] leading-[1.1]">
            The CRM that closes the loop between marketing and revenue
          </h1>

          <p className="mt-6 text-lg text-[#6B7280] max-w-xl">
            SmartCRM AI brings leads, pipeline, campaigns and AI recommendations into one spacious workspace — so your team spends time selling, not updating records.
          </p>

          <div className="mt-8 flex flex-wrap items-center gap-4">
            <Link
              to={RoutePath.PLANS}
              className="inline-flex items-center gap-2 rounded-full bg-indigo-600 px-6 py-3 font-semibold text-white shadow-sm shadow-indigo-600/20 transition-colors hover:bg-indigo-700"
            >
              See plans
            </Link>
            <Link
              to={RoutePath.CONTACT}
              className="inline-flex items-center gap-2 rounded-full border border-slate-200 bg-white px-6 py-3 font-semibold text-[#0F172A] transition-colors hover:bg-slate-50"
            >
              Book a demo
            </Link>
          </div>

          <dl className="mt-12 grid grid-cols-4 gap-8 max-w-lg">
            {STATS.map((stat) => (
              <div key={stat.label}>
                <dt className="sr-only">{stat.label}</dt>
                <dd className="text-2xl font-bold text-[#0F172A]">{stat.value}</dd>
                <p className="mt-1 text-xs text-[#6B7280] leading-snug">{stat.label}</p>
              </div>
            ))}
          </dl>
        </div>

        {/* Right — live pipeline snapshot */}
        <div className="relative">
          <div className="rounded-2xl border border-slate-200 bg-white p-6 shadow-xl shadow-slate-900/5">
            <p className="text-xs font-semibold uppercase tracking-wide text-[#6B7280]">
              Pipeline snapshot
            </p>

            <ul className="mt-5 space-y-4">
              {PIPELINE_PREVIEW.map((deal) => (
                <li
                  key={deal.account}
                  className="rounded-xl border border-slate-100 p-4 transition-colors hover:border-indigo-100 hover:bg-indigo-50/30"
                >
                  <div className="flex items-start justify-between gap-3">
                    <div>
                      <p className="font-semibold text-[#0F172A]">{deal.account}</p>
                      <p className="text-xs text-[#6B7280] mt-0.5">{deal.stage}</p>
                    </div>
                    <p className="font-semibold text-indigo-600 whitespace-nowrap">
                      {deal.amount}
                    </p>
                  </div>
                  <div className="mt-3 h-1.5 w-full overflow-hidden rounded-full bg-slate-100">
                    <div
                      className="h-full rounded-full bg-gradient-to-r from-indigo-600 to-indigo-400"
                      style={{ width: `${deal.progress}%` }}
                    />
                  </div>
                </li>
              ))}
            </ul>
          </div>
        </div>
      </div>
    </section>
  );
};

export default Hero;