import PageHeader from '../../components/common/PageHeader';
import AuditLogTable from '../../components/audit/AuditLogTable';

/** NFR-7.2d: the audit trail across all tenants (platform admin). */
const AuditLogs = () => {
  return (
    <section>
      <PageHeader
        title="Audit Logs"
        subtitle="Changes to customer, financial and user-management records across every tenant, plus platform actions."
      />
      <AuditLogTable showTenant />
    </section>
  );
};

export default AuditLogs;
