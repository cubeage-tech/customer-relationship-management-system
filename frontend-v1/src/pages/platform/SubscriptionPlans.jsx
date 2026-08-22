import PageHeader from '../../components/common/PageHeader';
import EmptyState from '../../components/common/EmptyState';

const SubscriptionPlans = () => {
  return (
    <section>
      <PageHeader
        title="Subscription Plans"
        subtitle="Manage the plans tenants can subscribe to — pricing, limits and feature gates."
      />

      <EmptyState
        title="Plan management is coming soon"
        message="Starter, Business and Enterprise plans are defined on tenants today; a dedicated editor for pricing and limits will live here."
      />
    </section>
  );
};

export default SubscriptionPlans;
