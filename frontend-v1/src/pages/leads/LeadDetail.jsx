import { useEffect, useState } from 'react';
import { useParams, Link } from 'react-router-dom';
import PageHeader from '../../components/common/PageHeader';
import EmptyState from '../../components/common/EmptyState';
import Button from '../../components/common/Button';
import { usePermissions } from '../../core/hooks/usePermissions';
import { PERMISSIONS } from '../../core/constants/permission.constant';
import {
  INDUSTRY_LABELS,
  INDUSTRY_OPTIONS,
  LEAD_SOURCE_LABELS,
  LEAD_SOURCE_OPTIONS,
  LEAD_STAGE_LABELS,
  LEAD_STAGE_OPTIONS,
} from '../../core/constants/app.constant';
import RoutePath from '../../core/constants/routes.constant';
import { getLead, updateLead, changeLeadStage, assignLead } from '../../core/services/lead.service';
import { listUsers } from '../../core/services/user.service';

const toProfileForm = (lead) => ({
  leadName: lead.leadName || '',
  companyName: lead.companyName || '',
  industry: lead.industry,
  source: lead.source,
  contactEmail: lead.contactEmail || '',
  contactPhone: lead.contactPhone || '',
  followUpDate: lead.followUpDate ? lead.followUpDate.slice(0, 10) : '',
  notes: lead.notes || '',
});

