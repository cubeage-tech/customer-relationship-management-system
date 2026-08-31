import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import PageHeader from '../../components/common/PageHeader';
import EmptyState from '../../components/common/EmptyState';
import Button from '../../components/common/Button';
import { usePermissions } from '../../core/hooks/usePermissions';
import { MODULES, PERMISSIONS, SCOPE_LABELS } from '../../core/constants/permission.constant';
import {
  INDUSTRY_OPTIONS,
  LEAD_SOURCE_LABELS,
  LEAD_SOURCE_OPTIONS,
  LEAD_STAGE_LABELS,
  LEAD_STAGE_OPTIONS,
  LEAD_STAGES,
} from '../../core/constants/app.constant';
import RoutePath from '../../core/constants/routes.constant';
import { listLeads, createLead, deleteLead, changeLeadStage } from '../../core/services/lead.service';

const INITIAL_FORM = {
  leadName: '',
  companyName: '',
  industry: INDUSTRY_OPTIONS[0].value,
  source: LEAD_SOURCE_OPTIONS[0].value,
  contactEmail: '',
  contactPhone: '',
};

const isOverdue = (lead) =>
  lead.stage !== LEAD_STAGES.CONVERTED && lead.followUpDate && new Date(lead.followUpDate) < new Date();

const Leads = () => {
  const { can, scopeFor } = usePermissions();
  const canCreate = can(PERMISSIONS.LEADS_CREATE);
  const canEdit = can(PERMISSIONS.LEADS_EDIT);
  const canDelete = can(PERMISSIONS.LEADS_DELETE);

  const [leads, setLeads] = useState([]);
  const [loading, setLoading] = useState(true);
  const [stageFilter, setStageFilter] = useState('');
  const [sourceFilter, setSourceFilter] = useState('');
  const [search, setSearch] = useState('');
  const [showForm, setShowForm] = useState(false);
  const [form, setForm] = useState(INITIAL_FORM);
  const [error, setError] = useState('');

  const refresh = () => {
    listLeads({ stage: stageFilter, source: sourceFilter, search })
      .then((data) => setLeads(data ?? []))
      .catch(() => setLeads([]))
      .finally(() => setLoading(false));
  };

  useEffect(() => {
    refresh();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [stageFilter, sourceFilter, search]);

  const handleChange = (e) => setForm((prev) => ({ ...prev, [e.target.name]: e.target.value }));

  const handleAddLead = async (e) => {
    e.preventDefault();
    setError('');
    try {
      await createLead(form);
      setForm(INITIAL_FORM);
      setShowForm(false);
      refresh();
    } catch (err) {
      setError(err.response?.data?.message || 'Could not add this lead. Check the details and try again.');
    }
  };

  const handleStageChange = async (lead, nextStage) => {
    if (nextStage === lead.stage) return;
    try {
      await changeLeadStage(lead.id, nextStage);
      refresh();
    } catch (err) {
      // A forward skip without confirmation is rejected by the backend (BR-1) —
      // offer the override rather than silently failing.
      if (err.response?.status === 400 && window.confirm('This skips pipeline stages. Move the lead anyway?')) {
        changeLeadStage(lead.id, nextStage, true).then(refresh).catch(() => {});
      }
    }
  };

  const handleDelete = async (lead) => {
    if (!window.confirm(`Delete the lead "${lead.leadName}"? This cannot be undone.`)) return;
    await deleteLead(lead.id);
    refresh();
  };

  return (
    <section>
      <PageHeader
        title="Leads"
        subtitle={`New Lead → Contacted → Meeting → Quotation → Negotiation → Converted — ${SCOPE_LABELS[scopeFor(MODULES.LEADS)]}`}
        actions={
          canCreate && (
            <Button onClick={() => setShowForm((prev) => !prev)}>
              {showForm ? 'Cancel' : 'Add lead'}
            </Button>
          )
        }
      />

      {canCreate && showForm && (
        <form
          onSubmit={handleAddLead}
          className="mb-6 grid gap-3 sm:grid-cols-2 border border-slate-300 rounded p-4"
        >
          <input
            type="text"
            name="leadName"
            placeholder="Lead / contact name"
            value={form.leadName}
            onChange={handleChange}
            className="border border-slate-300 rounded px-2 py-1"
            required
          />
          <input
            type="text"
            name="companyName"
            placeholder="Company name"
            value={form.companyName}
            onChange={handleChange}
            className="border border-slate-300 rounded px-2 py-1"
            required
          />
          <select
            name="industry"
            value={form.industry}
            onChange={handleChange}
            className="border border-slate-300 rounded px-2 py-1"
          >
            {INDUSTRY_OPTIONS.map((option) => (
              <option key={option.value} value={option.value}>
                {option.label}
              </option>
            ))}
          </select>
          <select
            name="source"
            value={form.source}
            onChange={handleChange}
            className="border border-slate-300 rounded px-2 py-1"
          >
            {LEAD_SOURCE_OPTIONS.map((option) => (
              <option key={option.value} value={option.value}>
                {option.label}
              </option>
            ))}
          </select>
          <input
            type="email"
            name="contactEmail"
            placeholder="Contact email (optional)"
            value={form.contactEmail}
            onChange={handleChange}
            className="border border-slate-300 rounded px-2 py-1"
          />
          <input
            type="text"
            name="contactPhone"
            placeholder="Contact phone (optional)"
            value={form.contactPhone}
            onChange={handleChange}
            className="border border-slate-300 rounded px-2 py-1"
          />

          {error && <p className="form-error sm:col-span-2">{error}</p>}
          <div className="sm:col-span-2">
            <Button type="submit">Add lead</Button>
          </div>
        </form>
      )}

      <div className="mb-4 flex flex-wrap gap-4">
        <input
          type="text"
          placeholder="Search by lead or company name"
          value={search}
          onChange={(e) => setSearch(e.target.value)}
          className="border border-slate-300 rounded px-2 py-1 text-sm"
        />
        <label className="text-sm text-slate-600">
          Stage
          <select
            value={stageFilter}
            onChange={(e) => setStageFilter(e.target.value)}
            className="ml-2 border border-slate-300 rounded px-2 py-1"
          >
            <option value="">All stages</option>
            {LEAD_STAGE_OPTIONS.map((option) => (
              <option key={option.value} value={option.value}>
                {option.label}
              </option>
            ))}
          </select>
        </label>
        <label className="text-sm text-slate-600">
          Source
          <select
            value={sourceFilter}
            onChange={(e) => setSourceFilter(e.target.value)}
            className="ml-2 border border-slate-300 rounded px-2 py-1"
          >
            <option value="">All sources</option>
            {LEAD_SOURCE_OPTIONS.map((option) => (
              <option key={option.value} value={option.value}>
                {option.label}
              </option>
            ))}
          </select>
        </label>
      </div>

      {loading ? null : leads.length === 0 ? (
        <EmptyState
          title="No leads yet"
          message={
            canCreate
              ? 'Add your first lead to start tracking it through the pipeline.'
              : 'Leads you have access to will be listed here.'
          }
        />
      ) : (
        <div className="overflow-x-auto">
          <table className="w-full text-left text-sm border-collapse">
            <thead>
              <tr className="border-b border-slate-300">
                <th className="py-2 pr-4">Lead</th>
                <th className="py-2 pr-4">Company</th>
                <th className="py-2 pr-4">Source</th>
                <th className="py-2 pr-4">Stage</th>
                <th className="py-2 pr-4">Owner</th>
                <th className="py-2 pr-4">Follow-up</th>
                {canDelete && <th className="py-2 pr-4"></th>}
              </tr>
            </thead>
            <tbody>
              {leads.map((lead) => (
                <tr key={lead.id} className="border-b border-slate-100">
                  <td className="py-2 pr-4">
                    <Link
                      to={RoutePath.EDIT_LEAD.replace(':id', lead.id)}
                      className="text-indigo-600 hover:underline font-medium"
                    >
                      {lead.leadName}
                    </Link>
                  </td>
                  <td className="py-2 pr-4">{lead.companyName}</td>
                  <td className="py-2 pr-4">{LEAD_SOURCE_LABELS[lead.source] || lead.source}</td>
                  <td className="py-2 pr-4">
                    {canEdit ? (
                      <select
                        value={lead.stage}
                        onChange={(e) => handleStageChange(lead, e.target.value)}
                        className="border border-slate-300 rounded px-2 py-1 text-xs"
                      >
                        {LEAD_STAGE_OPTIONS.map((option) => (
                          <option key={option.value} value={option.value}>
                            {option.label}
                          </option>
                        ))}
                      </select>
                    ) : (
                      LEAD_STAGE_LABELS[lead.stage] || lead.stage
                    )}
                  </td>
                  <td className="py-2 pr-4">{lead.ownerName || '—'}</td>
                  <td className={`py-2 pr-4 ${isOverdue(lead) ? 'text-red-600 font-medium' : ''}`}>
                    {lead.followUpDate ? new Date(lead.followUpDate).toLocaleDateString() : '—'}
                    {isOverdue(lead) && ' (overdue)'}
                  </td>
                  {canDelete && (
                    <td className="py-2 pr-4">
                      <button
                        type="button"
                        onClick={() => handleDelete(lead)}
                        className="text-xs text-red-600 hover:underline"
                      >
                        Delete
                      </button>
                    </td>
                  )}
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </section>
  );
};

export default Leads;
