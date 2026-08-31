import { useEffect, useState } from 'react';
import { useParams, Link } from 'react-router-dom';
import PageHeader from '../../components/common/PageHeader';
import EmptyState from '../../components/common/EmptyState';
import Button from '../../components/common/Button';
import { usePermissions } from '../../core/hooks/usePermissions';
import { PERMISSIONS } from '../../core/constants/permission.constant';
import { OPPORTUNITY_STAGE_LABELS, OPPORTUNITY_STAGE_OPTIONS, OPPORTUNITY_STAGES } from '../../core/constants/app.constant';
import RoutePath from '../../core/constants/routes.constant';
import { getOpportunity, updateOpportunity, changeOpportunityStage } from '../../core/services/opportunity.service';
import { listUsers } from '../../core/services/user.service';

const formatCurrency = (value) => `₹${Number(value ?? 0).toLocaleString('en-IN')}`;

const toProfileForm = (opportunity) => ({
  customerId: opportunity.customerId,
  productService: opportunity.productService || '',
  dealValue: opportunity.dealValue ?? 0,
  expectedClosingDate: opportunity.expectedClosingDate || '',
  ownerId: opportunity.ownerId || '',
});

const OpportunityDetail = () => {
  const { id } = useParams();
  const { can } = usePermissions();
  const canEdit = can(PERMISSIONS.OPPORTUNITIES_EDIT);

  const [opportunity, setOpportunity] = useState(null);
  const [loading, setLoading] = useState(true);
  const [notFound, setNotFound] = useState(false);
  const [users, setUsers] = useState([]);

  const [editingProfile, setEditingProfile] = useState(false);
  const [profileForm, setProfileForm] = useState(null);
  const [profileError, setProfileError] = useState('');
  const [stageError, setStageError] = useState('');

  const refresh = () => {
    getOpportunity(id)
      .then((data) => {
        setOpportunity(data);
        setProfileForm(toProfileForm(data));
      })
      .catch(() => setNotFound(true))
      .finally(() => setLoading(false));
  };

  useEffect(() => {
    refresh();
    if (canEdit) {
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
      const saved = await updateOpportunity(id, profileForm);
      setOpportunity(saved);
      setProfileForm(toProfileForm(saved));
      setEditingProfile(false);
    } catch (err) {
      setProfileError(err.response?.data?.message || 'Could not save these changes.');
    }
  };

  const handleStageChange = async (nextStage) => {
    if (nextStage === opportunity.stage) return;
    setStageError('');

    let lossReason;
    if (nextStage === OPPORTUNITY_STAGES.LOST) {
      lossReason = window.prompt('Why was this opportunity lost?');
      if (!lossReason) return;
    }

    try {
      const saved = await changeOpportunityStage(id, nextStage, lossReason);
      setOpportunity(saved);
    } catch (err) {
      setStageError(err.response?.data?.message || 'Could not update the stage.');
    }
  };

  if (loading) return null;

  if (notFound) {
    return (
      <EmptyState
        title="Opportunity not found"
        message="This opportunity may have been removed, or you may not have access to it."
        action={<Link to={RoutePath.OPPORTUNITIES} className="text-indigo-600 hover:underline">Back to opportunities</Link>}
      />
    );
  }

  return (
    <section>
      <PageHeader
        title={opportunity.customerName}
        subtitle={`${opportunity.productService || 'No product specified'} · ${formatCurrency(opportunity.dealValue)}`}
        actions={
          <Link to={RoutePath.OPPORTUNITIES} className="text-sm text-slate-500 hover:underline">
            Back to opportunities
          </Link>
        }
      />

      {/* ---------------- Pipeline stage ---------------- */}
      <div className="bg-white border border-slate-200 rounded-lg p-5 mb-6">
        <h2 className="font-semibold text-slate-900 mb-3">Pipeline stage</h2>
        <div className="flex flex-wrap items-center gap-2">
          {OPPORTUNITY_STAGE_OPTIONS.map((option) => (
            <button
              key={option.value}
              type="button"
              disabled={!canEdit}
              onClick={() => handleStageChange(option.value)}
              className={`px-3 py-1.5 rounded-full text-xs font-medium border ${
                opportunity.stage === option.value
                  ? 'bg-indigo-600 text-white border-indigo-600'
                  : 'bg-white text-slate-600 border-slate-300 hover:border-indigo-400'
              } ${!canEdit ? 'cursor-not-allowed opacity-60' : ''}`}
            >
              {option.label}
            </button>
          ))}
        </div>
        {stageError && <p className="form-error mt-2">{stageError}</p>}
        {opportunity.stage === OPPORTUNITY_STAGES.LOST && opportunity.lossReason && (
          <p className="mt-3 text-sm text-red-600">Loss reason: {opportunity.lossReason}</p>
        )}

        {opportunity.leadId && (
          <p className="mt-3 text-sm text-slate-500">
            Created from lead —{' '}
            <Link to={RoutePath.EDIT_LEAD.replace(':id', opportunity.leadId)} className="text-indigo-600 underline">
              view lead
            </Link>
          </p>
        )}
      </div>

      {/* ---------------- Deal details ---------------- */}
      <div className="bg-white border border-slate-200 rounded-lg p-5 mb-6">
        <div className="flex items-center justify-between mb-4">
          <h2 className="font-semibold text-slate-900">Deal details</h2>
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
              name="productService"
              placeholder="Product / service"
              value={profileForm.productService}
              onChange={handleProfileChange}
              className="border border-slate-300 rounded px-2 py-1"
            />
            <input
              type="number"
              name="dealValue"
              placeholder="Deal value"
              value={profileForm.dealValue}
              onChange={handleProfileChange}
              min="0"
              step="0.01"
              className="border border-slate-300 rounded px-2 py-1"
              required
            />
            <label className="text-sm text-slate-600">
              Expected closing date
              <input
                type="date"
                name="expectedClosingDate"
                value={profileForm.expectedClosingDate}
                onChange={handleProfileChange}
                className="ml-2 border border-slate-300 rounded px-2 py-1"
              />
            </label>
            <select
              name="ownerId"
              value={profileForm.ownerId}
              onChange={handleProfileChange}
              className="border border-slate-300 rounded px-2 py-1"
            >
              <option value="">Unassigned</option>
              {users.map((u) => (
                <option key={u.id} value={u.id}>
                  {u.fullName}
                </option>
              ))}
            </select>

            {profileError && <p className="form-error sm:col-span-2">{profileError}</p>}
            <div className="sm:col-span-2 flex gap-3">
              <Button type="submit">Save changes</Button>
              <Button
                type="button"
                variant="secondary"
                onClick={() => {
                  setProfileForm(toProfileForm(opportunity));
                  setEditingProfile(false);
                }}
              >
                Cancel
              </Button>
            </div>
          </form>
        ) : (
          <dl className="grid gap-3 sm:grid-cols-2 text-sm">
            <div><dt className="text-slate-500">Product / service</dt><dd>{opportunity.productService || '—'}</dd></div>
            <div><dt className="text-slate-500">Deal value</dt><dd>{formatCurrency(opportunity.dealValue)}</dd></div>
            <div>
              <dt className="text-slate-500">Expected closing date</dt>
              <dd>{opportunity.expectedClosingDate ? new Date(opportunity.expectedClosingDate).toLocaleDateString() : '—'}</dd>
            </div>
            <div><dt className="text-slate-500">Owner</dt><dd>{opportunity.ownerName || '—'}</dd></div>
            <div><dt className="text-slate-500">Stage</dt><dd>{OPPORTUNITY_STAGE_LABELS[opportunity.stage] || opportunity.stage}</dd></div>
          </dl>
        )}
      </div>
    </section>
  );
};

export default OpportunityDetail;
