import { useState } from 'react';
import { Clock3, Mail, MapPin, Phone, Sparkles } from 'lucide-react';

const enquiryTypes = ['Book a demo', 'Pricing & plans', 'Technical support', 'Partnerships'];

const contactDetails = [
  { label: 'Email', value: 'hello@smartcrm.ai', icon: Mail },
  { label: 'Phone', value: '+1 (415) 555-0148', icon: Phone },
  { label: 'HQ', value: '440 Market St, San Francisco', icon: MapPin },
  { label: 'Response time', value: 'Within 1 business day', icon: Clock3 },
];

const Contact = () => {
  const [activeEnquiry, setActiveEnquiry] = useState(enquiryTypes[0]);
  const isPricingEnquiry = activeEnquiry === 'Pricing & plans';
  const isSupportEnquiry = activeEnquiry === 'Technical support';
  const isPartnershipEnquiry = activeEnquiry === 'Partnerships';

  return (
    <section className="page-canvas px-6 py-14 sm:py-16 lg:py-[5.25rem]">
      <div className="mx-auto max-w-7xl">
        <div className="max-w-[760px]">
          <div className="inline-flex items-center gap-2 rounded-full border border-indigo-200 bg-indigo-50 px-3 py-1 text-[11px] font-bold uppercase tracking-[0.18em] text-indigo-700">
            <Sparkles size={14} strokeWidth={2.5} />
            Contact us
          </div>
          <h1 className="mt-7 text-4xl font-bold leading-[1.08] tracking-tight text-slate-950 sm:text-5xl lg:text-[50px]">
            Let&apos;s talk about your revenue stack
          </h1>
          <p className="mt-4 text-base leading-6 text-slate-500 sm:text-[17px]">
            Tell us a little about your team and we&apos;ll tailor a walkthrough around your pipeline.
          </p>
        </div>

        <div className="mt-9 grid gap-5 lg:grid-cols-[1.35fr_0.85fr]">
          <form className="surface-card p-6 sm:p-7">
            <div className="flex flex-wrap gap-2">
              {enquiryTypes.map((type) => (
                <button
                  key={type}
                  type="button"
                  onClick={() => setActiveEnquiry(type)}
                  className={`rounded-full border px-3 py-2 text-xs transition-colors ${
                    activeEnquiry === type
                      ? 'border-indigo-300 bg-indigo-50 font-medium text-indigo-700'
                      : 'border-slate-200 bg-white text-slate-500 hover:border-indigo-200 hover:text-indigo-700'
                  }`}
                >
                  {type}
                </button>
              ))}
            </div>

            <div className="mt-5 grid gap-4 sm:grid-cols-2">
              <label className="text-xs font-semibold text-slate-700">
                Full name
                <input
                  className="mt-2 block w-full rounded-xl border border-slate-200 bg-slate-50 px-3 py-3 text-sm font-normal text-slate-700 outline-none placeholder:text-slate-400 focus:border-indigo-300 focus:ring-2 focus:ring-indigo-100"
                  placeholder="John Doe"
                  type="text"
                />
              </label>
              <label className="text-xs font-semibold text-slate-700">
                Work email
                <input
                  className="mt-2 block w-full rounded-xl border border-slate-200 bg-slate-50 px-3 py-3 text-sm font-normal text-slate-700 outline-none placeholder:text-slate-400 focus:border-indigo-300 focus:ring-2 focus:ring-indigo-100"
                  placeholder="john@company.com"
                  type="email"
                />
              </label>
              <label className="text-xs font-semibold text-slate-700">
                Company{isSupportEnquiry ? ' (optional)' : ''}
                <input
                  className="mt-2 block w-full rounded-xl border border-slate-200 bg-slate-50 px-3 py-3 text-sm font-normal text-slate-700 outline-none placeholder:text-slate-400 focus:border-indigo-300 focus:ring-2 focus:ring-indigo-100"
                  placeholder={isSupportEnquiry ? 'Acme Inc. or Account / Org ID' : 'Acme Inc.'}
                  type="text"
                />
              </label>
              {isSupportEnquiry ? (
                <label className="text-xs font-semibold text-slate-700">
                  Plan
                  <select className="mt-2 block w-full rounded-xl border border-slate-200 bg-slate-50 px-3 py-3 text-sm font-normal text-slate-700 outline-none focus:border-indigo-300 focus:ring-2 focus:ring-indigo-100" defaultValue="Starter">
                    <option>Starter</option>
                    <option>Growth</option>
                    <option>Scale</option>
                    <option>Enterprise</option>
                  </select>
                </label>
              ) : isPartnershipEnquiry ? (
                <label className="text-xs font-semibold text-slate-700">
                  Partnership type
                  <select className="mt-2 block w-full rounded-xl border border-slate-200 bg-slate-50 px-3 py-3 text-sm font-normal text-slate-700 outline-none focus:border-indigo-300 focus:ring-2 focus:ring-indigo-100" defaultValue="Integration/Tech partner">
                    <option>Integration/Tech partner</option>
                    <option>Reseller</option>
                    <option>Agency</option>
                    <option>Referral</option>
                    <option>Other</option>
                  </select>
                </label>
              ) : (
                <label className="text-xs font-semibold text-slate-700">
                  Team size
                  <select className="mt-2 block w-full rounded-xl border border-slate-200 bg-slate-50 px-3 py-3 text-sm font-normal text-slate-700 outline-none focus:border-indigo-300 focus:ring-2 focus:ring-indigo-100" defaultValue="11-50">
                    <option>1-10</option>
                    <option>11-50</option>
                    <option>51-200</option>
                    <option>201+</option>
                  </select>
                </label>
              )}
              {isSupportEnquiry && (
                <label className="text-xs font-semibold text-slate-700">
                  Priority/urgency
                  <select className="mt-2 block w-full rounded-xl border border-slate-200 bg-slate-50 px-3 py-3 text-sm font-normal text-slate-700 outline-none focus:border-indigo-300 focus:ring-2 focus:ring-indigo-100" defaultValue="Normal">
                    <option>Low</option>
                    <option>Normal</option>
                    <option>High</option>
                    <option>Critical - production down</option>
                  </select>
                </label>
              )}
              {isPricingEnquiry && (
                <label className="text-xs font-semibold text-slate-700 sm:col-span-2">
                  Which plan are you interested in?
                  <select
                    className="mt-2 block w-full rounded-xl border border-slate-200 bg-slate-50 px-3 py-3 text-sm font-normal text-slate-700 outline-none focus:border-indigo-300 focus:ring-2 focus:ring-indigo-100"
                    defaultValue="Not sure yet"
                  >
                    <option>Starter</option>
                    <option>Growth</option>
                    <option>Scale</option>
                    <option>Enterprise</option>
                    <option>Not sure yet</option>
                  </select>
                </label>
              )}
              {isPartnershipEnquiry && (
                <label className="text-xs font-semibold text-slate-700 sm:col-span-2">
                  Company website
                  <input
                    className="mt-2 block w-full rounded-xl border border-slate-200 bg-slate-50 px-3 py-3 text-sm font-normal text-slate-700 outline-none placeholder:text-slate-400 focus:border-indigo-300 focus:ring-2 focus:ring-indigo-100"
                    placeholder="https://yourcompany.com"
                    type="url"
                  />
                </label>
              )}
            </div>

            <label className="mt-4 block text-xs font-semibold text-slate-700">
              {isSupportEnquiry
                ? 'Describe the issue'
                : isPricingEnquiry
                  ? 'Questions about pricing?'
                  : isPartnershipEnquiry
                    ? 'Tell us about the opportunity'
                  : 'How can we help?'}
              <textarea
                className="mt-2 block min-h-[108px] w-full resize-y rounded-xl border border-slate-200 bg-slate-50 px-3 py-3 text-sm font-normal leading-5 text-slate-700 outline-none placeholder:text-slate-400 focus:border-indigo-300 focus:ring-2 focus:ring-indigo-100"
                placeholder={
                  isSupportEnquiry
                    ? "What's happening, when did it start, steps to reproduce..."
                    : isPricingEnquiry
                      ? 'Ask about billing, discounts, or plan limits...'
                      : isPartnershipEnquiry
                        ? 'What are you looking to build or offer together?'
                        : "We're a 30-person sales team moving off spreadsheets..."
                }
              />
            </label>

            <div className="mt-6 flex items-center gap-4">
              <button type="submit" className="gradient-primary rounded-xl px-5 py-3 text-xs font-semibold text-white shadow-glow">
                Send message
              </button>
              <span className="text-xs text-slate-400">No spam, ever.</span>
            </div>
          </form>

          <div className="space-y-5">
            <aside className="surface-card p-6">
              <h2 className="text-sm font-semibold text-slate-800">Reach us directly</h2>
              <div className="mt-5 space-y-4">
                {contactDetails.map(({ label, value, icon: Icon }) => (
                  <div key={label} className="flex items-center gap-3">
                    <span className="gradient-primary flex size-8 shrink-0 items-center justify-center rounded-full text-white shadow-glow">
                      <Icon size={16} strokeWidth={1.8} />
                    </span>
                    <div>
                      <p className="text-[10px] uppercase tracking-wide text-slate-400">{label}</p>
                      <p className="text-xs font-medium text-slate-700">{value}</p>
                    </div>
                  </div>
                ))}
              </div>
            </aside>

            <aside className="surface-card p-6">
              <h2 className="text-sm font-semibold text-slate-800">Enterprise enquiries</h2>
              <p className="mt-2 text-xs leading-5 text-slate-500">
                Need SSO, data residency or a security review? Our enterprise team responds within 4 hours on business days.
              </p>
            </aside>
          </div>
        </div>
      </div>
    </section>
  );
};

export default Contact;
