import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import PageHeader from '../../components/common/PageHeader';
import EmptyState from '../../components/common/EmptyState';
import Button from '../../components/common/Button';
import { usePermissions } from '../../core/hooks/usePermissions';
import { MODULES, PERMISSIONS, SCOPE_LABELS } from '../../core/constants/permission.constant';
import {
  TICKET_PRIORITY_LABELS,
  TICKET_PRIORITY_OPTIONS,
  TICKET_STATUS_LABELS,
  TICKET_STATUS_OPTIONS,
  TICKET_SLA_STATUS_LABELS,
} from '../../core/constants/app.constant';
import RoutePath from '../../core/constants/routes.constant';
import { listTickets, getTicketSummary, createTicket } from '../../core/services/serviceTicket.service';
import { listCustomers } from '../../core/services/customer.service';

const INITIAL_FORM = { customerId: '', subject: '', description: '', priority: TICKET_PRIORITY_OPTIONS[2].value };

const SLA_BADGE_CLASS = {
  breached: 'text-red-600 font-medium',
  at_risk: 'text-amber-600 font-medium',
  on_track: 'text-slate-600',
  met: 'text-green-600',
};

const ServiceTickets = () => {
  const { can, scopeFor } = usePermissions();
  const canCreate = can(PERMISSIONS.TICKETS_CREATE);
  const canViewList = can(PERMISSIONS.TICKETS_RESOLVE) || can(PERMISSIONS.TICKETS_EDIT) || can(PERMISSIONS.TICKETS_VIEW);

  const [tickets, setTickets] = useState([]);
  const [summary, setSummary] = useState(null);
  const [customers, setCustomers] = useState([]);
  // No async gap for the !canViewList case — derive it up front instead of
  // setState-ing inside the effect, which React flags as a cascading-render risk.
  const [loading, setLoading] = useState(canViewList);
  const [statusFilter, setStatusFilter] = useState('');
  const [priorityFilter, setPriorityFilter] = useState('');
  const [search, setSearch] = useState('');

  const [showForm, setShowForm] = useState(false);
  const [form, setForm] = useState(INITIAL_FORM);
  const [error, setError] = useState('');

  const refresh = () => {
    listTickets({ status: statusFilter, priority: priorityFilter, search })
      .then((data) => setTickets(data ?? []))
      .catch(() => setTickets([]))
      .finally(() => setLoading(false));
    getTicketSummary().then(setSummary).catch(() => setSummary(null));
  };

  useEffect(() => {
    if (canViewList) {
      refresh();
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [statusFilter, priorityFilter, search]);

  useEffect(() => {
    if (canCreate) {
      listCustomers({}).then((data) => setCustomers(data ?? [])).catch(() => setCustomers([]));
    }
  }, [canCreate]);

  const handleChange = (e) => setForm((prev) => ({ ...prev, [e.target.name]: e.target.value }));

  const handleAddTicket = async (e) => {
    e.preventDefault();
    setError('');
    try {
      await createTicket(form);
      setForm(INITIAL_FORM);
      setShowForm(false);
      refresh();
    } catch (err) {
      setError(err.response?.data?.message || 'Could not raise this ticket. Check the details and try again.');
    }
  };

  return (
    <section>
      <PageHeader
        title="Service Tickets"
        subtitle={`Customer support requests — ${SCOPE_LABELS[scopeFor(MODULES.SERVICE_TICKETS)]}`}
        actions={canCreate && (
          <Button onClick={() => setShowForm((prev) => !prev)}>
            {showForm ? 'Cancel' : 'Raise ticket'}
          </Button>
        )}
      />

      {/* FR-6.4: open tickets by SLA status */}
      {summary && (
        <div className="mb-6 grid gap-3 sm:grid-cols-3">
          <div className="bg-white border border-slate-200 rounded-lg p-3">
            <p className="text-xs text-slate-500">On track</p>
            <p className="text-lg font-semibold text-slate-900">{summary.onTrack}</p>
          </div>
          <div className="bg-white border border-amber-200 rounded-lg p-3">
            <p className="text-xs text-amber-600">At risk</p>
            <p className="text-lg font-semibold text-amber-700">{summary.atRisk}</p>
          </div>
          <div className="bg-white border border-red-200 rounded-lg p-3">
            <p className="text-xs text-red-600">Breached</p>
            <p className="text-lg font-semibold text-red-700">{summary.breached}</p>
          </div>
        </div>
      )}

      {canCreate && showForm && (
        <form onSubmit={handleAddTicket} className="mb-6 grid gap-3 sm:grid-cols-2 border border-slate-300 rounded p-4">
          <select
            name="customerId"
            value={form.customerId}
            onChange={handleChange}
            className="border border-slate-300 rounded px-2 py-1"
            required
          >
            <option value="">Select customer</option>
            {customers.map((c) => (
              <option key={c.id} value={c.id}>{c.companyName}</option>
            ))}
          </select>
          <select
            name="priority"
            value={form.priority}
            onChange={handleChange}
            className="border border-slate-300 rounded px-2 py-1"
          >
            {TICKET_PRIORITY_OPTIONS.map((option) => (
              <option key={option.value} value={option.value}>{option.label}</option>
            ))}
          </select>
          <input
            type="text"
            name="subject"
            placeholder="Issue (e.g. Machine not working)"
            value={form.subject}
            onChange={handleChange}
            className="border border-slate-300 rounded px-2 py-1 sm:col-span-2"
            required
          />
          <textarea
            name="description"
            placeholder="Description (optional)"
            value={form.description}
            onChange={handleChange}
            className="border border-slate-300 rounded px-2 py-1 sm:col-span-2"
            rows={2}
          />

          {error && <p className="form-error sm:col-span-2">{error}</p>}
          <div className="sm:col-span-2">
            <Button type="submit">Raise ticket</Button>
          </div>
        </form>
      )}

      {canViewList && (
        <div className="mb-4 flex flex-wrap gap-4">
          <input
            type="text"
            placeholder="Search by subject or customer"
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            className="border border-slate-300 rounded px-2 py-1 text-sm"
          />
          <label className="text-sm text-slate-600">
            Status
            <select
              value={statusFilter}
              onChange={(e) => setStatusFilter(e.target.value)}
              className="ml-2 border border-slate-300 rounded px-2 py-1"
            >
              <option value="">All statuses</option>
              {TICKET_STATUS_OPTIONS.map((option) => (
                <option key={option.value} value={option.value}>{option.label}</option>
              ))}
            </select>
          </label>
          <label className="text-sm text-slate-600">
            Priority
            <select
              value={priorityFilter}
              onChange={(e) => setPriorityFilter(e.target.value)}
              className="ml-2 border border-slate-300 rounded px-2 py-1"
            >
              <option value="">All priorities</option>
              {TICKET_PRIORITY_OPTIONS.map((option) => (
                <option key={option.value} value={option.value}>{option.label}</option>
              ))}
            </select>
          </label>
        </div>
      )}

      {loading ? null : !canViewList ? (
        <EmptyState
          title="No service tickets"
          message="Tickets you raise are handled by the service team — you don't have list access to them here."
        />
      ) : tickets.length === 0 ? (
        <EmptyState
          title="No service tickets"
          message={
            canCreate
              ? 'Raise your first ticket to start tracking a customer support request.'
              : 'Tickets assigned to you will appear here with their customer, priority and SLA.'
          }
        />
      ) : (
        <div className="overflow-x-auto">
          <table className="w-full text-left text-sm border-collapse">
            <thead>
              <tr className="border-b border-slate-300">
                <th className="py-2 pr-4">Subject</th>
                <th className="py-2 pr-4">Customer</th>
                <th className="py-2 pr-4">Priority</th>
                <th className="py-2 pr-4">Status</th>
                <th className="py-2 pr-4">SLA</th>
                <th className="py-2 pr-4">Technician</th>
              </tr>
            </thead>
            <tbody>
              {tickets.map((t) => (
                <tr key={t.id} className="border-b border-slate-100">
                  <td className="py-2 pr-4">
                    <Link
                      to={RoutePath.EDIT_SERVICE_TICKET.replace(':id', t.id)}
                      className="text-indigo-600 hover:underline font-medium"
                    >
                      {t.subject}
                    </Link>
                  </td>
                  <td className="py-2 pr-4">{t.customerName}</td>
                  <td className="py-2 pr-4">{TICKET_PRIORITY_LABELS[t.priority] || t.priority}</td>
                  <td className="py-2 pr-4">{TICKET_STATUS_LABELS[t.status] || t.status}</td>
                  <td className={`py-2 pr-4 ${SLA_BADGE_CLASS[t.slaStatus] || ''}`}>
                    {TICKET_SLA_STATUS_LABELS[t.slaStatus] || t.slaStatus}
                  </td>
                  <td className="py-2 pr-4">{t.technicianName || '—'}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </section>
  );
};

export default ServiceTickets;
