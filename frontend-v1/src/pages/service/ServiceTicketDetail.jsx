import { useEffect, useState } from 'react';
import { useParams, Link } from 'react-router-dom';
import PageHeader from '../../components/common/PageHeader';
import EmptyState from '../../components/common/EmptyState';
import Button from '../../components/common/Button';
import { usePermissions } from '../../core/hooks/usePermissions';
import { PERMISSIONS } from '../../core/constants/permission.constant';
import {
  TICKET_PRIORITY_LABELS,
  TICKET_STATUS_OPTIONS,
  TICKET_SLA_STATUS_LABELS,
  TICKET_STATUSES,
  USER_ROLES,
} from '../../core/constants/app.constant';
import RoutePath from '../../core/constants/routes.constant';
import {
  getTicket,
  assignTicket,
  changeTicketStatus,
  recordTicketFeedback,
} from '../../core/services/serviceTicket.service';
import { listUsers } from '../../core/services/user.service';

const SLA_BADGE_CLASS = {
  breached: 'text-red-600 font-medium',
  at_risk: 'text-amber-600 font-medium',
  on_track: 'text-slate-600',
  met: 'text-green-600',
};

const ServiceTicketDetail = () => {
  const { id } = useParams();
  const { can } = usePermissions();
  const canEdit = can(PERMISSIONS.TICKETS_EDIT) || can(PERMISSIONS.TICKETS_RESOLVE);

  const [ticket, setTicket] = useState(null);
  const [loading, setLoading] = useState(true);
  const [notFound, setNotFound] = useState(false);
  const [technicians, setTechnicians] = useState([]);
  const [actionError, setActionError] = useState('');
  const [feedbackForm, setFeedbackForm] = useState({ score: 5, comment: '' });

  const refresh = () => {
    getTicket(id)
      .then(setTicket)
      .catch(() => setNotFound(true))
      .finally(() => setLoading(false));
  };

  useEffect(() => {
    refresh();
    if (canEdit) {
      listUsers()
        .then((data) => setTechnicians((data ?? []).filter((u) => u.role === USER_ROLES.SERVICE_AGENT)))
        .catch(() => setTechnicians([]));
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [id]);

  const handleAssign = async (e) => {
    const technicianId = e.target.value;
    if (!technicianId) return;
    const saved = await assignTicket(id, technicianId);
    setTicket(saved);
  };

  const handleStatusChange = async (status) => {
    if (status === ticket.status) return;
    setActionError('');
    try {
      const saved = await changeTicketStatus(id, status);
      setTicket(saved);
    } catch (err) {
      setActionError(err.response?.data?.message || 'Could not update the status.');
    }
  };

  const handleFeedbackSubmit = async (e) => {
    e.preventDefault();
    setActionError('');
    try {
      const saved = await recordTicketFeedback(id, Number(feedbackForm.score), feedbackForm.comment);
      setTicket(saved);
    } catch (err) {
      setActionError(err.response?.data?.message || 'Could not record feedback.');
    }
  };

  if (loading) return null;

  if (notFound) {
    return (
      <EmptyState
        title="Service ticket not found"
        message="This ticket may have been removed, or you may not have access to it."
        action={<Link to={RoutePath.SERVICE_TICKETS} className="text-indigo-600 hover:underline">Back to tickets</Link>}
      />
    );
  }

  const canRecordFeedback =
    canEdit && (ticket.status === TICKET_STATUSES.RESOLVED || ticket.status === TICKET_STATUSES.CLOSED);

  return (
    <section>
      <PageHeader
        title={ticket.subject}
        subtitle={`${ticket.customerName} · ${TICKET_PRIORITY_LABELS[ticket.priority] || ticket.priority} priority`}
        actions={
          <Link to={RoutePath.SERVICE_TICKETS} className="text-sm text-slate-500 hover:underline">
            Back to tickets
          </Link>
        }
      />

      {/* ---------------- Status & SLA ---------------- */}
      <div className="bg-white border border-slate-200 rounded-lg p-5 mb-6">
        <div className="flex flex-wrap items-center gap-6 text-sm mb-4">
          <div>
            <p className="text-slate-500">SLA</p>
            <p className={SLA_BADGE_CLASS[ticket.slaStatus] || ''}>
              {TICKET_SLA_STATUS_LABELS[ticket.slaStatus] || ticket.slaStatus} — due {new Date(ticket.slaDueAt).toLocaleString()}
            </p>
          </div>
          {ticket.resolvedAt && (
            <div>
              <p className="text-slate-500">Resolved at</p>
              <p>{new Date(ticket.resolvedAt).toLocaleString()}</p>
            </div>
          )}
        </div>

        <div className="flex flex-wrap items-center gap-2">
          {TICKET_STATUS_OPTIONS.map((option) => (
            <button
              key={option.value}
              type="button"
              disabled={!canEdit}
              onClick={() => handleStatusChange(option.value)}
              className={`px-3 py-1.5 rounded-full text-xs font-medium border ${
                ticket.status === option.value
                  ? 'bg-indigo-600 text-white border-indigo-600'
                  : 'bg-white text-slate-600 border-slate-300 hover:border-indigo-400'
              } ${!canEdit ? 'cursor-not-allowed opacity-60' : ''}`}
            >
              {option.label}
            </button>
          ))}
        </div>
        {actionError && <p className="form-error mt-2">{actionError}</p>}
      </div>

      {/* ---------------- Assignment ---------------- */}
      {canEdit && (
        <div className="bg-white border border-slate-200 rounded-lg p-5 mb-6">
          <h2 className="font-semibold text-slate-900 mb-3">Assigned technician</h2>
          <select
            value={ticket.technicianId || ''}
            onChange={handleAssign}
            className="border border-slate-300 rounded px-2 py-1 text-sm"
          >
            <option value="" disabled>Select a technician…</option>
            {technicians.map((tech) => (
              <option key={tech.id} value={tech.id}>{tech.fullName}</option>
            ))}
          </select>
          {ticket.technicianName && !technicians.some((t) => t.id === ticket.technicianId) && (
            <p className="text-xs text-slate-500 mt-1">Currently: {ticket.technicianName}</p>
          )}
        </div>
      )}

      {/* ---------------- Details ---------------- */}
      <div className="bg-white border border-slate-200 rounded-lg p-5 mb-6">
        <h2 className="font-semibold text-slate-900 mb-3">Details</h2>
        <p className="text-sm text-slate-700 whitespace-pre-wrap">{ticket.description || 'No description provided.'}</p>
      </div>

      {/* ---------------- Feedback ---------------- */}
      <div className="bg-white border border-slate-200 rounded-lg p-5">
        <h2 className="font-semibold text-slate-900 mb-3">Customer feedback</h2>
        {ticket.feedbackScore ? (
          <div className="text-sm">
            <p className="font-medium">{ticket.feedbackScore} / 5</p>
            {ticket.feedbackComment && <p className="text-slate-600 mt-1">{ticket.feedbackComment}</p>}
          </div>
        ) : canRecordFeedback ? (
          <form onSubmit={handleFeedbackSubmit} className="flex flex-wrap items-end gap-3">
            <label className="text-sm text-slate-600">
              Score
              <select
                value={feedbackForm.score}
                onChange={(e) => setFeedbackForm((prev) => ({ ...prev, score: e.target.value }))}
                className="ml-2 border border-slate-300 rounded px-2 py-1"
              >
                {[5, 4, 3, 2, 1].map((n) => (
                  <option key={n} value={n}>{n}</option>
                ))}
              </select>
            </label>
            <input
              type="text"
              placeholder="Comment (optional)"
              value={feedbackForm.comment}
              onChange={(e) => setFeedbackForm((prev) => ({ ...prev, comment: e.target.value }))}
              className="border border-slate-300 rounded px-2 py-1 text-sm flex-1 min-w-[200px]"
            />
            <Button type="submit">Record feedback</Button>
          </form>
        ) : (
          <p className="text-sm text-slate-500">
            {ticket.status === TICKET_STATUSES.RESOLVED || ticket.status === TICKET_STATUSES.CLOSED
              ? 'No feedback recorded yet.'
              : 'Feedback can be recorded once the ticket is resolved.'}
          </p>
        )}
      </div>
    </section>
  );
};

export default ServiceTicketDetail;
