import PageHeader from '../../components/common/PageHeader';
import EmptyState from '../../components/common/EmptyState';

const HelpCenter = () => {
  return (
    <section>
      <PageHeader
        title="Help Center"
        subtitle="Guides and answers for running the SmartCRM AI platform."
      />

      <EmptyState
        title="Help articles are coming soon"
        message="In the meantime, reach out from Contact us and the team will get back to you directly."
      />
    </section>
  );
};

export default HelpCenter;
