import { Link } from 'react-router-dom';
import { ArrowRight, TrendingUp } from 'lucide-react';
import RoutePath from '../../core/constants/routes.constant';
import { APP_NAME } from '../../core/constants/app.constant';

/** Sample pipeline preview shown on the marketing hero — mirrors real Opportunity stages. */
const PIPELINE_PREVIEW = [
  { account: 'Northwind Group', stage: 'Negotiation', amount: '$84,500', progress: 85 },
  { account: 'Vertex Labs', stage: 'Proposal', amount: '$52,000', progress: 60 },
  { account: 'Halden Retail', stage: 'Qualified', amount: '$31,200', progress: 35 },
  { account: 'Orbit Health', stage: 'New lead', amount: '$18,900', progress: 15 },
];

const STATS = [
  { value: '7', label: 'Role-based dashboards' },
  { value: '6', label: 'Modules, one workspace' },
  { value: '100%', label: 'Access scoped to role' },
];

const Hero = () => {
  return (
    <section className="relative overflow-hidden bg-[#F7F8FA]">
      {/* soft ambient backdrop */}
      <div
        aria-hidden="true"
        className="pointer-events-none absolute -top-40 right-[-10%] h-[520px] w-[520px] rounded-full bg-blue-600/10 blur-3xl"
      />
      <div
        aria-hidden="true"
        className="pointer-events-none absolute -bottom-32 left-[-10%] h-[420px] w-[420px] rounded-full bg-blue-600/5 blur-3xl"
      />

      <div className="relative max-w-7xl mx-auto px-6 py-20 lg:py-28 grid lg:grid-cols-2 gap-16 items-center">
        {/* Left — message */}
        <div>
          <span className="inline-flex items-center gap-2 rounded-full border border-blue-200 bg-blue-50 px-3 py-1 text-xs font-semibold tracking-wide text-blue-700 uppercase">
            <TrendingUp className="h-3.5 w-3.5" aria-hidden="true" />
            Role-aware CRM
          </span>

          <h1 className="mt-6 text-4xl sm:text-5xl font-bold tracking-tight text-[#0F172A] leading-[1.1]">
            One workspace for every deal,
            <span className="text-blue-600"> ticket and customer.</span>
          </h1>

          <p className="mt-6 text-lg text-[#6B7280] max-w-xl">
            {APP_NAME} connects customers, leads, opportunities, quotations and
            service tickets in one place — with dashboards built for exactly
            what each role needs to see, and nothing they don't.
          </p>

          <div className="mt-8 flex flex-wrap items-center gap-4">
            <Link
              to={RoutePath.SIGNUP}
              className="group inline-flex items-center gap-2 rounded-lg bg-blue-600 px-5 py-2.5 font-semibold text-white shadow-sm shadow-blue-600/20 transition-colors hover:bg-[#1D4ED8]"
            >
              Get started
              <ArrowRight className="h-4 w-4 transition-transform group-hover:translate-x-0.5" aria-hidden="true" />
            </Link>
            <Link
              to={RoutePath.LOGIN}
              className="inline-flex items-center gap-2 rounded-lg border border-slate-300 bg-white px-5 py-2.5 font-semibold text-[#0F172A] transition-colors hover:bg-slate-50"
            >
              Log in
            </Link>
          </div>

          <dl className="mt-12 grid grid-cols-3 gap-6 max-w-md">
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
            <div className="flex items-center justify-between">
              <p className="text-xs font-semibold uppercase tracking-wide text-[#6B7280]">
                Pipeline snapshot
              </p>
              <span className="rounded-full bg-blue-50 px-2.5 py-1 text-xs font-semibold text-blue-700">
                Live
              </span>
            </div>

            <ul className="mt-5 space-y-4">
              {PIPELINE_PREVIEW.map((deal) => (
                <li
                  key={deal.account}
                  className="rounded-xl border border-slate-100 p-4 transition-colors hover:border-blue-100 hover:bg-blue-50/30"
                >
                  <div className="flex items-start justify-between gap-3">
                    <div>
                      <p className="font-semibold text-[#0F172A]">{deal.account}</p>
                      <p className="text-xs text-[#6B7280] mt-0.5">{deal.stage}</p>
                    </div>
                    <p className="font-semibold text-blue-600 whitespace-nowrap">
                      {deal.amount}
                    </p>
                  </div>
                  <div className="mt-3 h-1.5 w-full overflow-hidden rounded-full bg-slate-100">
                    <div
                      className="h-full rounded-full bg-blue-600"
                      style={{ width: `${deal.progress}%` }}
                    />
                  </div>
                </li>
              ))}
            </ul>
          </div>

          {/* floating badge, tucked behind the card */}
          <div
            aria-hidden="true"
            className="absolute -z-10 -top-4 -right-4 h-full w-full rounded-2xl border border-blue-100 bg-blue-50/60"
          />
        </div>
      </div>
    </section>
  );
};

export default Hero;
