import PageHeader from '../../components/common/PageHeader';
import EmptyState from '../../components/common/EmptyState';
import IntegrationHealth from '../../components/platform/IntegrationHealth';
import IntegrationCallLog from '../../components/platform/IntegrationCallLog';

const PlatformSettings = () => {
  return (
    <section>
      <PageHeader
        title="System Settings"
        subtitle="Platform-wide configuration — not scoped to any single tenant."
      />

      {/* INT-8: integrations that need re-authorising */}
      <IntegrationHealth />

      {/* INT-9: every outbound call, without payloads */}
      <IntegrationCallLog />

      <EmptyState
        title="No other platform settings yet"
        message="Branding, email delivery and security policy for the whole platform will be managed here."
      />
    </section>
  );
};

export default PlatformSettings;