const LeadDetail = () => {
  const { id } = useParams();
  const { can } = usePermissions();
  const canEdit = can(PERMISSIONS.LEADS_EDIT);
  const canAssign = can(PERMISSIONS.LEADS_ASSIGN);

  const [lead, setLead] = useState(null);
  const [loading, setLoading] = useState(true);
  const [notFound, setNotFound] = useState(false);
  const [users, setUsers] = useState([]);

  const [editingProfile, setEditingProfile] = useState(false);
  const [profileForm, setProfileForm] = useState(null);
  const [profileError, setProfileError] = useState('');
  const [stageError, setStageError] = useState('');

  const refresh = () => {
    getLead(id)
      .then((data) => {
        setLead(data);
        setProfileForm(toProfileForm(data));
      })
      .catch(() => setNotFound(true))
      .finally(() => setLoading(false));
  };

  useEffect(() => {
    refresh();
    if (canAssign) {
      listUsers().then((data) => setUsers(data ?? [])).catch(() => setUsers([]));
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [id]);

  const handleProfileChange = (e) =>
    setProfileForm((prev) => ({ ...prev, [e.target.name]: e.target.value }));

  const handleProfileSave = async (e) => {
    e.preventDefault();
    setProfileError('');
    try {
      const saved = await updateLead(id, {
        ...profileForm,
        followUpDate: profileForm.followUpDate ? `${profileForm.followUpDate}T00:00:00` : null,
      });
      setLead(saved);
      setProfileForm(toProfileForm(saved));
      setEditingProfile(false);
    } catch (err) {
      setProfileError(err.response?.data?.message || 'Could not save these changes.');
    }
  };

  const handleStageChange = async (nextStage) => {
    if (nextStage === lead.stage) return;
    setStageError('');
    try {
      const saved = await changeLeadStage(id, nextStage);
      setLead(saved);
    } catch (err) {
      if (err.response?.status === 400 && window.confirm('This skips pipeline stages. Move the lead anyway?')) {
        try {
          const saved = await changeLeadStage(id, nextStage, true);
          setLead(saved);
        } catch (retryErr) {
          setStageError(retryErr.response?.data?.message || 'Could not update the stage.');
        }
      } else {
        setStageError(err.response?.data?.message || 'Could not update the stage.');
      }
    }
  };

  const handleAssign = async (e) => {
    const ownerId = e.target.value;
    if (!ownerId) return;
    const saved = await assignLead(id, ownerId);
    setLead(saved);
  };

  if (loading) return null;

  if (notFound) {
    return (
      <EmptyState
        title="Lead not found"
        message="This lead may have been removed, or you may not have access to it."
        action={<Link to={RoutePath.LEADS} className="text-indigo-600 hover:underline">Back to leads</Link>}
      />
    );
  }

  return (
    <section>
      <PageHeader
        title={lead.leadName}
        subtitle={`${lead.companyName} · ${INDUSTRY_LABELS[lead.industry] || lead.industry} · ${LEAD_SOURCE_LABELS[lead.source] || lead.source}`}
        actions={
          <Link to={RoutePath.LEADS} className="text-sm text-slate-500 hover:underline">
            Back to leads
          </Link>
        }
      />

      {/* ---------------- Pipeline stage ---------------- */}
      <div className="bg-white border border-slate-200 rounded-lg p-5 mb-6">
        <h2 className="font-semibold text-slate-900 mb-3">Pipeline stage</h2>
        <div className="flex flex-wrap items-center gap-2">
          {LEAD_STAGE_OPTIONS.map((option) => (
            <button
              key={option.value}
              type="button"
              disabled={!canEdit}
              onClick={() => handleStageChange(option.value)}
              className={`px-3 py-1.5 rounded-full text-xs font-medium border ${
                lead.stage === option.value
                  ? 'bg-indigo-600 text-white border-indigo-600'
                  : 'bg-white text-slate-600 border-slate-300 hover:border-indigo-400'
              } ${!canEdit ? 'cursor-not-allowed opacity-60' : ''}`}
            >
              {option.label}
            </button>
          ))}
        </div>
        {stageError && <p className="form-error mt-2">{stageError}</p>}

        {lead.convertedCustomerId && (
          <p className="mt-3 text-sm text-green-700">
            Converted to customer —{' '}
            <Link
              to={RoutePath.EDIT_CUSTOMER.replace(':id', lead.convertedCustomerId)}
              className="underline font-medium"
            >
              view account
            </Link>
          </p>
        )}
      </div>

      {/* ---------------- Assignment ---------------- */}
      {canAssign && (
        <div className="bg-white border border-slate-200 rounded-lg p-5 mb-6">
          <h2 className="font-semibold text-slate-900 mb-3">Assigned to</h2>
          <select
            value={lead.ownerId || ''}
            onChange={handleAssign}
            className="border border-slate-300 rounded px-2 py-1 text-sm"
          >
            <option value="">Unassigned</option>
            {users.map((u) => (
              <option key={u.id} value={u.id}>
                {u.fullName}
              </option>
            ))}
          </select>
        </div>
      )}

      {/* ---------------- Profile ---------------- */}
      <div className="bg-white border border-slate-200 rounded-lg p-5 mb-6">
        <div className="flex items-center justify-between mb-4">
          <h2 className="font-semibold text-slate-900">Lead details</h2>
          {canEdit && !editingProfile && (
            <button
              type="button"
              onClick={() => setEditingProfile(true)}
              className="text-sm text-indigo-600 hover:underline"
            >
              Edit
            </button>
          )}
        </div>

        {editingProfile ? (
          <form onSubmit={handleProfileSave} className="grid gap-3 sm:grid-cols-2">
            <input
              type="text"
              name="leadName"
              placeholder="Lead / contact name"
              value={profileForm.leadName}
              onChange={handleProfileChange}
              className="border border-slate-300 rounded px-2 py-1"
              required
            />
            <input
              type="text"
              name="companyName"
              placeholder="Company name"
              value={profileForm.companyName}
              onChange={handleProfileChange}
              className="border border-slate-300 rounded px-2 py-1"
              required
            />
            <select
              name="industry"
              value={profileForm.industry}
              onChange={handleProfileChange}
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
              value={profileForm.source}
              onChange={handleProfileChange}
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
              placeholder="Contact email"
              value={profileForm.contactEmail}
              onChange={handleProfileChange}
              className="border border-slate-300 rounded px-2 py-1"
            />
            <input
              type="text"
              name="contactPhone"
              placeholder="Contact phone"
              value={profileForm.contactPhone}
              onChange={handleProfileChange}
              className="border border-slate-300 rounded px-2 py-1"
            />
            <label className="text-sm text-slate-600 sm:col-span-2">
              Follow-up date
              <input
                type="date"
                name="followUpDate"
                value={profileForm.followUpDate}
                onChange={handleProfileChange}
                className="ml-2 border border-slate-300 rounded px-2 py-1"
              />
            </label>
            <textarea
              name="notes"
              placeholder="Notes"
              value={profileForm.notes}
              onChange={handleProfileChange}
              className="border border-slate-300 rounded px-2 py-1 sm:col-span-2"
              rows={3}
            />

            {profileError && <p className="form-error sm:col-span-2">{profileError}</p>}
            <div className="sm:col-span-2 flex gap-3">
              <Button type="submit">Save changes</Button>
              <Button
                type="button"
                variant="secondary"
                onClick={() => {
                  setProfileForm(toProfileForm(lead));
                  setEditingProfile(false);
                }}
              >
                Cancel
              </Button>
            </div>
          </form>
        ) : (
          <dl className="grid gap-3 sm:grid-cols-2 text-sm">
            <div><dt className="text-slate-500">Contact email</dt><dd>{lead.contactEmail || '—'}</dd></div>
            <div><dt className="text-slate-500">Contact phone</dt><dd>{lead.contactPhone || '—'}</dd></div>
            <div>
              <dt className="text-slate-500">Follow-up date</dt>
              <dd>{lead.followUpDate ? new Date(lead.followUpDate).toLocaleDateString() : '—'}</dd>
            </div>
            <div><dt className="text-slate-500">Stage</dt><dd>{LEAD_STAGE_LABELS[lead.stage] || lead.stage}</dd></div>
            <div className="sm:col-span-2"><dt className="text-slate-500">Notes</dt><dd>{lead.notes || '—'}</dd></div>
          </dl>
        )}
      </div>
    </section>
  );
};

export default LeadDetail;
