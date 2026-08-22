import PageHeader from '../../components/common/PageHeader';
import EmptyState from '../../components/common/EmptyState';

const PlatformReports = () => {
  return (
    <section>
      <PageHeader
        title="Reports"
        subtitle="Cross-tenant sales, growth and usage analytics."
      />

      <EmptyState
        title="Platform reports are coming soon"
        message="Tenant growth is already visible on the platform dashboard — deeper cross-tenant sales and usage reports will appear here once the sales module ships."
      />
    </section>
  );
};

export default PlatformReports;
