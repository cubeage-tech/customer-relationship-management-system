import PageHeader from '../../components/common/PageHeader';
import EmptyState from '../../components/common/EmptyState';

const RolesPermissions = () => {
  return (
    <section>
      <PageHeader
        title="Roles & Permissions"
        subtitle="The platform-wide role model every tenant is built on."
      />

      <EmptyState
        title="Role editor is coming soon"
        message="Today the 8 CRM roles and their permissions are fixed system roles. An editor for adjusting the platform default permission matrix will live here."
      />
    </section>
  );
};

export default RolesPermissions;
