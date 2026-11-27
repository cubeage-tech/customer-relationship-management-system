
import { Link } from 'react-router-dom'

const leaders = [
  
  { initials: 'AR', name: 'Ananya Rao', role: 'Co-founder & CEO' },
  { initials: 'MF', name: 'Marcus Feld', role: 'Co-founder & CTO' },
  { initials: 'LN', name: 'Leah Nakamura', role: 'Head of Design' },
  { initials: 'DA', name: 'Diego Alvarez', role: 'VP Customer Success' },
]

const Leadership = () => {
  return (
    <section className="bg-transparent pb-16 pt-6 sm:pb-24 sm:pt-8 lg:pt-12">
      <div className="mx-auto max-w-7xl px-4 sm:px-6">
        <h2 className="text-2xl font-bold tracking-tight text-slate-900 sm:text-4xl">
          Leadership
        </h2>

        <div className="mt-8 grid gap-5 sm:grid-cols-2 lg:grid-cols-4">
          {leaders.map(({ initials, name, role }) => (
            <article
              key={name}
              className="surface-card flex min-h-[180px] flex-col items-center justify-center px-6 py-6 text-center transition-transform duration-200 hover:-translate-y-1"
            >
              <div className="gradient-primary shadow-glow flex size-16 items-center justify-center rounded-full text-lg font-bold text-white">
                {initials}
              </div>
              <h3 className="mt-5 text-base font-semibold text-slate-800">{name}</h3>
              <p className="mt-2 text-sm text-slate-500">{role}</p>
            </article>
          ))}
        </div>

        <div className="surface-card mt-16 flex flex-col gap-6 px-5 py-6 sm:mt-24 sm:px-10 sm:py-8 lg:flex-row lg:items-center lg:justify-between lg:px-11">
          <h2 className="text-xl font-bold tracking-tight text-slate-900 sm:text-3xl">
            Want to work with us?
          </h2>
          <Link
            to="/contact"
            className="gradient-primary shadow-glow inline-flex h-12 w-full items-center justify-center rounded-2xl px-6 text-base font-semibold text-white transition-transform duration-200 hover:-translate-y-0.5 sm:w-auto"
          >
            Get in touch
          </Link>
        </div>
      </div>
    </section>
  )
}

export default Leadership