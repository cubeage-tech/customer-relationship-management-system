import PageHeader from '../../components/common/PageHeader';
import EmptyState from '../../components/common/EmptyState';

const AuditLogs = () => {
  return (
    <section>
      <PageHeader
        title="Audit Logs"
        subtitle="A record of security-relevant platform activity — logins, tenant changes and admin actions."
      />

      <EmptyState
        title="No audit log yet"
        message="Login attempts are already recorded on the backend; a searchable audit trail across tenants, users and admin actions will appear here."
      />
    </section>
  );
};

export default AuditLogs;
