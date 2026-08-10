import PageHeader from '../common/PageHeader';
import StatCard from '../common/StatCard';

/**
 * Shared layout for the role dashboards: a heading, a row of summary tiles and
 * a slot for role specific content.
 */
const DashboardShell = ({ title, subtitle, stats = [], children }) => {
  return (
    <section>
      <PageHeader title={title} subtitle={subtitle} />

      {stats.length > 0 && (
        <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4 mb-6">
          {stats.map((stat) => (
            <StatCard
              key={stat.label}
              label={stat.label}
              value={stat.value}
              hint={stat.hint}
            />
          ))}
        </div>
      )}

      {children}
    </section>
  );
};

export default DashboardShell;
