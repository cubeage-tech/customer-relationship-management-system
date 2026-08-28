import { useEffect, useState } from 'react';
import { useParams, Link } from 'react-router-dom';
import PageHeader from '../../components/common/PageHeader';
import EmptyState from '../../components/common/EmptyState';
import Button from '../../components/common/Button';
import { usePermissions } from '../../core/hooks/usePermissions';
import { PERMISSIONS } from '../../core/constants/permission.constant';
import {
  CAMPAIGN_CHANNEL_LABELS,
  CAMPAIGN_CHANNEL_OPTIONS,
  CAMPAIGN_STATUS_OPTIONS,
} from '../../core/constants/app.constant';
import RoutePath from '../../core/constants/routes.constant';
import {
  getCampaign,
  getCampaignLeads,
  updateCampaign,
  changeCampaignStatus,
} from '../../core/services/campaign.service';

const CampaignDetail = () => {
  const { id } = useParams();
  const { can } = usePermissions();
  const canManage = can(PERMISSIONS.CAMPAIGNS_MANAGE);

  const [campaign, setCampaign] = useState(null);
  const [leads, setLeads] = useState([]);
  const [loading, setLoading] = useState(true);
  const [notFound, setNotFound] = useState(false);
  const [actionError, setActionError] = useState('');

  const [editing, setEditing] = useState(false);
  const [form, setForm] = useState(null);

  const refresh = () => {
    getCampaign(id)
      .then((data) => {
        setCampaign(data);
        setForm({
          name: data.name,
          description: data.description || '',
          channel: data.channel,
          startDate: data.startDate || '',
          endDate: data.endDate || '',
          budget: data.budget,
          actualCost: data.actualCost,
        });
      })
      .catch(() => setNotFound(true))
      .finally(() => setLoading(false));
    getCampaignLeads(id).then((data) => setLeads(data ?? [])).catch(() => setLeads([]));
  };

  useEffect(() => {
    refresh();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [id]);

  const handleChange = (e) => setForm((prev) => ({ ...prev, [e.target.name]: e.target.value }));

  const handleSave = async (e) => {
    e.preventDefault();
    setActionError('');
    try {
      const saved = await updateCampaign(id, form);
      setCampaign(saved);
      setEditing(false);
    } catch (err) {
      setActionError(err.response?.data?.message || 'Could not save these changes.');
    }
  };

  const handleStatusChange = async (status) => {
    if (status === campaign.status) return;
    setActionError('');
    try {
      const saved = await changeCampaignStatus(id, status);
      setCampaign(saved);
    } catch (err) {
      setActionError(err.response?.data?.message || 'Could not update the status.');
    }
  };

  if (loading) return null;

  if (notFound) {
    return (
      <EmptyState
        title="Campaign not found"
        message="This campaign may have been removed, or you may not have access to it."
        action={<Link to={RoutePath.CAMPAIGNS} className="text-indigo-600 hover:underline">Back to campaigns</Link>}
      />
    );
  }

  return (
    <section>
      <PageHeader
        title={campaign.name}
        subtitle={`${CAMPAIGN_CHANNEL_LABELS[campaign.channel] || campaign.channel} · ${campaign.leadsGenerated} leads generated`}
        actions={
          <div className="flex items-center gap-3">
            {canManage && (
              <Button variant="secondary" onClick={() => setEditing((prev) => !prev)}>
                {editing ? 'Cancel' : 'Edit'}
              </Button>
            )}
            <Link to={RoutePath.CAMPAIGNS} className="text-sm text-slate-500 hover:underline">
              Back to campaigns
            </Link>
          </div>
        }
      />

      {/* ---------------- Status ---------------- */}
      <div className="bg-white border border-slate-200 rounded-lg p-5 mb-6">
        <div className="flex flex-wrap items-center gap-2">
          {CAMPAIGN_STATUS_OPTIONS.map((option) => (
            <button
              key={option.value}
              type="button"
              disabled={!canManage}
              onClick={() => handleStatusChange(option.value)}
              className={`px-3 py-1.5 rounded-full text-xs font-medium border ${
                campaign.status === option.value
                  ? 'bg-indigo-600 text-white border-indigo-600'
                  : 'bg-white text-slate-600 border-slate-300 hover:border-indigo-400'
              } ${!canManage ? 'cursor-not-allowed opacity-60' : ''}`}
            >
              {option.label}
            </button>
          ))}
        </div>
        {actionError && <p className="form-error mt-2">{actionError}</p>}
      </div>

      {/* ---------------- Performance ---------------- */}
      <div className="mb-6 grid gap-3 sm:grid-cols-4">
        <div className="bg-white border border-slate-200 rounded-lg p-3">
          <p className="text-xs text-slate-500">Leads generated</p>
          <p className="text-lg font-semibold text-slate-900">{campaign.leadsGenerated}</p>
        </div>
        <div className="bg-white border border-slate-200 rounded-lg p-3">
          <p className="text-xs text-slate-500">Converted</p>
          <p className="text-lg font-semibold text-slate-900">{campaign.convertedLeads}</p>
        </div>
        <div className="bg-white border border-slate-200 rounded-lg p-3">
          <p className="text-xs text-slate-500">Conversion rate</p>
          <p className="text-lg font-semibold text-slate-900">{campaign.conversionRate.toFixed(1)}%</p>
        </div>
        <div className="bg-white border border-slate-200 rounded-lg p-3">
          <p className="text-xs text-slate-500">Budget / Spent</p>
          <p className="text-lg font-semibold text-slate-900">
            {Number(campaign.budget).toLocaleString()} / {Number(campaign.actualCost).toLocaleString()}
          </p>
        </div>
      </div>

      {/* ---------------- Details ---------------- */}
      <div className="bg-white border border-slate-200 rounded-lg p-5 mb-6">
        <h2 className="font-semibold text-slate-900 mb-3">Details</h2>
        {editing ? (
          <form onSubmit={handleSave} className="grid gap-3 sm:grid-cols-2">
            <input
              type="text"
              name="name"
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
              min="0"
              step="0.01"
              value={form.budget}
              onChange={handleChange}
              placeholder="Budget"
              className="border border-slate-300 rounded px-2 py-1"
            />
            <input
              type="number"
              name="actualCost"
              min="0"
              step="0.01"
              value={form.actualCost}
              onChange={handleChange}
              placeholder="Actual cost"
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
              value={form.description}
              onChange={handleChange}
              placeholder="Description"
              rows={3}
              className="border border-slate-300 rounded px-2 py-1 sm:col-span-2"
            />
            {actionError && <p className="form-error sm:col-span-2">{actionError}</p>}
            <div className="sm:col-span-2">
              <Button type="submit">Save changes</Button>
            </div>
          </form>
        ) : (
          <p className="text-sm text-slate-700 whitespace-pre-wrap">
            {campaign.description || 'No description provided.'}
          </p>
        )}
      </div>

      {/* ---------------- Leads generated ---------------- */}
      <div className="bg-white border border-slate-200 rounded-lg p-5">
        <h2 className="font-semibold text-slate-900 mb-3">Leads generated</h2>
        {leads.length === 0 ? (
          <p className="text-sm text-slate-500">No leads have been linked to this campaign yet.</p>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-left text-sm border-collapse">
              <thead>
                <tr className="border-b border-slate-300">
                  <th className="py-2 pr-4">Lead</th>
                  <th className="py-2 pr-4">Company</th>
                  <th className="py-2 pr-4">Stage</th>
                  <th className="py-2 pr-4">Owner</th>
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
                    <td className="py-2 pr-4">{lead.stage}</td>
                    <td className="py-2 pr-4">{lead.ownerName || '—'}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>
    </section>
  );
};

export default CampaignDetail;
