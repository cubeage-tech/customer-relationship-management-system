import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { Building2, CalendarDays, CheckCircle2, CircleAlert, CircleDollarSign, Plus, Search, Target, Trash2, UserRound, X } from 'lucide-react';
import PageHeader from '../../components/common/PageHeader';
import Button from '../../components/common/Button';
import { usePermissions } from '../../core/hooks/usePermissions';
import { MODULES, PERMISSIONS, SCOPE_LABELS } from '../../core/constants/permission.constant';
import { OPPORTUNITY_STAGE_LABELS, OPPORTUNITY_STAGE_OPTIONS, OPPORTUNITY_STAGES } from '../../core/constants/app.constant';
import RoutePath from '../../core/constants/routes.constant';
import {
  listOpportunities,
  getOpportunitySummary,
  createOpportunity,
  deleteOpportunity,
  changeOpportunityStage,
} from '../../core/services/opportunity.service';
import { listCustomers } from '../../core/services/customer.service';

const formatCurrency = (value) => `₹${Number(value ?? 0).toLocaleString('en-IN')}`;

const INITIAL_FORM = { customerId: '', productService: '', dealValue: '', expectedClosingDate: '' };

const Opportunities = () => {
  const { can, scopeFor } = usePermissions();
  const canCreate = can(PERMISSIONS.OPPORTUNITIES_CREATE);
  const canEdit = can(PERMISSIONS.OPPORTUNITIES_EDIT);
  const canDelete = can(PERMISSIONS.OPPORTUNITIES_DELETE);

  const [opportunities, setOpportunities] = useState([]);
  const [summary, setSummary] = useState([]);
  const [customers, setCustomers] = useState([]);
  const [loading, setLoading] = useState(true);
  const [stageFilter, setStageFilter] = useState('');
  const [search, setSearch] = useState('');
  const [showForm, setShowForm] = useState(false);
  const [form, setForm] = useState(INITIAL_FORM);
  const [toast, setToast] = useState(null);

  const refresh = () => {
    listOpportunities({ stage: stageFilter, search })
      .then((data) => setOpportunities(data ?? []))
      .catch(() => setOpportunities([]))
      .finally(() => setLoading(false));
    getOpportunitySummary().then((data) => setSummary(data ?? [])).catch(() => setSummary([]));
  };

  useEffect(() => {
    refresh();
    if (canCreate) {
      listCustomers({}).then((data) => setCustomers(data ?? [])).catch(() => setCustomers([]));
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [stageFilter, search]);

  useEffect(() => {
    if (!toast) return undefined;
    const timeoutId = window.setTimeout(() => setToast(null), 4000);
    return () => window.clearTimeout(timeoutId);
  }, [toast]);

  const handleChange = (e) => setForm((prev) => ({ ...prev, [e.target.name]: e.target.value }));

  const handleAddOpportunity = async (e) => {
    e.preventDefault();
    try {
      await createOpportunity(form);
      setForm(INITIAL_FORM);
      setShowForm(false);
      refresh();
      setToast({ type: 'success', message: 'Opportunity added to your sales pipeline.' });
    } catch (err) {
      setToast({
        type: 'error',
        message: err.response?.data?.message || 'Could not add this opportunity. Check the details and try again.',
      });
    }
  };

  const handleStageChange = async (opportunity, nextStage) => {
    if (nextStage === opportunity.stage) return;
    if (nextStage === OPPORTUNITY_STAGES.LOST) {
      const reason = window.prompt('Why was this opportunity lost?');
      if (!reason) return; // FR-3.4: a loss reason is required — backend rejects an empty one anyway.
      await changeOpportunityStage(opportunity.id, nextStage, reason);
    } else {
      await changeOpportunityStage(opportunity.id, nextStage);
    }
    refresh();
  };

  const handleDelete = async (opportunity) => {
    if (!window.confirm(`Delete this opportunity for ${opportunity.customerName}? This cannot be undone.`)) return;
    await deleteOpportunity(opportunity.id);
    refresh();
  };

  return (
    <section className="space-y-5">
      {toast && (
        <div
          className={`fixed right-5 top-20 z-50 flex w-[min(26rem,calc(100vw-2.5rem))] items-start gap-3 rounded-lg border bg-white p-4 shadow-xl ${toast.type === 'success' ? 'border-emerald-200' : 'border-rose-200'}`}
          role={toast.type === 'error' ? 'alert' : 'status'}
          aria-live={toast.type === 'error' ? 'assertive' : 'polite'}
        >
          <span className={`mt-0.5 grid size-8 shrink-0 place-items-center rounded-full ${toast.type === 'success' ? 'bg-emerald-50 text-emerald-600' : 'bg-rose-50 text-rose-600'}`}>
            {toast.type === 'success' ? <CheckCircle2 size={18} aria-hidden="true" /> : <CircleAlert size={18} aria-hidden="true" />}
          </span>
          <p className="flex-1 pt-1 text-sm font-medium text-slate-800">{toast.message}</p>
          <button type="button" className="grid size-8 shrink-0 place-items-center rounded-md text-slate-400 hover:bg-slate-100 hover:text-slate-700 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary" onClick={() => setToast(null)} aria-label="Dismiss message">
            <X size={16} aria-hidden="true" />
          </button>
        </div>
      )}

      <PageHeader
        title="Opportunities"
        subtitle={`Deals in the sales pipeline — ${SCOPE_LABELS[scopeFor(MODULES.OPPORTUNITIES)]}`}
        actions={
          canCreate && (
            <Button onClick={() => setShowForm((prev) => !prev)} icon={showForm ? X : Plus}>
              {showForm ? 'Close form' : 'Add opportunity'}
            </Button>
          )
        }
      />

      {/* FR-3.3: cumulative deal value per stage */}
      {summary.length > 0 && (
        <div className="grid gap-3 sm:grid-cols-2 xl:grid-cols-5">
          {summary.map((s) => (
            <div key={s.stage} className="rounded-lg border border-slate-200 bg-white p-4 shadow-sm">
              <div className="flex items-center justify-between gap-3">
                <p className="text-xs font-semibold uppercase text-slate-500">{OPPORTUNITY_STAGE_LABELS[s.stage] || s.stage}</p>
                <span className="grid size-8 place-items-center rounded-md bg-violet-50 text-violet-700"><CircleDollarSign size={17} /></span>
              </div>
              <p className="mt-2 text-xl font-bold text-slate-900">{formatCurrency(s.totalValue)}</p>
              <p className="mt-1 text-xs text-slate-500">{s.count} deal{s.count === 1 ? '' : 's'}</p>
            </div>
          ))}
        </div>
      )}

      {canCreate && showForm && (
        <div className="fixed inset-0 z-40 flex items-center justify-center overflow-y-auto p-4 sm:p-6" onKeyDown={(event) => {
          if (event.key === 'Escape') setShowForm(false);
        }}>
          <button type="button" className="fixed inset-0 bg-slate-950/45 backdrop-blur-sm" onClick={() => setShowForm(false)} aria-label="Close opportunity form" tabIndex={-1} />
          <div role="dialog" aria-modal="true" aria-labelledby="new-opportunity-title" className="relative z-10 my-auto w-full max-w-2xl overflow-hidden rounded-lg border border-white/70 bg-white shadow-2xl">
            <form onSubmit={handleAddOpportunity}>
              <div className="flex items-center gap-3 border-b border-slate-100 bg-slate-50/80 px-5 py-4 sm:px-6">
                <span className="grid size-10 place-items-center rounded-lg bg-violet-100 text-violet-700"><Target size={19} /></span>
                <div className="min-w-0 flex-1">
                  <h2 id="new-opportunity-title" className="text-base font-bold text-slate-900">Create an opportunity</h2>
                  <p className="mt-0.5 text-xs text-slate-500">Add a deal to your sales pipeline.</p>
                </div>
                <button type="button" onClick={() => setShowForm(false)} className="grid size-9 shrink-0 place-items-center rounded-md text-slate-400 transition hover:bg-slate-200/70 hover:text-slate-700 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary" aria-label="Close opportunity form"><X size={18} /></button>
              </div>
              <div className="grid gap-4 p-5 sm:grid-cols-2 sm:p-6">
                <label className="space-y-1.5 text-sm font-medium text-slate-700">
                  Customer <span className="text-rose-500">*</span>
                  <select name="customerId" value={form.customerId} onChange={handleChange} className="h-10 w-full rounded-md border border-slate-200 bg-white px-3 text-sm font-normal text-slate-900 outline-none focus:border-violet-400 focus:ring-2 focus:ring-violet-100" required>
                    <option value="">Select customer</option>
                    {customers.map((c) => <option key={c.id} value={c.id}>{c.companyName}</option>)}
                  </select>
                </label>
                <label className="space-y-1.5 text-sm font-medium text-slate-700">
                  Product / service
                  <span className="relative block">
                    <Building2 size={16} className="absolute left-3 top-1/2 -translate-y-1/2 text-slate-400" aria-hidden="true" />
                    <input type="text" name="productService" placeholder="e.g. Annual software license" value={form.productService} onChange={handleChange} className="h-10 w-full rounded-md border border-slate-200 bg-white pl-9 pr-3 text-sm font-normal text-slate-900 outline-none focus:border-violet-400 focus:ring-2 focus:ring-violet-100" />
                  </span>
                </label>
                <label className="space-y-1.5 text-sm font-medium text-slate-700">
                  Deal value <span className="text-rose-500">*</span>
                  <span className="relative block">
                    <CircleDollarSign size={16} className="absolute left-3 top-1/2 -translate-y-1/2 text-slate-400" aria-hidden="true" />
                    <input type="number" name="dealValue" placeholder="0.00" value={form.dealValue} onChange={handleChange} min="0" step="0.01" className="h-10 w-full rounded-md border border-slate-200 bg-white pl-9 pr-3 text-sm font-normal text-slate-900 outline-none focus:border-violet-400 focus:ring-2 focus:ring-violet-100" required />
                  </span>
                </label>
                <label className="space-y-1.5 text-sm font-medium text-slate-700">
                  Expected close date
                  <span className="relative block">
                    <CalendarDays size={16} className="absolute left-3 top-1/2 -translate-y-1/2 text-slate-400" aria-hidden="true" />
                    <input type="date" name="expectedClosingDate" value={form.expectedClosingDate} onChange={handleChange} className="h-10 w-full rounded-md border border-slate-200 bg-white pl-9 pr-3 text-sm font-normal text-slate-900 outline-none focus:border-violet-400 focus:ring-2 focus:ring-violet-100" />
                  </span>
                </label>
                <div className="flex flex-wrap justify-end gap-2 border-t border-slate-100 pt-4 sm:col-span-2">
                  <Button variant="outline" onClick={() => setShowForm(false)}>Cancel</Button>
                  <Button type="submit" icon={Plus}>Add opportunity</Button>
                </div>
              </div>
            </form>
          </div>
        </div>
      )}

      <section className="overflow-hidden rounded-lg border border-slate-200 bg-white shadow-sm">
        <div className="flex flex-col gap-4 border-b border-slate-100 p-4 sm:flex-row sm:items-center sm:justify-between">
          <div>
            <h2 className="text-base font-bold text-slate-900">Opportunity pipeline</h2>
            <p className="mt-1 text-xs text-slate-500">Monitor deal value, close dates, and stage progression.</p>
          </div>
          <div className="flex flex-wrap items-center gap-2">
            <label className="relative min-w-48 flex-1 sm:flex-none">
              <Search size={16} className="absolute left-3 top-1/2 -translate-y-1/2 text-slate-400" aria-hidden="true" />
              <span className="sr-only">Search by customer or product</span>
              <input type="search" placeholder="Search opportunities" value={search} onChange={(e) => setSearch(e.target.value)} className="h-9 w-full rounded-md border border-slate-200 bg-white pl-9 pr-3 text-sm outline-none focus:border-violet-400 focus:ring-2 focus:ring-violet-100" />
            </label>
            <label className="sr-only" htmlFor="opportunity-stage-filter">Filter by stage</label>
          <select
            id="opportunity-stage-filter"
            value={stageFilter}
            onChange={(e) => setStageFilter(e.target.value)}
            className="h-9 rounded-md border border-slate-200 bg-white px-3 text-sm text-slate-700 outline-none focus:border-violet-400 focus:ring-2 focus:ring-violet-100"
          >
            <option value="">All stages</option>
            {OPPORTUNITY_STAGE_OPTIONS.map((option) => (
              <option key={option.value} value={option.value}>
                {option.label}
              </option>
            ))}
          </select>
          </div>
        </div>

        {loading ? (
          <div className="space-y-3 p-5" role="status" aria-label="Loading opportunities">{[0, 1, 2].map((row) => <div key={row} className="h-12 animate-pulse rounded-md bg-slate-100" />)}</div>
        ) : opportunities.length === 0 ? (
          <div className="px-5 py-14 text-center">
            <span className="mx-auto grid size-12 place-items-center rounded-xl bg-violet-50 text-violet-700"><Target size={22} /></span>
            <h3 className="mt-4 text-base font-bold text-slate-900">{search || stageFilter ? 'No matching opportunities' : 'No opportunities yet'}</h3>
            <p className="mx-auto mt-1 max-w-md text-sm text-slate-500">{search || stageFilter ? 'Try changing your search or stage filter.' : canCreate ? 'Add your first deal to start tracking it through the sales pipeline.' : 'Opportunities you have access to will be listed here.'}</p>
            {canCreate && !search && !stageFilter && <Button className="mt-5" onClick={() => setShowForm(true)} icon={Plus}>Add first opportunity</Button>}
          </div>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full min-w-[850px] text-left text-sm">
              <thead className="bg-slate-50 text-xs uppercase text-slate-500">
                <tr><th className="px-5 py-3 font-semibold">Customer</th><th className="px-4 py-3 font-semibold">Product / service</th><th className="px-4 py-3 font-semibold">Value</th><th className="px-4 py-3 font-semibold">Expected close</th><th className="px-4 py-3 font-semibold">Stage</th><th className="px-4 py-3 font-semibold">Owner</th>{canDelete && <th className="px-4 py-3" aria-label="Actions" />}</tr>
              </thead>
              <tbody className="divide-y divide-slate-100">
                {opportunities.map((o) => (
                  <tr key={o.id} className="transition-colors hover:bg-slate-50/70">
                    <td className="px-5 py-3.5"><div className="flex items-center gap-3"><span className="grid size-9 shrink-0 place-items-center rounded-lg bg-violet-50 text-violet-700"><UserRound size={17} /></span><Link to={RoutePath.EDIT_OPPORTUNITY.replace(':id', o.id)} className="font-semibold text-slate-800 hover:text-violet-700 hover:underline">{o.customerName}</Link></div></td>
                    <td className="px-4 py-3.5 text-slate-600">{o.productService || '—'}</td>
                    <td className="px-4 py-3.5 font-semibold text-slate-800">{formatCurrency(o.dealValue)}</td>
                    <td className="px-4 py-3.5 text-slate-600">{o.expectedClosingDate ? new Date(o.expectedClosingDate).toLocaleDateString() : '—'}</td>
                    <td className="px-4 py-3.5">{canEdit ? <select value={o.stage} onChange={(e) => handleStageChange(o, e.target.value)} className="h-8 rounded-md border border-slate-200 bg-white px-2 text-xs font-medium text-slate-700 outline-none focus:border-violet-400 focus:ring-2 focus:ring-violet-100">{OPPORTUNITY_STAGE_OPTIONS.map((option) => <option key={option.value} value={option.value}>{option.label}</option>)}</select> : <span className="inline-flex rounded-full bg-violet-50 px-2.5 py-1 text-xs font-semibold text-violet-700">{OPPORTUNITY_STAGE_LABELS[o.stage] || o.stage}</span>}</td>
                    <td className="px-4 py-3.5 text-slate-600">{o.ownerName || '—'}</td>
                    {canDelete && <td className="px-4 py-3.5 text-right"><button type="button" onClick={() => handleDelete(o)} className="inline-flex items-center gap-1 rounded-md px-2.5 py-1.5 text-xs font-semibold text-rose-600 transition hover:bg-rose-50 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary"><Trash2 size={14} />Delete</button></td>}
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
        {!loading && opportunities.length > 0 && <div className="border-t border-slate-100 px-5 py-3 text-xs text-slate-500">Showing {opportunities.length} {opportunities.length === 1 ? 'opportunity' : 'opportunities'}</div>}
      </section>
    </section>
  );
};

export default Opportunities;
