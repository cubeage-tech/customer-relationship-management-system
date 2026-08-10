import PageHeader from '../../components/common/PageHeader';
import EmptyState from '../../components/common/EmptyState';
import Button from '../../components/common/Button';
import { usePermissions } from '../../core/hooks/usePermissions';
import { PERMISSIONS } from '../../core/constants/permission.constant';

const Campaigns = () => {
  const { can } = usePermissions();

  return (
    <section>
      <PageHeader
        title="Campaigns"
        subtitle="Marketing campaigns and the leads they generate."
        actions={can(PERMISSIONS.CAMPAIGNS_MANAGE) && <Button>New campaign</Button>}
      />

      <EmptyState
        title="No campaigns yet"
        message={
          can(PERMISSIONS.CAMPAIGNS_MANAGE)
            ? 'Launch a campaign to capture leads and measure conversion.'
            : 'Campaigns run by the marketing team will be listed here.'
        }
      />
    </section>
  );
};

export default Campaigns;
