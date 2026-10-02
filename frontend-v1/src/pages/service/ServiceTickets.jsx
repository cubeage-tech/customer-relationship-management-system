import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { CheckCircle2, CircleAlert, Plus, Search, Ticket, UserRound, X } from 'lucide-react';
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
  const [toast, setToast] = useState(null);

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

  useEffect(() => {
    if (!toast) return undefined;
    const timeoutId = window.setTimeout(() => setToast(null), 4000);
    return () => window.clearTimeout(timeoutId);
  }, [toast]);

  const handleChange = (e) => setForm((prev) => ({ ...prev, [e.target.name]: e.target.value }));

  const handleAddTicket = async (e) => {
    e.preventDefault();
    try {
      await createTicket(form);
      setForm(INITIAL_FORM);
      setShowForm(false);
      refresh();
      setToast({ type: 'success', message: 'Your service ticket was raised successfully.' });
    } catch (err) {
      setToast({
        type: 'error',
        message: err.response?.data?.message || 'Could not raise this ticket. Check the details and try again.',
      });
    }
  };

  return (
    <section className="space-y-5">
      {toast && (
        <div className={`fixed right-5 top-20 z-50 flex w-[min(26rem,calc(100vw-2.5rem))] items-start gap-3 rounded-lg border bg-white p-4 shadow-xl ${toast.type === 'success' ? 'border-emerald-200' : 'border-rose-200'}`} role={toast.type === 'error' ? 'alert' : 'status'} aria-live={toast.type === 'error' ? 'assertive' : 'polite'}>
          <span className={`mt-0.5 grid size-8 shrink-0 place-items-center rounded-full ${toast.type === 'success' ? 'bg-emerald-50 text-emerald-600' : 'bg-rose-50 text-rose-600'}`}>
            {toast.type === 'success' ? <CheckCircle2 size={18} aria-hidden="true" /> : <CircleAlert size={18} aria-hidden="true" />}
          </span>
          <p className="flex-1 pt-1 text-sm font-medium text-slate-800">{toast.message}</p>
          <button type="button" className="grid size-8 shrink-0 place-items-center rounded-md text-slate-400 hover:bg-slate-100 hover:text-slate-700 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary" onClick={() => setToast(null)} aria-label="Dismiss message"><X size={16} aria-hidden="true" /></button>
        </div>
      )}

      <PageHeader
        title="Service Tickets"
        subtitle={`Customer support requests — ${SCOPE_LABELS[scopeFor(MODULES.SERVICE_TICKETS)]}`}
        actions={canCreate && (
          <Button onClick={() => setShowForm((prev) => !prev)} icon={showForm ? X : Plus}>
            {showForm ? 'Close form' : 'Raise ticket'}
          </Button>
        )}
      />

      {/* FR-6.4: open tickets by SLA status */}
      {summary && (
        <div className="grid gap-3 sm:grid-cols-3">
          <div className="rounded-lg border border-emerald-200 bg-white p-4 shadow-sm">
            <p className="text-xs font-semibold uppercase text-emerald-700">On track</p>
            <p className="mt-1 text-xl font-bold text-slate-900">{summary.onTrack}</p>
            <p className="mt-1 text-xs text-slate-500">Tickets within their SLA</p>
          </div>
          <div className="rounded-lg border border-amber-200 bg-white p-4 shadow-sm">
            <p className="text-xs font-semibold uppercase text-amber-700">At risk</p>
            <p className="mt-1 text-xl font-bold text-slate-900">{summary.atRisk}</p>
            <p className="mt-1 text-xs text-slate-500">Approaching the SLA deadline</p>
          </div>
          <div className="rounded-lg border border-rose-200 bg-white p-4 shadow-sm">
            <p className="text-xs font-semibold uppercase text-rose-700">Breached</p>
            <p className="mt-1 text-xl font-bold text-slate-900">{summary.breached}</p>
            <p className="mt-1 text-xs text-slate-500">Past the SLA deadline</p>
          </div>
        </div>
      )}

      {canCreate && showForm && (
        <div className="fixed inset-0 z-40 flex items-center justify-center overflow-y-auto p-4 sm:p-6" onKeyDown={(event) => {
          if (event.key === 'Escape') setShowForm(false);
        }}>
          <button type="button" className="fixed inset-0 bg-slate-950/45 backdrop-blur-sm" onClick={() => setShowForm(false)} aria-label="Close ticket form" tabIndex={-1} />
          <div role="dialog" aria-modal="true" aria-labelledby="new-ticket-title" className="relative z-10 my-auto w-full max-w-2xl overflow-hidden rounded-lg border border-white/70 bg-white shadow-2xl">
            <form onSubmit={handleAddTicket}>
              <div className="flex items-center gap-3 border-b border-slate-100 bg-slate-50/80 px-5 py-4 sm:px-6">
                <span className="grid size-10 place-items-center rounded-lg bg-violet-100 text-violet-700"><Ticket size={19} /></span>
                <div className="min-w-0 flex-1"><h2 id="new-ticket-title" className="text-base font-bold text-slate-900">Raise a service ticket</h2><p className="mt-0.5 text-xs text-slate-500">Tell the service team what needs attention.</p></div>
                <button type="button" onClick={() => setShowForm(false)} className="grid size-9 shrink-0 place-items-center rounded-md text-slate-400 transition hover:bg-slate-200/70 hover:text-slate-700 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary" aria-label="Close ticket form"><X size={18} /></button>
              </div>
              <div className="grid gap-4 p-5 sm:grid-cols-2 sm:p-6">
                <label className="space-y-1.5 text-sm font-medium text-slate-700">Customer <span className="text-rose-500">*</span>
                  <select autoFocus name="customerId" value={form.customerId} onChange={handleChange} className="h-10 w-full rounded-md border border-slate-200 bg-white px-3 text-sm font-normal text-slate-900 outline-none focus:border-violet-400 focus:ring-2 focus:ring-violet-100" required>
                    <option value="">Select customer</option>{customers.map((c) => <option key={c.id} value={c.id}>{c.companyName}</option>)}
                  </select>
                </label>
                <label className="space-y-1.5 text-sm font-medium text-slate-700">Priority
                  <select name="priority" value={form.priority} onChange={handleChange} className="h-10 w-full rounded-md border border-slate-200 bg-white px-3 text-sm font-normal text-slate-900 outline-none focus:border-violet-400 focus:ring-2 focus:ring-violet-100">
                    {TICKET_PRIORITY_OPTIONS.map((option) => <option key={option.value} value={option.value}>{option.label}</option>)}
                  </select>
                </label>
                <label className="space-y-1.5 text-sm font-medium text-slate-700 sm:col-span-2">Issue <span className="text-rose-500">*</span>
                  <input type="text" name="subject" placeholder="e.g. Machine not working" value={form.subject} onChange={handleChange} className="h-10 w-full rounded-md border border-slate-200 bg-white px-3 text-sm font-normal text-slate-900 outline-none focus:border-violet-400 focus:ring-2 focus:ring-violet-100" required />
                </label>
                <label className="space-y-1.5 text-sm font-medium text-slate-700 sm:col-span-2">Description <span className="font-normal text-slate-400">Optional</span>
                  <textarea name="description" placeholder="Add helpful details for the service team" value={form.description} onChange={handleChange} className="w-full resize-y rounded-md border border-slate-200 bg-white px-3 py-2 text-sm font-normal text-slate-900 outline-none focus:border-violet-400 focus:ring-2 focus:ring-violet-100" rows={3} />
                </label>
                <div className="flex flex-wrap justify-end gap-2 border-t border-slate-100 pt-4 sm:col-span-2"><Button variant="outline" onClick={() => setShowForm(false)}>Cancel</Button><Button type="submit" icon={Plus}>Raise ticket</Button></div>
              </div>
            </form>
          </div>
        </div>
      )}

      {canViewList && (
        <section className="overflow-hidden rounded-lg border border-slate-200 bg-white shadow-sm">
          <div className="flex flex-col gap-4 border-b border-slate-100 p-4 sm:flex-row sm:items-center sm:justify-between">
            <div><h2 className="text-base font-bold text-slate-900">Ticket queue</h2><p className="mt-1 text-xs text-slate-500">Track support requests, urgency, and SLA health.</p></div>
            <div className="flex flex-wrap items-center gap-2">
              <label className="relative min-w-48 flex-1 sm:flex-none"><Search size={16} className="absolute left-3 top-1/2 -translate-y-1/2 text-slate-400" aria-hidden="true" /><span className="sr-only">Search by subject or customer</span><input type="search" placeholder="Search tickets" value={search} onChange={(e) => setSearch(e.target.value)} className="h-9 w-full rounded-md border border-slate-200 bg-white pl-9 pr-3 text-sm outline-none focus:border-violet-400 focus:ring-2 focus:ring-violet-100" /></label>
              <label className="sr-only" htmlFor="ticket-status-filter">Filter by status</label>
            <select
              id="ticket-status-filter"
              value={statusFilter}
              onChange={(e) => setStatusFilter(e.target.value)}
              className="h-9 rounded-md border border-slate-200 bg-white px-3 text-sm text-slate-700 outline-none focus:border-violet-400 focus:ring-2 focus:ring-violet-100"
            >
              <option value="">All statuses</option>
              {TICKET_STATUS_OPTIONS.map((option) => (
                <option key={option.value} value={option.value}>{option.label}</option>
              ))}
            </select>
              <label className="sr-only" htmlFor="ticket-priority-filter">Filter by priority</label>
            <select
              id="ticket-priority-filter"
              value={priorityFilter}
              onChange={(e) => setPriorityFilter(e.target.value)}
              className="h-9 rounded-md border border-slate-200 bg-white px-3 text-sm text-slate-700 outline-none focus:border-violet-400 focus:ring-2 focus:ring-violet-100"
            >
              <option value="">All priorities</option>
              {TICKET_PRIORITY_OPTIONS.map((option) => (
                <option key={option.value} value={option.value}>{option.label}</option>
              ))}
            </select>
            </div>
          </div>

          {loading ? (
            <div className="space-y-3 p-5" role="status" aria-label="Loading tickets">{[0, 1, 2].map((row) => <div key={row} className="h-12 animate-pulse rounded-md bg-slate-100" />)}</div>
          ) : tickets.length === 0 ? (
            <div className="px-5 py-14 text-center"><span className="mx-auto grid size-12 place-items-center rounded-xl bg-violet-50 text-violet-700"><Ticket size={22} /></span><h3 className="mt-4 text-base font-bold text-slate-900">{search || statusFilter || priorityFilter ? 'No matching tickets' : 'No service tickets yet'}</h3><p className="mx-auto mt-1 max-w-md text-sm text-slate-500">{search || statusFilter || priorityFilter ? 'Try changing your search or filters.' : canCreate ? 'Raise a ticket to start tracking a customer support request.' : 'Tickets assigned to you will appear here with their customer, priority, and SLA.'}</p>{canCreate && !search && !statusFilter && !priorityFilter && <Button className="mt-5" onClick={() => setShowForm(true)} icon={Plus}>Raise first ticket</Button>}</div>
          ) : (
            <div className="overflow-x-auto">
              <table className="w-full min-w-[800px] text-left text-sm">
                <thead className="bg-slate-50 text-xs uppercase text-slate-500"><tr><th className="px-5 py-3 font-semibold">Subject</th><th className="px-4 py-3 font-semibold">Customer</th><th className="px-4 py-3 font-semibold">Priority</th><th className="px-4 py-3 font-semibold">Status</th><th className="px-4 py-3 font-semibold">SLA</th><th className="px-4 py-3 font-semibold">Technician</th></tr></thead>
                <tbody className="divide-y divide-slate-100">
                  {tickets.map((t) => (
                    <tr key={t.id} className="transition-colors hover:bg-slate-50/70">
                      <td className="px-5 py-3.5"><div className="flex items-center gap-3"><span className="grid size-9 shrink-0 place-items-center rounded-lg bg-violet-50 text-violet-700"><Ticket size={17} /></span><Link to={RoutePath.EDIT_SERVICE_TICKET.replace(':id', t.id)} className="font-semibold text-slate-800 hover:text-violet-700 hover:underline">{t.subject}</Link></div></td>
                      <td className="px-4 py-3.5 text-slate-600">{t.customerName}</td>
                      <td className="px-4 py-3.5"><span className={`inline-flex rounded-full px-2.5 py-1 text-xs font-semibold ${t.priority === 'critical' || t.priority === 'high' ? 'bg-rose-50 text-rose-700' : 'bg-slate-100 text-slate-600'}`}>{TICKET_PRIORITY_LABELS[t.priority] || t.priority}</span></td>
                      <td className="px-4 py-3.5"><span className="inline-flex rounded-full bg-blue-50 px-2.5 py-1 text-xs font-semibold text-blue-700">{TICKET_STATUS_LABELS[t.status] || t.status}</span></td>
                      <td className={`px-4 py-3.5 text-xs font-semibold ${SLA_BADGE_CLASS[t.slaStatus] || 'text-slate-600'}`}>{TICKET_SLA_STATUS_LABELS[t.slaStatus] || t.slaStatus}</td>
                      <td className="px-4 py-3.5"><span className="inline-flex items-center gap-2 text-slate-600"><UserRound size={15} className="text-slate-400" />{t.technicianName || '—'}</span></td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
          {!loading && tickets.length > 0 && <div className="border-t border-slate-100 px-5 py-3 text-xs text-slate-500">Showing {tickets.length} {tickets.length === 1 ? 'ticket' : 'tickets'}</div>}
        </section>
      )}

      {!loading && !canViewList && (
        <EmptyState
          title="No service tickets"
          message="Tickets you raise are handled by the service team — you don't have list access to them here."
        />
      )}
    </section>
  );
};

export default ServiceTickets;
