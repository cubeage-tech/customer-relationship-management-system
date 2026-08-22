import PageHeader from '../../components/common/PageHeader';
import EmptyState from '../../components/common/EmptyState';

const PlatformSettings = () => {
  return (
    <section>
      <PageHeader
        title="System Settings"
        subtitle="Platform-wide configuration — not scoped to any single tenant."
      />

      <EmptyState
        title="No platform settings yet"
        message="Branding, email delivery, security policy and integration settings for the whole platform will be managed here."
      />
    </section>
  );
};

export default PlatformSettings;
