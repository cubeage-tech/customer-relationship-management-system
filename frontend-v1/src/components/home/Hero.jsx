import { Link } from 'react-router-dom';
import { Activity, ArrowUpRight, CircleDollarSign, Sparkles, TrendingUp, Users } from 'lucide-react';
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
        className="pointer-events-none absolute -top-40 right-[-10%] h-[520px] w-[520px] rounded-full bg-primary/10 blur-3xl"
      />
      <div
        aria-hidden="true"
        className="pointer-events-none absolute -bottom-32 left-[-10%] h-[420px] w-[420px] rounded-full bg-primary/5 blur-3xl"
      />

      <div className="relative max-w-7xl mx-auto px-6 py-20 lg:py-28 grid lg:grid-cols-2 gap-16 items-center">



        {/* Left — message */}
        <div>
          <span className="inline-flex items-center gap-2 rounded-full border border-primary/20 bg-primary/5 px-3 py-1 text-xs font-semibold tracking-wide text-primary uppercase">
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
              className="inline-flex items-center gap-2 rounded-full bg-primary px-6 py-3 font-semibold text-white shadow-sm shadow-indigo-600/20 transition-colors hover:bg-primary/90"
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
        <div className="hero-preview motion-rise motion-delay-3 relative mx-auto w-full max-w-[610px]">
          <div className="hero-panel rounded-[1.75rem] border border-slate-200 bg-white p-5 sm:p-7">
            <div className="flex items-center justify-between border-b border-slate-100 pb-5">
              <div className="flex items-center gap-3">
                <div className="grid size-10 place-items-center rounded-2xl bg-indigo-600 text-white shadow-lg shadow-indigo-600/25">
                  <Activity size={19} aria-hidden="true" />
                </div>
                <div>
                  <p className="text-sm font-bold text-slate-900">Revenue overview</p>
                  <p className="mt-0.5 text-xs text-slate-500">Your team is on track this month</p>
                </div>
              </div>
              <span className="inline-flex items-center gap-1.5 rounded-full bg-emerald-50 px-2.5 py-1 text-[11px] font-semibold text-emerald-700">
                <span className="size-1.5 rounded-full bg-emerald-500" />
                Live
              </span>
            </div>

            <div className="mt-5 grid grid-cols-2 gap-3 sm:grid-cols-3">
              <div className="hero-metric rounded-2xl border border-slate-100 bg-white p-3.5">
                <div className="flex items-center justify-between text-slate-500">
                  <span className="text-[11px] font-medium">Pipeline</span>
                  <CircleDollarSign size={15} className="text-indigo-500" />
                </div>
                <p className="mt-2 text-xl font-bold tracking-tight text-slate-900">$186.4k</p>
                <p className="mt-1 flex items-center gap-1 text-[11px] font-semibold text-emerald-600">
                  <TrendingUp size={12} /> 12.8% <span className="font-normal text-slate-400">vs last month</span>
                </p>
              </div>
              <div className="hero-metric rounded-2xl border border-slate-100 bg-white p-3.5">
                <div className="flex items-center justify-between text-slate-500">
                  <span className="text-[11px] font-medium">New leads</span>
                  <Users size={15} className="text-violet-500" />
                </div>
                <p className="mt-2 text-xl font-bold tracking-tight text-slate-900">248</p>
                <p className="mt-1 flex items-center gap-1 text-[11px] font-semibold text-emerald-600">
                  <TrendingUp size={12} /> 8.4% <span className="font-normal text-slate-400">this month</span>
                </p>
              </div>
              <div className="hero-metric hidden rounded-2xl border border-slate-100 bg-white p-3.5 sm:block">
                <div className="flex items-center justify-between text-slate-500">
                  <span className="text-[11px] font-medium">Win rate</span>
                  <ArrowUpRight size={15} className="text-emerald-500" />
                </div>
                <p className="mt-2 text-xl font-bold tracking-tight text-slate-900">38.6%</p>
                <p className="mt-1 text-[11px] font-medium text-slate-400">Quarter to date</p>
              </div>
            </div>

            <div className="mt-5 rounded-2xl border border-slate-100 bg-white p-4">
              <div className="flex items-center justify-between">
                <div>
                  <p className="text-xs font-bold text-slate-800">Deals in motion</p>
                  <p className="mt-0.5 text-[11px] text-slate-400">Live sales pipeline</p>
                </div>
                <span className="rounded-lg bg-indigo-50 px-2.5 py-1 text-[11px] font-semibold text-indigo-600">This quarter</span>
              </div>
              <ul className="mt-4 space-y-3">
              {PIPELINE_PREVIEW.map((deal) => (
                <li
                  key={deal.account}
                  className="pipeline-item"
                >
                  <div className="flex items-start justify-between gap-3">
                    <div className="flex min-w-0 items-center gap-2.5">
                      <span className="grid size-8 shrink-0 place-items-center rounded-xl bg-indigo-50 text-[10px] font-bold text-indigo-600">
                        {deal.account.split(' ').map((part) => part[0]).join('').slice(0, 2)}
                      </span>
                      <div className="min-w-0">
                        <p className="truncate text-xs font-semibold text-[#0F172A]">{deal.account}</p>
                        <p className="text-[10px] text-[#6B7280] mt-0.5">{deal.stage}</p>
                      </div>
                    </div>
                    <p className="pt-1 text-xs font-bold text-indigo-600 whitespace-nowrap">
                      {deal.amount}
                    </p>
                  </div>
                  <div className="mt-2.5 h-1.5 w-full overflow-hidden rounded-full bg-slate-100">
                    <div
                      className="pipeline-fill h-full rounded-full bg-gradient-to-r from-indigo-600 to-indigo-400"
                      style={{ '--progress-width': `${deal.progress}%` }}
                    />
                  </div>
                </li>
              ))}
              </ul>
            </div>
          </div>
          <div className="hero-ai-float absolute -bottom-5 -left-4 hidden items-center gap-2.5 rounded-2xl border border-white/80 bg-white/95 px-4 py-3 shadow-xl shadow-indigo-950/10 backdrop-blur sm:flex">
            <span className="grid size-9 place-items-center rounded-xl bg-gradient-to-br from-indigo-600 to-violet-500 text-white shadow-md shadow-indigo-500/25">
              <Sparkles size={17} aria-hidden="true" />
            </span>
            <span>
              <span className="block text-xs font-bold text-slate-800">AI insight</span>
              <span className="mt-0.5 block text-[11px] text-slate-500">3 deals ready for follow-up</span>
            </span>
            <span className="ml-1 size-2 rounded-full bg-emerald-400 ring-4 ring-emerald-100" />
          </div>
        </div>
      </div>
    </section>
  );
};

export default Hero;