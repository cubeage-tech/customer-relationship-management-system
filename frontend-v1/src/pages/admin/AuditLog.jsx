import PageHeader from '../../components/common/PageHeader';
import AuditLogTable from '../../components/audit/AuditLogTable';

/** NFR-7.2d: the tenant's audit trail (admin and executive owner). */
const AuditLog = () => (
  <section>
    <PageHeader
      title="Audit Log"
      subtitle="Every change to customers, quotations, deals, commissions, payments, users and settings — who made it and when."
    />
    <AuditLogTable />
  </section>
);

export default AuditLog;
