const milestones = [
  {
    year: '2021',
    description: 'Founded after years of watching reps fight their CRM.',
  },
  {
    year: '2023',
    description: 'Launched AI lead scoring; crossed 500 revenue teams.',
  },
  {
    year: '2025',
    description: 'Shipped the AI copilot and unified marketing analytics.',
  },
  {
    year: '2026',
    description: '2,400+ teams across 34 countries run on SmartCRM AI.',
  },
]

const Journey = () => {
  return (
    <section className="bg-transparent px-4 pb-16 pt-6 sm:px-6 sm:pb-24 sm:pt-8 lg:pt-12">
      <div className="mx-auto grid max-w-7xl gap-10 lg:grid-cols-[1fr_1.15fr] lg:gap-20">
        <div>
          <h2 className="text-2xl font-bold tracking-tight text-slate-900 sm:text-4xl">
            Our journey
          </h2>
          <p className="mt-4 text-base leading-6 text-slate-500">
            From a two-person prototype to an enterprise suite.
          </p>
        </div>

        <ol className="relative border-l border-slate-200 pl-5 sm:pl-7">
          {milestones.map(({ year, description }) => (
            <li key={year} className="relative pb-8 last:pb-0">
              <span className="gradient-primary absolute -left-[28px] top-0 size-3.5 rounded-full ring-4 ring-[#f8f9fc] sm:-left-[35px]" />
              <p className="text-sm font-bold text-indigo-600">{year}</p>
              <p className="mt-2 text-[15px] leading-6 text-slate-500">
                {description}
              </p>
            </li>
          ))}
        </ol>
      </div>
    </section>
  )
}

export default Journey