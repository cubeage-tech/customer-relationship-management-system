import { useEffect, useState } from 'react';
import ScoreBadge from '../../components/ai/ScoreBadge';
import { Link, useSearchParams } from 'react-router-dom';
import { Building2, CheckCircle2, CircleAlert, Mail, Phone, Plus, Search, Target, UserRound, X } from 'lucide-react';
import PageHeader from '../../components/common/PageHeader';
import Button from '../../components/common/Button';
import { usePermissions } from '../../core/hooks/usePermissions';
import { DATA_SCOPE, MODULES, PERMISSIONS, SCOPE_LABELS } from '../../core/constants/permission.constant';
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
import { askOverrideReason, isStageSkip } from '../../core/utils/stageOverride';
import DuplicateWarning from '../../components/common/DuplicateWarning';
import { duplicateConfirmText, useDuplicateCheck } from '../../core/hooks/useDuplicateCheck';
import { listUsers } from '../../core/services/user.service';
import LeadBoard from '../../components/lead/LeadBoard';

const INITIAL_FORM = {
  leadName: '',
  companyName: '',
  industry: INDUSTRY_OPTIONS[0].value,
  source: LEAD_SOURCE_OPTIONS[0].value,
  contactEmail: '',
  contactPhone: '',
  budget: '',
};

const VIEW_KEY = 'leads.view';
const readView = () => {
  try {
    return window.localStorage.getItem(VIEW_KEY) === 'board' ? 'board' : 'table';
  } catch {
    return 'table';
  }
};

const isOverdue = (lead) =>
  lead.stage !== LEAD_STAGES.CONVERTED && lead.followUpDate && new Date(lead.followUpDate) < new Date();

/** NFR-7.1b: leads per page. */
const PAGE_SIZE = 100;

