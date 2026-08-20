import { Compass, Heart, Rocket, Target } from 'lucide-react';

const principles = [
  {
    title: 'Clarity over clutter',
    description: 'Every screen answers one question fast.',
    icon: Target,
  },
  {
    title: 'Speed is a feature',
    description: 'Sub-second interactions, everywhere.',
    icon: Rocket,
  },
  {
    title: 'Built with customers',
    description: 'Shipped weekly from real rep feedback.',
    icon: Heart,
  },
  {
    title: 'Trust by default',
    description: 'Transparent AI, auditable data, no lock-in.',
    icon: Compass,
  },
];

const Hero = () => {
  return (
    <section className="bg-transparent px-6 py-14 sm:py-16 lg:py-[5.25rem]">
      <div className="mx-auto max-w-7xl">
        <div className="max-w-[760px]">
          <div className="inline-flex items-center gap-2 rounded-full border border-indigo-200 bg-indigo-50 px-3 py-1 text-[11px] font-bold uppercase tracking-[0.18em] text-indigo-700">
            <Compass size={14} strokeWidth={2.5} />
            About us
          </div>
          <h1 className="mt-7 max-w-[740px] text-4xl font-bold leading-[1.08] tracking-tight text-slate-950 sm:text-5xl lg:text-[50px]">
            We build revenue software that respects the people using it
          </h1>
          <p className="mt-6 max-w-[710px] text-base leading-6 text-slate-500 sm:text-[17px]">
            SmartCRM AI started with a simple frustration: CRMs were built for
            reporting, not for selling. We rebuilt the category around the daily
            rhythm of a revenue team - fast capture, clear pipeline, and AI that
            does the admin nobody wants.
          </p>
        </div>

        <div className="mt-20 grid gap-5 sm:grid-cols-2 lg:mt-[5.25rem] lg:grid-cols-4">
          {principles.map(({ title, description, icon: Icon }) => (
            <article
              key={title}
              className="surface-card min-h-[187px] px-6 py-6 transition-transform duration-200 hover:-translate-y-1"
            >
              <div className="gradient-primary shadow-glow flex size-12 items-center justify-center rounded-2xl text-white">
                <Icon size={23} strokeWidth={1.8} />
              </div>
              <h2 className="mt-5 text-base font-semibold text-slate-800">{title}</h2>
              <p className="mt-3 text-sm leading-5 text-slate-500">{description}</p>
            </article>
          ))}
        </div>
      </div>
    </section>
  );
};

export default Hero;
