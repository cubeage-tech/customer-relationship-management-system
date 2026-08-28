import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
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
  const [error, setError] = useState('');

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

  const handleChange = (e) => setForm((prev) => ({ ...prev, [e.target.name]: e.target.value }));

  const handleAddCampaign = async (e) => {
    e.preventDefault();
    setError('');
    try {
      await createCampaign({ ...form, budget: form.budget || 0 });
      setForm(INITIAL_FORM);
      setShowForm(false);
      refresh();
    } catch (err) {
      setError(err.response?.data?.message || 'Could not create this campaign. Check the details and try again.');
    }
  };

  return (
    <section>
      <PageHeader
        title="Campaigns"
        subtitle={`Marketing campaigns and the leads they generate — ${SCOPE_LABELS[scopeFor(MODULES.CAMPAIGNS)]}`}
        actions={canManage && (
          <Button onClick={() => setShowForm((prev) => !prev)}>
            {showForm ? 'Cancel' : 'New campaign'}
          </Button>
        )}
      />

      {summary && (
        <div className="mb-6 grid gap-3 sm:grid-cols-4">
          <div className="bg-white border border-slate-200 rounded-lg p-3">
            <p className="text-xs text-slate-500">Active campaigns</p>
            <p className="text-lg font-semibold text-slate-900">{summary.activeCampaigns}</p>
          </div>
          <div className="bg-white border border-slate-200 rounded-lg p-3">
            <p className="text-xs text-slate-500">Leads generated</p>
            <p className="text-lg font-semibold text-slate-900">{summary.leadsGenerated}</p>
          </div>
          <div className="bg-white border border-slate-200 rounded-lg p-3">
            <p className="text-xs text-slate-500">Qualified leads</p>
            <p className="text-lg font-semibold text-slate-900">{summary.qualifiedLeads}</p>
          </div>
          <div className="bg-white border border-slate-200 rounded-lg p-3">
            <p className="text-xs text-slate-500">Conversion rate</p>
            <p className="text-lg font-semibold text-slate-900">{summary.conversionRate.toFixed(1)}%</p>
          </div>
        </div>
      )}

      {canManage && showForm && (
        <form onSubmit={handleAddCampaign} className="mb-6 grid gap-3 sm:grid-cols-2 border border-slate-300 rounded p-4">
          <input
            type="text"
            name="name"
            placeholder="Campaign name"
            value={form.name}
            onChange={handleChange}
            className="border border-slate-300 rounded px-2 py-1 sm:col-span-2"
            required
          />
          <select
            name="channel"
            value={form.channel}
            onChange={handleChange}
            className="border border-slate-300 rounded px-2 py-1"
          >
            {CAMPAIGN_CHANNEL_OPTIONS.map((option) => (
              <option key={option.value} value={option.value}>{option.label}</option>
            ))}
          </select>
          <input
            type="number"
            name="budget"
            placeholder="Budget"
            min="0"
            step="0.01"
            value={form.budget}
            onChange={handleChange}
            className="border border-slate-300 rounded px-2 py-1"
          />
          <label className="text-sm text-slate-600">
            Start date
            <input
              type="date"
              name="startDate"
              value={form.startDate}
              onChange={handleChange}
              className="mt-1 w-full border border-slate-300 rounded px-2 py-1"
            />
          </label>
          <label className="text-sm text-slate-600">
            End date
            <input
              type="date"
              name="endDate"
              value={form.endDate}
              onChange={handleChange}
              className="mt-1 w-full border border-slate-300 rounded px-2 py-1"
            />
          </label>
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
            <Button type="submit">Create campaign</Button>
          </div>
        </form>
      )}

      {canViewList && (
        <div className="mb-4 flex flex-wrap gap-4">
          <input
            type="text"
            placeholder="Search by name"
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
              {CAMPAIGN_STATUS_OPTIONS.map((option) => (
                <option key={option.value} value={option.value}>{option.label}</option>
              ))}
            </select>
          </label>
          <label className="text-sm text-slate-600">
            Channel
            <select
              value={channelFilter}
              onChange={(e) => setChannelFilter(e.target.value)}
              className="ml-2 border border-slate-300 rounded px-2 py-1"
            >
              <option value="">All channels</option>
              {CAMPAIGN_CHANNEL_OPTIONS.map((option) => (
                <option key={option.value} value={option.value}>{option.label}</option>
              ))}
            </select>
          </label>
        </div>
      )}

      {loading ? null : !canViewList ? (
        <EmptyState
          title="No campaigns"
          message="Campaigns run by the marketing team will be listed here."
        />
      ) : campaigns.length === 0 ? (
        <EmptyState
          title="No campaigns yet"
          message={
            canManage
              ? 'Launch a campaign to capture leads and measure conversion.'
              : 'Campaigns run by the marketing team will be listed here.'
          }
        />
      ) : (
        <div className="overflow-x-auto">
          <table className="w-full text-left text-sm border-collapse">
            <thead>
              <tr className="border-b border-slate-300">
                <th className="py-2 pr-4">Name</th>
                <th className="py-2 pr-4">Channel</th>
                <th className="py-2 pr-4">Status</th>
                <th className="py-2 pr-4">Leads</th>
                <th className="py-2 pr-4">Conversion</th>
                <th className="py-2 pr-4">Budget</th>
              </tr>
            </thead>
            <tbody>
              {campaigns.map((c) => (
                <tr key={c.id} className="border-b border-slate-100">
                  <td className="py-2 pr-4">
                    <Link
                      to={RoutePath.EDIT_CAMPAIGN.replace(':id', c.id)}
                      className="text-indigo-600 hover:underline font-medium"
                    >
                      {c.name}
                    </Link>
                  </td>
                  <td className="py-2 pr-4">{CAMPAIGN_CHANNEL_LABELS[c.channel] || c.channel}</td>
                  <td className="py-2 pr-4">{CAMPAIGN_STATUS_LABELS[c.status] || c.status}</td>
                  <td className="py-2 pr-4">{c.leadsGenerated}</td>
                  <td className="py-2 pr-4">{c.conversionRate.toFixed(1)}%</td>
                  <td className="py-2 pr-4">{Number(c.budget).toLocaleString()}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </section>
  );
};

export default Campaigns;