const Leads = () => {
  const { can, scopeFor } = usePermissions();
  const canCreate = can(PERMISSIONS.LEADS_CREATE);
  const canEdit = can(PERMISSIONS.LEADS_EDIT);
  const canDelete = can(PERMISSIONS.LEADS_DELETE);
  // FR-2.7: filtering by owner only makes sense for users who see more than their own leads.
  const canFilterByOwner = scopeFor(MODULES.LEADS) !== DATA_SCOPE.OWN;

  const [leads, setLeads] = useState([]);
  const [loading, setLoading] = useState(true);
  const [searchParams] = useSearchParams();
  // UI-8: drill-downs from reports and dashboard charts arrive with ?stage=
  const [stageFilter, setStageFilter] = useState(() => searchParams.get('stage') ?? '');
  const [sourceFilter, setSourceFilter] = useState(() => searchParams.get('source') ?? '');
  const [ownerFilter, setOwnerFilter] = useState('');
  // FR-AI-1.3: filter and sort by AI score.
  const [scoreFilter, setScoreFilter] = useState(() => searchParams.get('scoreLabel') ?? '');
  const [sortBy, setSortBy] = useState('');
  const [industryFilter, setIndustryFilter] = useState('');
  const [owners, setOwners] = useState([]);
  const [search, setSearch] = useState('');
  const [showForm, setShowForm] = useState(false);
  const [form, setForm] = useState(INITIAL_FORM);
  const [toast, setToast] = useState(null);
  // NAV-M03: pipeline (kanban) or table, remembered per browser.
  const [view, setView] = useState(readView);
  const changeView = (next) => {
    setView(next);
    try {
      window.localStorage.setItem(VIEW_KEY, next);
    } catch {
      // view preference is a convenience only
    }
  };
  const canSeeSourceReport = can(PERMISSIONS.REPORTS_SALES) || can(PERMISSIONS.REPORTS_CAMPAIGN);

  // NFR-7.1b: a page at a time, filtered on the server; "Show more" appends the next page.
  const [total, setTotal] = useState(0);
  const loadPage = (pageNo) =>
    listLeads({ stage: stageFilter, source: sourceFilter, industry: industryFilter, search, ownerId: ownerFilter || undefined,
      scoreLabel: scoreFilter || undefined, sort: sortBy || undefined, page: pageNo, size: PAGE_SIZE })
      .then((data) => {
        setLeads((prev) => (pageNo === 0 ? data?.items ?? [] : [...prev, ...(data?.items ?? [])]));
        setTotal(data?.totalElements ?? 0);
      });
  const refresh = () => {
    loadPage(0)
      .catch(() => setLeads([]))
      .finally(() => setLoading(false));
  };
  const showMore = () => loadPage(Math.ceil(leads.length / PAGE_SIZE)).catch(() => {});

  useEffect(() => {
    refresh();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [stageFilter, sourceFilter, industryFilter, search, ownerFilter, scoreFilter, sortBy]);

  useEffect(() => {
    if (!canFilterByOwner) return;
    listUsers()
      .then((data) => setOwners([...(data ?? [])].sort((a, b) => (a.fullName || '').localeCompare(b.fullName || ''))))
      .catch(() => setOwners([]));
  }, [canFilterByOwner]);

  useEffect(() => {
    if (!toast) return undefined;
    const timeoutId = window.setTimeout(() => setToast(null), 4000);
    return () => window.clearTimeout(timeoutId);
  }, [toast]);

  const handleChange = (e) => setForm((prev) => ({ ...prev, [e.target.name]: e.target.value }));

  const duplicates = useDuplicateCheck('lead', { companyName: form.companyName, email: form.contactEmail });

  const handleAddLead = async (e) => {
    e.preventDefault();
    if (duplicates.length > 0 && !window.confirm(duplicateConfirmText(duplicates))) return;
    try {
      await createLead({ ...form, budget: form.budget === '' ? null : Number(form.budget) });
      setForm(INITIAL_FORM);
      setShowForm(false);
      refresh();
      setToast({ type: 'success', message: `${form.leadName} was added to your leads.` });
    } catch (err) {
      setToast({
        type: 'error',
        message: err.response?.data?.message || 'Could not add this lead. Check the details and try again.',
      });
    }
  };

  const handleStageChange = async (lead, nextStage) => {
    if (nextStage === lead.stage) return;
    try {
      await changeLeadStage(lead.id, nextStage);
      refresh();
    } catch (err) {
      // A forward skip is refused by the backend (BR-1) — offer the logged override instead.
      if (!isStageSkip(err)) {
        setToast({ type: 'error', message: err.response?.data?.message || 'Could not move this lead.' });
        return;
      }
      const reason = askOverrideReason();
      if (reason !== null) {
        changeLeadStage(lead.id, nextStage, true, reason).then(refresh)
          .catch((retryErr) => setToast({ type: 'error', message: retryErr.response?.data?.message || 'Could not move this lead.' }));
      }
    }
  };

  const handleDelete = async (lead) => {
    if (!window.confirm(`Delete the lead "${lead.leadName}"? This cannot be undone.`)) return;
    await deleteLead(lead.id);
    refresh();
  };

  return (
    <section className="space-y-5">
      {toast && (
        <div
          className={`fixed right-5 top-20 z-50 flex w-[min(26rem,calc(100vw-2.5rem))] items-start gap-3 rounded-lg border bg-white p-4 shadow-xl ${
            toast.type === 'success' ? 'border-emerald-200' : 'border-rose-200'
          }`}
          role={toast.type === 'error' ? 'alert' : 'status'}
          aria-live={toast.type === 'error' ? 'assertive' : 'polite'}
        >
          <span className={`mt-0.5 grid size-8 shrink-0 place-items-center rounded-full ${toast.type === 'success' ? 'bg-emerald-50 text-emerald-600' : 'bg-rose-50 text-rose-600'}`}>
            {toast.type === 'success' ? <CheckCircle2 size={18} aria-hidden="true" /> : <CircleAlert size={18} aria-hidden="true" />}
          </span>
          <p className="flex-1 pt-1 text-sm font-medium text-slate-800">{toast.message}</p>
          <button
            type="button"
            className="grid size-8 shrink-0 place-items-center rounded-md text-slate-400 hover:bg-slate-100 hover:text-slate-700 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary"
            onClick={() => setToast(null)}
            aria-label="Dismiss message"
          >
            <X size={16} aria-hidden="true" />
          </button>
        </div>
      )}

      <PageHeader
        title="Leads"
        subtitle={`New Lead → Contacted → Meeting → Quotation → Negotiation → Converted — ${SCOPE_LABELS[scopeFor(MODULES.LEADS)]}`}
        actions={
          canCreate && (
            <Button onClick={() => setShowForm((prev) => !prev)} icon={showForm ? X : Plus}>
              {showForm ? 'Close form' : 'Add lead'}
            </Button>
          )
        }
      />

      {canCreate && showForm && (
        <div
          className="fixed inset-0 z-40 flex items-center justify-center overflow-y-auto p-4 sm:p-6"
          onKeyDown={(event) => {
            if (event.key === 'Escape') setShowForm(false);
          }}
        >
          <button
            type="button"
            className="fixed inset-0 bg-slate-950/45 backdrop-blur-sm"
            onClick={() => setShowForm(false)}
            aria-label="Close lead form"
            tabIndex={-1}
          />
          <div
            role="dialog"
            aria-modal="true"
            aria-labelledby="new-lead-title"
            className="relative z-10 my-auto w-full max-w-2xl overflow-hidden rounded-lg border border-white/70 bg-white shadow-2xl"
          >
            <form onSubmit={handleAddLead}>
              <div className="flex items-center gap-3 border-b border-slate-100 bg-slate-50/80 px-5 py-4 sm:px-6">
                <span className="grid size-10 place-items-center rounded-lg bg-primary/10 text-primary"><Target size={19} /></span>
                <div className="min-w-0 flex-1">
                  <h2 id="new-lead-title" className="text-base font-bold text-slate-900">Create a new lead</h2>
                  <p className="mt-0.5 text-xs text-slate-500">Add a contact and company to your sales pipeline.</p>
                </div>
                <button
                  type="button"
                  onClick={() => setShowForm(false)}
                  className="grid size-9 shrink-0 place-items-center rounded-md text-slate-400 transition hover:bg-slate-200/70 hover:text-slate-700 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary"
                  aria-label="Close lead form"
                >
                  <X size={18} />
                </button>
              </div>
              <div className="grid gap-4 p-5 sm:grid-cols-2 sm:p-6">
                <label className="space-y-1.5 text-sm font-medium text-slate-700">
                  Lead / contact name <span className="text-rose-500">*</span>
                  <input
                    autoFocus
                    type="text"
                    name="leadName"
                    placeholder="e.g. Jordan Lee"
                    value={form.leadName}
                    onChange={handleChange}
                    className="h-10 w-full rounded-md border border-slate-200 bg-white px-3 text-sm font-normal text-slate-900 outline-none transition focus:border-primary/50 focus:ring-2 focus:ring-primary/20"
                    required
                  />
                </label>
                <label className="space-y-1.5 text-sm font-medium text-slate-700">
                  Company name <span className="text-rose-500">*</span>
                  <span className="relative block">
                    <Building2 size={16} className="absolute left-3 top-1/2 -translate-y-1/2 text-slate-400" aria-hidden="true" />
                    <input
                      type="text"
                      name="companyName"
                      placeholder="e.g. Acme Industries"
                      value={form.companyName}
                      minLength={2}
                      onChange={handleChange}
                      className="h-10 w-full rounded-md border border-slate-200 bg-white pl-9 pr-3 text-sm font-normal text-slate-900 outline-none transition focus:border-primary/50 focus:ring-2 focus:ring-primary/20"
                      required
                    />
                  </span>
                </label>
                <label className="space-y-1.5 text-sm font-medium text-slate-700">
                  Industry
                  <select
                    name="industry"
                    value={form.industry}
                    onChange={handleChange}
                    className="h-10 w-full rounded-md border border-slate-200 bg-white px-3 text-sm font-normal text-slate-900 outline-none transition focus:border-primary/50 focus:ring-2 focus:ring-primary/20"
                  >
                    {INDUSTRY_OPTIONS.map((option) => (
                      <option key={option.value} value={option.value}>{option.label}</option>
                    ))}
                  </select>
                </label>
                <label className="space-y-1.5 text-sm font-medium text-slate-700">
                  Lead source
                  <select
                    name="source"
                    value={form.source}
                    onChange={handleChange}
                    className="h-10 w-full rounded-md border border-slate-200 bg-white px-3 text-sm font-normal text-slate-900 outline-none transition focus:border-primary/50 focus:ring-2 focus:ring-primary/20"
                  >
                    {LEAD_SOURCE_OPTIONS.map((option) => (
                      <option key={option.value} value={option.value}>{option.label}</option>
                    ))}
                  </select>
                </label>
                <label className="space-y-1.5 text-sm font-medium text-slate-700">
                  Email <span className="font-normal text-slate-400">Optional</span>
                  <span className="relative block">
                    <Mail size={16} className="absolute left-3 top-1/2 -translate-y-1/2 text-slate-400" aria-hidden="true" />
                    <input
                      type="email"
                      name="contactEmail"
                      placeholder="jordan@example.com"
                      value={form.contactEmail}
                      onChange={handleChange}
                      className="h-10 w-full rounded-md border border-slate-200 bg-white pl-9 pr-3 text-sm font-normal text-slate-900 outline-none transition focus:border-primary/50 focus:ring-2 focus:ring-primary/20"
                    />
                  </span>
                </label>
                <label className="space-y-1.5 text-sm font-medium text-slate-700">
                  Phone <span className="font-normal text-slate-400">Optional</span>
                  <span className="relative block">
                    <Phone size={16} className="absolute left-3 top-1/2 -translate-y-1/2 text-slate-400" aria-hidden="true" />
                    <input
                      type="tel"
                      name="contactPhone"
                      placeholder="+1 555 0100"
                      value={form.contactPhone}
                      onChange={handleChange}
                      className="h-10 w-full rounded-md border border-slate-200 bg-white pl-9 pr-3 text-sm font-normal text-slate-900 outline-none transition focus:border-primary/50 focus:ring-2 focus:ring-primary/20"
                    />
                  </span>
                </label>
                <label className="space-y-1.5 text-sm font-medium text-slate-700">
                  Budget <span className="font-normal text-slate-400">Optional — used by the AI score</span>
                  <input
                    type="number"
                    name="budget"
                    min="0"
                    step="0.01"
                    placeholder="e.g. 250000"
                    value={form.budget}
                    onChange={handleChange}
                    className="h-10 w-full rounded-md border border-slate-200 bg-white px-3 text-sm font-normal text-slate-900 outline-none transition focus:border-primary/50 focus:ring-2 focus:ring-primary/20"
                  />
                </label>
                <div className="sm:col-span-2"><DuplicateWarning matches={duplicates} /></div>
                <div className="flex flex-wrap justify-end gap-2 border-t border-slate-100 pt-4 sm:col-span-2">
                  <Button variant="outline" onClick={() => setShowForm(false)}>Cancel</Button>
                  <Button type="submit" icon={Plus}>Add lead</Button>
                </div>
              </div>
            </form>
          </div>
        </div>
      )}

      <section className="overflow-hidden rounded-lg border border-slate-200 bg-white shadow-sm">
        <div className="flex flex-col gap-4 border-b border-slate-100 p-4 sm:flex-row sm:items-center sm:justify-between">
          <div>
            <h2 className="text-base font-bold text-slate-900">Lead pipeline</h2>
            <p className="mt-1 text-xs text-slate-500">
              Find and track prospects through each stage.
              {canSeeSourceReport && (
                <> <Link to="/reports?tab=lead-sources" className="text-primary hover:underline">Lead source report</Link></>
              )}
            </p>
          </div>
          <div className="flex flex-wrap items-center gap-2">
            <div className="inline-flex rounded-md border border-slate-200 p-0.5" role="group" aria-label="View">
              {[['board', 'Pipeline'], ['table', 'Table']].map(([value, label]) => (
                <button
                  key={value}
                  type="button"
                  onClick={() => changeView(value)}
                  aria-pressed={view === value}
                  className={`h-8 rounded px-3 text-xs font-semibold ${view === value ? 'bg-primary text-white' : 'text-slate-600 hover:bg-slate-100'}`}
                >
                  {label}
                </button>
              ))}
            </div>
            <label className="relative min-w-48 flex-1 sm:flex-none">
              <Search size={16} className="absolute left-3 top-1/2 -translate-y-1/2 text-slate-400" aria-hidden="true" />
              <span className="sr-only">Search by lead or company name</span>
              <input
                type="search"
                placeholder="Search leads"
                value={search}
                onChange={(e) => setSearch(e.target.value)}
                className="h-9 w-full rounded-md border border-slate-200 bg-white pl-9 pr-3 text-sm outline-none focus:border-primary/50 focus:ring-2 focus:ring-primary/20"
              />
            </label>
            <label className="sr-only" htmlFor="lead-stage-filter">Filter by stage</label>
          <select
            id="lead-stage-filter"
            value={stageFilter}
            onChange={(e) => setStageFilter(e.target.value)}
            className="h-9 rounded-md border border-slate-200 bg-white px-3 text-sm text-slate-700 outline-none focus:border-primary/50 focus:ring-2 focus:ring-primary/20"
          >
            <option value="">All stages</option>
            {LEAD_STAGE_OPTIONS.map((option) => (
              <option key={option.value} value={option.value}>
                {option.label}
              </option>
            ))}
          </select>
            <label className="sr-only" htmlFor="lead-source-filter">Filter by source</label>
          <select
            id="lead-source-filter"
            value={sourceFilter}
            onChange={(e) => setSourceFilter(e.target.value)}
            className="h-9 rounded-md border border-slate-200 bg-white px-3 text-sm text-slate-700 outline-none focus:border-primary/50 focus:ring-2 focus:ring-primary/20"
          >
            <option value="">All sources</option>
            {LEAD_SOURCE_OPTIONS.map((option) => (
              <option key={option.value} value={option.value}>
                {option.label}
              </option>
            ))}
          </select>
            <label className="sr-only" htmlFor="lead-industry-filter">Filter by industry</label>
          <select
            id="lead-industry-filter"
            value={industryFilter}
            onChange={(e) => setIndustryFilter(e.target.value)}
            className="h-9 rounded-md border border-slate-200 bg-white px-3 text-sm text-slate-700 outline-none focus:border-primary/50 focus:ring-2 focus:ring-primary/20"
          >
            <option value="">All industries</option>
            {INDUSTRY_OPTIONS.map((option) => (
              <option key={option.value} value={option.value}>
                {option.label}
              </option>
            ))}
          </select>
          <label className="sr-only" htmlFor="lead-score-filter">Filter by AI score</label>
          <select
            id="lead-score-filter"
            value={scoreFilter}
            onChange={(e) => setScoreFilter(e.target.value)}
            className="h-9 rounded-md border border-slate-200 bg-white px-3 text-sm text-slate-700 outline-none focus:border-primary/50 focus:ring-2 focus:ring-primary/20"
          >
            <option value="">Any AI score</option>
            <option value="High">High (70+)</option>
            <option value="Medium">Medium (40–69)</option>
            <option value="Low">Low (under 40)</option>
          </select>
          <label className="sr-only" htmlFor="lead-sort">Sort leads</label>
          <select
            id="lead-sort"
            value={sortBy}
            onChange={(e) => setSortBy(e.target.value)}
            className="h-9 rounded-md border border-slate-200 bg-white px-3 text-sm text-slate-700 outline-none focus:border-primary/50 focus:ring-2 focus:ring-primary/20"
          >
            <option value="">Newest first</option>
            <option value="aiScore,desc">AI score: high to low</option>
            <option value="aiScore,asc">AI score: low to high</option>
          </select>
          {canFilterByOwner && (
            <>
              <label className="sr-only" htmlFor="lead-owner-filter">Filter by owner</label>
              <select
                id="lead-owner-filter"
                value={ownerFilter}
                onChange={(e) => setOwnerFilter(e.target.value)}
                className="h-9 rounded-md border border-slate-200 bg-white px-3 text-sm text-slate-700 outline-none focus:border-primary/50 focus:ring-2 focus:ring-primary/20"
              >
                <option value="">All owners</option>
                {owners.map((owner) => (
                  <option key={owner.id} value={owner.id}>
                    {owner.fullName}
                  </option>
                ))}
              </select>
            </>
          )}
          </div>
        </div>

        {loading ? (
          <div className="space-y-3 p-5" role="status" aria-label="Loading leads">
            {[0, 1, 2].map((row) => <div key={row} className="h-12 animate-pulse rounded-md bg-slate-100" />)}
          </div>
        ) : leads.length === 0 ? (
          <div className="px-5 py-14 text-center">
            <span className="mx-auto grid size-12 place-items-center rounded-xl bg-primary/5 text-primary"><Target size={22} /></span>
            <h3 className="mt-4 text-base font-bold text-slate-900">{search || stageFilter || sourceFilter || industryFilter || ownerFilter ? 'No matching leads' : 'No leads yet'}</h3>
            <p className="mx-auto mt-1 max-w-md text-sm text-slate-500">
              {search || stageFilter || sourceFilter || industryFilter || ownerFilter
                ? 'Try changing your search or filters to see more prospects.'
                : canCreate
                  ? 'Add your first lead to start tracking it through the pipeline.'
                  : 'Leads you have access to will be listed here.'}
            </p>
            {canCreate && !search && !stageFilter && !sourceFilter && !industryFilter && !ownerFilter && (
              <Button className="mt-5" onClick={() => setShowForm(true)} icon={Plus}>Add first lead</Button>
            )}
          </div>
        ) : view === 'board' ? (
          <LeadBoard
            leads={leads}
            stages={stageFilter ? LEAD_STAGE_OPTIONS.filter((o) => o.value === stageFilter) : LEAD_STAGE_OPTIONS}
            canEdit={canEdit}
            onStageChange={handleStageChange}
            isOverdue={isOverdue}
          />
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full min-w-[800px] text-left text-sm">
              <thead className="bg-slate-50 text-xs uppercase text-slate-500">
                <tr>
                  <th className="px-5 py-3 font-semibold">Lead</th>
                  <th className="px-4 py-3 font-semibold">Company</th>
                  <th className="px-4 py-3 font-semibold">Source</th>
                  <th className="px-4 py-3 font-semibold">Stage</th>
                  <th className="px-4 py-3 font-semibold">AI score</th>
                  <th className="px-4 py-3 font-semibold">Owner</th>
                  <th className="px-4 py-3 font-semibold">Follow-up</th>
                  {canDelete && <th className="px-4 py-3" aria-label="Actions" />}
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100">
                {leads.map((lead) => (
                  <tr key={lead.id} className="transition-colors hover:bg-slate-50/70">
                    <td className="px-5 py-3.5">
                      <div className="flex items-center gap-3">
                        <span className="grid size-9 shrink-0 place-items-center rounded-lg bg-primary/5 text-primary"><UserRound size={17} /></span>
                        <Link
                          to={RoutePath.EDIT_LEAD.replace(':id', lead.id)}
                          className="font-semibold text-slate-800 hover:text-primary/80 hover:underline"
                        >
                          {lead.leadName}
                        </Link>
                      </div>
                    </td>
                    <td className="px-4 py-3.5 text-slate-600">{lead.companyName}</td>
                    <td className="px-4 py-3.5 text-slate-600">{LEAD_SOURCE_LABELS[lead.source] || lead.source}</td>
                    <td className="px-4 py-3.5">
                      {canEdit ? (
                        <select
                          value={lead.stage}
                          onChange={(e) => handleStageChange(lead, e.target.value)}
                          className="h-8 rounded-md border border-slate-200 bg-white px-2 text-xs font-medium text-slate-700 outline-none focus:border-primary/50 focus:ring-2 focus:ring-primary/20"
                        >
                          {LEAD_STAGE_OPTIONS.map((option) => (
                            <option key={option.value} value={option.value}>{option.label}</option>
                          ))}
                        </select>
                      ) : (
                        <span className="inline-flex rounded-full bg-primary/5 px-2.5 py-1 text-xs font-semibold text-primary">
                          {LEAD_STAGE_LABELS[lead.stage] || lead.stage}
                        </span>
                      )}
                    </td>
                    <td className="px-4 py-3.5"><ScoreBadge value={lead.aiScore} label={lead.aiScoreLabel} /></td>
                    <td className="px-4 py-3.5 text-slate-600">{lead.ownerName || '—'}</td>
                    <td className={`px-4 py-3.5 ${isOverdue(lead) ? 'font-semibold text-rose-600' : 'text-slate-600'}`}>
                      {lead.followUpDate ? new Date(lead.followUpDate).toLocaleDateString() : '—'}
                      {isOverdue(lead) && ' · Overdue'}
                    </td>
                    {canDelete && (
                      <td className="px-4 py-3.5 text-right">
                        <button
                          type="button"
                          onClick={() => handleDelete(lead)}
                          className="rounded-md px-2.5 py-1.5 text-xs font-semibold text-rose-600 transition hover:bg-rose-50 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary"
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
        {!loading && leads.length > 0 && (
          <div className="border-t border-slate-100 px-5 py-3 text-xs text-slate-500">
            Showing {leads.length} of {total} {total === 1 ? 'lead' : 'leads'}
            {leads.length < total && (
              <button type="button" onClick={showMore} className="ml-3 font-medium text-primary hover:underline">Show more</button>
            )}
          </div>
        )}
      </section>
    </section>
  );
};

export default Leads;
