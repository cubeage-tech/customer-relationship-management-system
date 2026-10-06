import { useState } from 'react';
import { Link } from 'react-router-dom';
import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { ChevronLeft, ChevronRight, Hand, Search, Ticket } from 'lucide-react';
import Button from '../common/Button';
import Toast from '../common/Toast';
import { useToast } from '../../core/hooks/useToast';
import { useDebouncedValue } from '../../core/hooks/useDebouncedValue';
import {
  TICKET_PRIORITY_LABELS,
  TICKET_PRIORITY_OPTIONS,
  TICKET_SLA_STATUS_LABELS,
  TICKET_STATUS_LABELS,
  TICKET_STATUS_OPTIONS,
} from '../../core/constants/app.constant';
import RoutePath from '../../core/constants/routes.constant';
import { QUERY_KEYS } from '../../core/query/queryKeys';
import { apiErrorMessage, apiStatus } from '../../core/utils/apiError';
import { claimTicket, listAgentTickets } from '../../core/services/serviceTicket.service';

const PAGE_SIZE = 20;

const TABS = [
  { id: 'queue', label: 'Queue', hint: 'Unassigned tickets anyone on the team can pick up.' },
  { id: 'mine', label: 'My tickets', hint: 'Tickets assigned to you.' },
];

const SLA_CLASS = {
  breached: 'text-red-600',
  at_risk: 'text-amber-600',
  on_track: 'text-slate-600',
  met: 'text-green-600',
};

const selectClass =
  'h-9 rounded-md border border-slate-200 bg-white px-3 text-sm text-slate-700 outline-none focus:border-violet-400 focus:ring-2 focus:ring-violet-100';

/**
 * Service agent's ticket workspace (Decision 4): a Queue of unassigned tickets to claim and their
 * own tickets. Server-side filters and paging; claims are optimistic and roll back on 409.
 */
