import { Users, Workflow, BarChart3, Bot, ShieldCheck, CheckCircle2 } from 'lucide-react';

const NEEDS = [
  {
    icon: Users,
    title: 'Unified lead intelligence',
    description: 'Every lead scored, enriched and routed automatically to the right owner in seconds.',
  },
  {
    icon: Workflow,
    title: 'Pipeline that moves itself',
    description: 'Drag-and-drop kanban with inline editing, stage totals and deal risk signals.',
  },
  {
    icon: BarChart3,
    title: 'Revenue analytics',
    description: 'Lead growth, forecast and campaign ROI in dashboards your board actually reads.',
  },
  {
    icon: Bot,
    title: 'AI copilot',
    description: 'Draft follow-ups, summarise calls and surface next best action inside the record.',
  },
  {
    icon: ShieldCheck,
    title: 'Enterprise ready',
    description: 'SSO, granular roles, audit trails and regional data residency out of the box.',
  },
  {
    icon: CheckCircle2,
    title: 'Live in days',
    description: 'Guided imports and prebuilt playbooks get your team productive in under a week.',
  },
];

const Needs = () => {
  return (
    <section className="bg-background py-16 px-6">
      <div className="max-w-7xl mx-auto">
        <h2 className="text-3xl sm:text-4xl font-bold text-foreground">
          Everything a revenue team needs
        </h2>
        <p className="mt-3 text-muted-foreground">
          One system of record for pipeline, marketing performance and AI-assisted execution.
        </p>

        <div className="mt-10 grid gap-6 sm:grid-cols-2 lg:grid-cols-3">
          {NEEDS.map(({ icon: Icon, title, description }) => (
            <div
              key={title}
              className="surface-card p-6 transition-all duration-300 hover:shadow-elevated hover:-translate-y-0.5"
            >
              <div className="flex h-12 w-12 items-center justify-center rounded-2xl gradient-primary">
                <Icon className="h-6 w-6 text-primary-foreground" aria-hidden="true" />
              </div>
              <h3 className="mt-5 font-bold text-foreground">{title}</h3>
              <p className="mt-2 text-sm text-muted-foreground leading-relaxed">{description}</p>
            </div>
          ))}
        </div>
      </div>
    </section>
  );
};

export default Needs;
