import { useEffect, useState } from 'react';
import DashboardShell from '../../components/dashboard/DashboardShell';
import EmptyState from '../../components/common/EmptyState';
import { getTicketSummary } from '../../core/services/serviceTicket.service';

const ServiceAgentDashboard = () => {
  // The backend scopes this summary to the agent's own assigned tickets.
  const [summary, setSummary] = useState(null);

  useEffect(() => {
    getTicketSummary().then(setSummary).catch(() => setSummary(null));
  }, []);

  const stats = [
    { label: 'Assigned tickets', value: summary?.open ?? '—', hint: 'Open and assigned to you' },
    { label: 'Due today', value: summary?.dueWithin24h ?? '—', hint: 'SLA due in the next 24 hours' },
    { label: 'Resolved this week', value: summary?.resolvedThisWeek ?? '—', hint: 'By you' },
    {
      label: 'Average resolution',
      value: summary?.averageResolutionHours != null ? `${summary.averageResolutionHours.toFixed(1)} h` : '—',
      hint: 'Time to close, last 30 days',
    },
  ];

  return (
  <DashboardShell
    title="Service Dashboard"
    subtitle="Service tickets assigned to you and their resolution status."
    stats={stats}
  >
    <EmptyState
      title="No tickets assigned"
      message="Tickets assigned to you will appear here with their customer and SLA details."
    />
  </DashboardShell>
  );
};

export default ServiceAgentDashboard;