const AgentTicketQueue = ({ onClaimed }) => {
  const queryClient = useQueryClient();
  const { toast, showSuccess, showError, hide } = useToast();
  const [tab, setTab] = useState('queue');
  const [status, setStatus] = useState('');
  const [priority, setPriority] = useState('');
  const [searchInput, setSearchInput] = useState('');
  const [page, setPage] = useState(0);
  const search = useDebouncedValue(searchInput.trim());

  const params = { scope: tab, status, priority, search, page, size: PAGE_SIZE };
  const queryKey = QUERY_KEYS.tickets(params);

  const ticketsQuery = useQuery({
    queryKey,
    queryFn: () => listAgentTickets(params),
    placeholderData: keepPreviousData,
  });

  const claimMutation = useMutation({
    mutationFn: (ticket) => claimTicket(ticket.id),
    onMutate: async (ticket) => {
      // Optimistic: drop the row from the queue straight away.
      await queryClient.cancelQueries({ queryKey });
      const previous = queryClient.getQueryData(queryKey);
      queryClient.setQueryData(queryKey, (current) =>
        current ? { ...current, items: current.items.filter((item) => item.id !== ticket.id), totalElements: current.totalElements - 1 } : current);
      return { previous };
    },
    onSuccess: (_, ticket) => {
      showSuccess(`"${ticket.subject}" is now yours.`);
      onClaimed?.();
    },
    onError: (error, ticket, context) => {
      if (context?.previous) queryClient.setQueryData(queryKey, context.previous);
      showError(apiStatus(error) === 409
        ? `${apiErrorMessage(error)} — it has been removed from the queue.`
        : apiErrorMessage(error, `"${ticket.subject}" could not be claimed.`));
    },
    onSettled: () => queryClient.invalidateQueries({ queryKey: ['tickets'] }),
  });

  const changeTab = (next) => {
    setTab(next);
    setPage(0);
  };
  const changeFilter = (setter) => (event) => {
    setter(event.target.value);
    setPage(0);
  };

  const data = ticketsQuery.data;
  const tickets = data?.items ?? [];
  const totalPages = data?.totalPages ?? 0;
  const filtered = Boolean(search || status || priority);
  const activeTab = TABS.find((item) => item.id === tab);

  return (
    <section className="overflow-hidden rounded-lg border border-slate-200 bg-white shadow-sm">
      <Toast toast={toast} onClose={hide} />

      <div className="flex flex-col gap-4 border-b border-slate-100 p-4 lg:flex-row lg:items-center lg:justify-between">
        <div>
          <div role="tablist" aria-label="Ticket views" className="inline-flex rounded-md border border-slate-200 bg-slate-50 p-1">
            {TABS.map((item) => (
              <button
                key={item.id}
                type="button"
                role="tab"
                id={`tab-${item.id}`}
                aria-selected={tab === item.id}
                aria-controls="agent-ticket-panel"
                onClick={() => changeTab(item.id)}
                className={`rounded px-3 py-1.5 text-sm font-medium transition ${tab === item.id ? 'bg-white text-violet-700 shadow-sm' : 'text-slate-600 hover:text-slate-900'}`}
              >
                {item.label}
              </button>
            ))}
          </div>
          <p className="mt-2 text-xs text-slate-500">{activeTab.hint}</p>
        </div>
        <div className="flex flex-wrap items-center gap-2">
          <label className="relative min-w-48 flex-1 sm:flex-none">
            <Search size={16} className="absolute left-3 top-1/2 -translate-y-1/2 text-slate-400" aria-hidden="true" />
            <span className="sr-only">Search by subject or customer</span>
            <input type="search" placeholder="Search tickets" value={searchInput}
              onChange={(event) => { setSearchInput(event.target.value); setPage(0); }}
              className="h-9 w-full rounded-md border border-slate-200 bg-white pl-9 pr-3 text-sm outline-none focus:border-violet-400 focus:ring-2 focus:ring-violet-100" />
          </label>
          <label className="sr-only" htmlFor="agent-status-filter">Filter by status</label>
          <select id="agent-status-filter" value={status} onChange={changeFilter(setStatus)} className={selectClass}>
            <option value="">All statuses</option>
            {TICKET_STATUS_OPTIONS.map((option) => <option key={option.value} value={option.value}>{option.label}</option>)}
          </select>
          <label className="sr-only" htmlFor="agent-priority-filter">Filter by priority</label>
          <select id="agent-priority-filter" value={priority} onChange={changeFilter(setPriority)} className={selectClass}>
            <option value="">All priorities</option>
            {TICKET_PRIORITY_OPTIONS.map((option) => <option key={option.value} value={option.value}>{option.label}</option>)}
          </select>
        </div>
      </div>

      <div id="agent-ticket-panel" role="tabpanel" aria-labelledby={`tab-${tab}`}>
        {ticketsQuery.isPending ? (
          <div className="space-y-3 p-5" role="status" aria-label="Loading tickets">
            {[0, 1, 2].map((row) => <div key={row} className="h-12 animate-pulse rounded-md bg-slate-100" />)}
          </div>
        ) : ticketsQuery.isError ? (
          <div className="px-5 py-12 text-center" role="alert">
            <p className="text-sm font-medium text-red-700">{apiErrorMessage(ticketsQuery.error, 'Tickets could not be loaded.')}</p>
            <Button className="mt-4" variant="outline" onClick={() => ticketsQuery.refetch()}>Try again</Button>
          </div>
        ) : tickets.length === 0 ? (
          <div className="px-5 py-14 text-center">
            <span className="mx-auto grid size-12 place-items-center rounded-xl bg-violet-50 text-violet-700"><Ticket size={22} aria-hidden="true" /></span>
            <h3 className="mt-4 text-base font-bold text-slate-900">
              {filtered ? 'No matching tickets' : tab === 'queue' ? 'The queue is empty' : 'Nothing assigned to you'}
            </h3>
            <p className="mx-auto mt-1 max-w-md text-sm text-slate-500">
              {filtered ? 'Try changing your search or filters.' : tab === 'queue' ? 'New unassigned tickets will appear here.' : 'Claim a ticket from the Queue to start working on it.'}
            </p>
          </div>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full min-w-[760px] text-left text-sm">
              <thead className="bg-slate-50 text-xs uppercase text-slate-500">
                <tr>
                  <th scope="col" className="px-5 py-3 font-semibold">Subject</th>
                  <th scope="col" className="px-4 py-3 font-semibold">Customer</th>
                  <th scope="col" className="px-4 py-3 font-semibold">Priority</th>
                  <th scope="col" className="px-4 py-3 font-semibold">Status</th>
                  <th scope="col" className="px-4 py-3 font-semibold">SLA</th>
                  {tab === 'queue' && <th scope="col" className="px-4 py-3 font-semibold"><span className="sr-only">Actions</span></th>}
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100">
                {tickets.map((ticket) => (
                  <tr key={ticket.id} className="hover:bg-slate-50/70">
                    <td className="px-5 py-3.5">
                      <Link to={RoutePath.EDIT_SERVICE_TICKET.replace(':id', ticket.id)} className="font-semibold text-slate-800 hover:text-violet-700 hover:underline">{ticket.subject}</Link>
                    </td>
                    <td className="px-4 py-3.5 text-slate-600">{ticket.customerName}</td>
                    <td className="px-4 py-3.5">
                      <span className={`inline-flex rounded-full px-2.5 py-1 text-xs font-semibold ${ticket.priority === 'critical' || ticket.priority === 'high' ? 'bg-rose-50 text-rose-700' : 'bg-slate-100 text-slate-600'}`}>
                        {TICKET_PRIORITY_LABELS[ticket.priority] || ticket.priority}
                      </span>
                    </td>
                    <td className="px-4 py-3.5"><span className="inline-flex rounded-full bg-blue-50 px-2.5 py-1 text-xs font-semibold text-blue-700">{TICKET_STATUS_LABELS[ticket.status] || ticket.status}</span></td>
                    <td className={`px-4 py-3.5 text-xs font-semibold ${SLA_CLASS[ticket.slaStatus] || 'text-slate-600'}`}>{TICKET_SLA_STATUS_LABELS[ticket.slaStatus] || ticket.slaStatus}</td>
                    {tab === 'queue' && (
                      <td className="px-4 py-3.5 text-right">
                        <Button size="sm" icon={Hand} onClick={() => claimMutation.mutate(ticket)}
                          disabled={claimMutation.isPending} aria-label={`Claim ${ticket.subject}`}>
                          Claim
                        </Button>
                      </td>
                    )}
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>

      {totalPages > 1 && (
        <nav className="flex items-center justify-between border-t border-slate-100 px-5 py-3 text-xs text-slate-500" aria-label="Ticket pages">
          <span>Page {page + 1} of {totalPages} · {data.totalElements} tickets</span>
          <div className="flex gap-2">
            <Button size="sm" variant="outline" icon={ChevronLeft} onClick={() => setPage((current) => current - 1)} disabled={page === 0}>Previous</Button>
            <Button size="sm" variant="outline" icon={ChevronRight} iconPosition="right" onClick={() => setPage((current) => current + 1)} disabled={page + 1 >= totalPages}>Next</Button>
          </div>
        </nav>
      )}
    </section>
  );
};

export default AgentTicketQueue;
