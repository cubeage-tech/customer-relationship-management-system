import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { CalendarDays, CheckCircle2, CircleAlert, Megaphone, Plus, Search, X } from 'lucide-react';
import PageHeader from '../../components/common/PageHeader';
import EmptyState from '../../components/common/EmptyState';
import Button from '../../components/common/Button';
import { usePermissions } from '../../core/hooks/usePermissions';
import { MODULES, PERMISSIONS, SCOPE_LABELS } from '../../core/constants/permission.constant';
import {
  CAMPAIGN_CHANNEL_LABELS,
  CAMPAIGN_CHANNEL_OPTIONS,
  CAMPAIGN_STATUS_LABELS,
  CAMPAIGN_STATUS_OPTIONS,
} from '../../core/constants/app.constant';
import RoutePath from '../../core/constants/routes.constant';
import { listCampaigns, getCampaignSummary, createCampaign } from '../../core/services/campaign.service';

const INITIAL_FORM = {
  name: '',
  description: '',
  channel: CAMPAIGN_CHANNEL_OPTIONS[0].value,
  startDate: '',
  endDate: '',
  budget: '',
};

const Campaigns = () => {
  const { can, scopeFor } = usePermissions();
  const canManage = can(PERMISSIONS.CAMPAIGNS_MANAGE);
  const canViewList = can(PERMISSIONS.CAMPAIGNS_VIEW) || canManage;

  const [campaigns, setCampaigns] = useState([]);
  const [summary, setSummary] = useState(null);
  const [loading, setLoading] = useState(canViewList);
  const [statusFilter, setStatusFilter] = useState('');
  const [channelFilter, setChannelFilter] = useState('');
  const [search, setSearch] = useState('');

  const [showForm, setShowForm] = useState(false);
  const [form, setForm] = useState(INITIAL_FORM);
  const [toast, setToast] = useState(null);

  const refresh = () => {
    listCampaigns({ status: statusFilter, channel: channelFilter, search })
      .then((data) => setCampaigns(data ?? []))
      .catch(() => setCampaigns([]))
      .finally(() => setLoading(false));
    getCampaignSummary().then(setSummary).catch(() => setSummary(null));
  };

  useEffect(() => {
    if (canViewList) {
      refresh();
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [statusFilter, channelFilter, search]);

  useEffect(() => {
    if (!toast) return undefined;
    const timeoutId = window.setTimeout(() => setToast(null), 4000);
    return () => window.clearTimeout(timeoutId);
  }, [toast]);

  const handleChange = (e) => setForm((prev) => ({ ...prev, [e.target.name]: e.target.value }));

  const handleAddCampaign = async (e) => {
    e.preventDefault();
    try {
      await createCampaign({ ...form, budget: form.budget || 0 });
      setForm(INITIAL_FORM);
      setShowForm(false);
      refresh();
      setToast({ type: 'success', message: `${form.name} was created successfully.` });
    } catch (err) {
      setToast({
        type: 'error',
        message: err.response?.data?.message || 'Could not create this campaign. Check the details and try again.',
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
        title="Campaigns"
        subtitle={`Marketing campaigns and the leads they generate — ${SCOPE_LABELS[scopeFor(MODULES.CAMPAIGNS)]}`}
        actions={canManage && (
          <Button onClick={() => setShowForm((prev) => !prev)} icon={showForm ? X : Plus}>
            {showForm ? 'Close form' : 'New campaign'}
          </Button>
        )}
      />

      {summary && (
        <div className="grid gap-3 sm:grid-cols-2 xl:grid-cols-4">
          <div className="rounded-lg border border-slate-200 bg-white p-4 shadow-sm">
            <p className="text-xs font-semibold uppercase text-slate-500">Active campaigns</p>
            <p className="mt-1 text-xl font-bold text-slate-900">{summary.activeCampaigns}</p>
            <p className="mt-1 text-xs text-slate-500">Currently running</p>
          </div>
          <div className="rounded-lg border border-slate-200 bg-white p-4 shadow-sm">
            <p className="text-xs font-semibold uppercase text-slate-500">Leads generated</p>
            <p className="mt-1 text-xl font-bold text-slate-900">{summary.leadsGenerated}</p>
            <p className="mt-1 text-xs text-slate-500">Across all campaigns</p>
          </div>
          <div className="rounded-lg border border-slate-200 bg-white p-4 shadow-sm">
            <p className="text-xs font-semibold uppercase text-slate-500">Qualified leads</p>
            <p className="mt-1 text-xl font-bold text-slate-900">{summary.qualifiedLeads}</p>
            <p className="mt-1 text-xs text-slate-500">Sales-ready prospects</p>
          </div>
          <div className="rounded-lg border border-slate-200 bg-white p-4 shadow-sm">
            <p className="text-xs font-semibold uppercase text-slate-500">Conversion rate</p>
            <p className="mt-1 text-xl font-bold text-slate-900">{summary.conversionRate.toFixed(1)}%</p>
            <p className="mt-1 text-xs text-slate-500">Qualified from generated</p>
          </div>
        </div>
      )}

      {canManage && showForm && (
        <div className="fixed inset-0 z-40 flex items-center justify-center overflow-y-auto p-4 sm:p-6" onKeyDown={(event) => {
          if (event.key === 'Escape') setShowForm(false);
        }}>
          <button type="button" className="fixed inset-0 bg-slate-950/45 backdrop-blur-sm" onClick={() => setShowForm(false)} aria-label="Close campaign form" tabIndex={-1} />
          <div role="dialog" aria-modal="true" aria-labelledby="new-campaign-title" className="relative z-10 my-auto w-full max-w-2xl overflow-hidden rounded-lg border border-white/70 bg-white shadow-2xl">
            <form onSubmit={handleAddCampaign}>
              <div className="flex items-center gap-3 border-b border-slate-100 bg-slate-50/80 px-5 py-4 sm:px-6">
                <span className="grid size-10 place-items-center rounded-lg bg-violet-100 text-violet-700"><Megaphone size={19} /></span>
                <div className="min-w-0 flex-1"><h2 id="new-campaign-title" className="text-base font-bold text-slate-900">Create a campaign</h2><p className="mt-0.5 text-xs text-slate-500">Set up a channel, schedule, and budget for your campaign.</p></div>
                <button type="button" onClick={() => setShowForm(false)} className="grid size-9 shrink-0 place-items-center rounded-md text-slate-400 transition hover:bg-slate-200/70 hover:text-slate-700 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary" aria-label="Close campaign form"><X size={18} /></button>
              </div>
              <div className="grid gap-4 p-5 sm:grid-cols-2 sm:p-6">
                <label className="space-y-1.5 text-sm font-medium text-slate-700 sm:col-span-2">Campaign name <span className="text-rose-500">*</span>
                  <input autoFocus type="text" name="name" placeholder="e.g. Spring product launch" value={form.name} onChange={handleChange} className="h-10 w-full rounded-md border border-slate-200 bg-white px-3 text-sm font-normal text-slate-900 outline-none focus:border-violet-400 focus:ring-2 focus:ring-violet-100" required />
                </label>
                <label className="space-y-1.5 text-sm font-medium text-slate-700">Channel
                  <select name="channel" value={form.channel} onChange={handleChange} className="h-10 w-full rounded-md border border-slate-200 bg-white px-3 text-sm font-normal text-slate-900 outline-none focus:border-violet-400 focus:ring-2 focus:ring-violet-100">
                    {CAMPAIGN_CHANNEL_OPTIONS.map((option) => <option key={option.value} value={option.value}>{option.label}</option>)}
                  </select>
                </label>
                <label className="space-y-1.5 text-sm font-medium text-slate-700">Budget <span className="font-normal text-slate-400">Optional</span>
                  <input type="number" name="budget" placeholder="0.00" min="0" step="0.01" value={form.budget} onChange={handleChange} className="h-10 w-full rounded-md border border-slate-200 bg-white px-3 text-sm font-normal text-slate-900 outline-none focus:border-violet-400 focus:ring-2 focus:ring-violet-100" />
                </label>
                <label className="space-y-1.5 text-sm font-medium text-slate-700">Start date <span className="font-normal text-slate-400">Optional</span>
                  <span className="relative block"><CalendarDays size={16} className="absolute left-3 top-1/2 -translate-y-1/2 text-slate-400" aria-hidden="true" /><input type="date" name="startDate" value={form.startDate} onChange={handleChange} className="h-10 w-full rounded-md border border-slate-200 bg-white pl-9 pr-3 text-sm font-normal text-slate-900 outline-none focus:border-violet-400 focus:ring-2 focus:ring-violet-100" /></span>
                </label>
                <label className="space-y-1.5 text-sm font-medium text-slate-700">End date <span className="font-normal text-slate-400">Optional</span>
                  <span className="relative block"><CalendarDays size={16} className="absolute left-3 top-1/2 -translate-y-1/2 text-slate-400" aria-hidden="true" /><input type="date" name="endDate" value={form.endDate} onChange={handleChange} className="h-10 w-full rounded-md border border-slate-200 bg-white pl-9 pr-3 text-sm font-normal text-slate-900 outline-none focus:border-violet-400 focus:ring-2 focus:ring-violet-100" /></span>
                </label>
                <label className="space-y-1.5 text-sm font-medium text-slate-700 sm:col-span-2">Description <span className="font-normal text-slate-400">Optional</span>
                  <textarea name="description" placeholder="What is this campaign about?" value={form.description} onChange={handleChange} className="w-full resize-y rounded-md border border-slate-200 bg-white px-3 py-2 text-sm font-normal text-slate-900 outline-none focus:border-violet-400 focus:ring-2 focus:ring-violet-100" rows={3} />
                </label>
                <div className="flex flex-wrap justify-end gap-2 border-t border-slate-100 pt-4 sm:col-span-2"><Button variant="outline" onClick={() => setShowForm(false)}>Cancel</Button><Button type="submit" icon={Plus}>Create campaign</Button></div>
              </div>
            </form>
          </div>
        </div>
      )}

      {canViewList && (
        <section className="overflow-hidden rounded-lg border border-slate-200 bg-white shadow-sm">
          <div className="flex flex-col gap-4 border-b border-slate-100 p-4 sm:flex-row sm:items-center sm:justify-between">
            <div><h2 className="text-base font-bold text-slate-900">Campaign directory</h2><p className="mt-1 text-xs text-slate-500">Compare channels, lead results, and campaign budgets.</p></div>
            <div className="flex flex-wrap items-center gap-2">
              <label className="relative min-w-48 flex-1 sm:flex-none"><Search size={16} className="absolute left-3 top-1/2 -translate-y-1/2 text-slate-400" aria-hidden="true" /><span className="sr-only">Search by campaign name</span><input type="search" placeholder="Search campaigns" value={search} onChange={(e) => setSearch(e.target.value)} className="h-9 w-full rounded-md border border-slate-200 bg-white pl-9 pr-3 text-sm outline-none focus:border-violet-400 focus:ring-2 focus:ring-violet-100" /></label>
              <label className="sr-only" htmlFor="campaign-status-filter">Filter by status</label>
            <select
              id="campaign-status-filter"
              value={statusFilter}
              onChange={(e) => setStatusFilter(e.target.value)}
              className="h-9 rounded-md border border-slate-200 bg-white px-3 text-sm text-slate-700 outline-none focus:border-violet-400 focus:ring-2 focus:ring-violet-100"
            >
              <option value="">All statuses</option>
              {CAMPAIGN_STATUS_OPTIONS.map((option) => (
                <option key={option.value} value={option.value}>{option.label}</option>
              ))}
            </select>
              <label className="sr-only" htmlFor="campaign-channel-filter">Filter by channel</label>
            <select
              id="campaign-channel-filter"
              value={channelFilter}
              onChange={(e) => setChannelFilter(e.target.value)}
              className="h-9 rounded-md border border-slate-200 bg-white px-3 text-sm text-slate-700 outline-none focus:border-violet-400 focus:ring-2 focus:ring-violet-100"
            >
              <option value="">All channels</option>
              {CAMPAIGN_CHANNEL_OPTIONS.map((option) => (
                <option key={option.value} value={option.value}>{option.label}</option>
              ))}
            </select>
            </div>
          </div>

          {loading ? (
            <div className="space-y-3 p-5" role="status" aria-label="Loading campaigns">{[0, 1, 2].map((row) => <div key={row} className="h-12 animate-pulse rounded-md bg-slate-100" />)}</div>
          ) : campaigns.length === 0 ? (
            <div className="px-5 py-14 text-center"><span className="mx-auto grid size-12 place-items-center rounded-xl bg-violet-50 text-violet-700"><Megaphone size={22} /></span><h3 className="mt-4 text-base font-bold text-slate-900">{search || statusFilter || channelFilter ? 'No matching campaigns' : 'No campaigns yet'}</h3><p className="mx-auto mt-1 max-w-md text-sm text-slate-500">{search || statusFilter || channelFilter ? 'Try changing your search or filters.' : canManage ? 'Launch a campaign to capture leads and measure conversion.' : 'Campaigns run by the marketing team will be listed here.'}</p>{canManage && !search && !statusFilter && !channelFilter && <Button className="mt-5" onClick={() => setShowForm(true)} icon={Plus}>Create first campaign</Button>}</div>
          ) : (
            <div className="overflow-x-auto">
              <table className="w-full min-w-[800px] text-left text-sm">
                <thead className="bg-slate-50 text-xs uppercase text-slate-500"><tr><th className="px-5 py-3 font-semibold">Campaign</th><th className="px-4 py-3 font-semibold">Channel</th><th className="px-4 py-3 font-semibold">Status</th><th className="px-4 py-3 font-semibold">Leads</th><th className="px-4 py-3 font-semibold">Conversion</th><th className="px-4 py-3 font-semibold">Budget</th></tr></thead>
                <tbody className="divide-y divide-slate-100">
                  {campaigns.map((c) => (
                    <tr key={c.id} className="transition-colors hover:bg-slate-50/70">
                      <td className="px-5 py-3.5"><div className="flex items-center gap-3"><span className="grid size-9 shrink-0 place-items-center rounded-lg bg-violet-50 text-violet-700"><Megaphone size={17} /></span><Link to={RoutePath.EDIT_CAMPAIGN.replace(':id', c.id)} className="font-semibold text-slate-800 hover:text-violet-700 hover:underline">{c.name}</Link></div></td>
                      <td className="px-4 py-3.5 text-slate-600">{CAMPAIGN_CHANNEL_LABELS[c.channel] || c.channel}</td>
                      <td className="px-4 py-3.5"><span className={`inline-flex rounded-full px-2.5 py-1 text-xs font-semibold ${c.status === 'active' ? 'bg-emerald-50 text-emerald-700' : 'bg-slate-100 text-slate-600'}`}>{CAMPAIGN_STATUS_LABELS[c.status] || c.status}</span></td>
                      <td className="px-4 py-3.5 font-medium text-slate-700">{c.leadsGenerated}</td>
                      <td className="px-4 py-3.5 font-medium text-slate-700">{c.conversionRate.toFixed(1)}%</td>
                      <td className="px-4 py-3.5 text-slate-600">{Number(c.budget).toLocaleString()}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
          {!loading && campaigns.length > 0 && <div className="border-t border-slate-100 px-5 py-3 text-xs text-slate-500">Showing {campaigns.length} {campaigns.length === 1 ? 'campaign' : 'campaigns'}</div>}
        </section>
      )}

      {!loading && !canViewList && (
        <EmptyState
          title="No campaigns"
          message="Campaigns run by the marketing team will be listed here."
        />
      )}
    </section>
  );
};

export default Campaigns;
