import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import PageHeader from '../../components/common/PageHeader';
import EmptyState from '../../components/common/EmptyState';
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
  const [error, setError] = useState('');

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

  const handleChange = (e) => setForm((prev) => ({ ...prev, [e.target.name]: e.target.value }));

  const handleAddOpportunity = async (e) => {
    e.preventDefault();
    setError('');
    try {
      await createOpportunity(form);
      setForm(INITIAL_FORM);
      setShowForm(false);
      refresh();
    } catch (err) {
      setError(err.response?.data?.message || 'Could not add this opportunity. Check the details and try again.');
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
    <section>
      <PageHeader
        title="Opportunities"
        subtitle={`Deals in the sales pipeline — ${SCOPE_LABELS[scopeFor(MODULES.OPPORTUNITIES)]}`}
        actions={
          canCreate && (
            <Button onClick={() => setShowForm((prev) => !prev)}>
              {showForm ? 'Cancel' : 'Add opportunity'}
            </Button>
          )
        }
      />

      {/* FR-3.3: cumulative deal value per stage */}
      {summary.length > 0 && (
        <div className="mb-6 grid gap-3 sm:grid-cols-5">
          {summary.map((s) => (
            <div key={s.stage} className="bg-white border border-slate-200 rounded-lg p-3">
              <p className="text-xs text-slate-500">{OPPORTUNITY_STAGE_LABELS[s.stage] || s.stage}</p>
              <p className="text-lg font-semibold text-slate-900">{formatCurrency(s.totalValue)}</p>
              <p className="text-xs text-slate-400">{s.count} deal{s.count === 1 ? '' : 's'}</p>
            </div>
          ))}
        </div>
      )}

      {canCreate && showForm && (
        <form
          onSubmit={handleAddOpportunity}
          className="mb-6 grid gap-3 sm:grid-cols-2 border border-slate-300 rounded p-4"
        >
          <select
            name="customerId"
            value={form.customerId}
            onChange={handleChange}
            className="border border-slate-300 rounded px-2 py-1"
            required
          >
            <option value="">Select customer</option>
            {customers.map((c) => (
              <option key={c.id} value={c.id}>
                {c.companyName}
              </option>
            ))}
          </select>
          <input
            type="text"
            name="productService"
            placeholder="Product / service"
            value={form.productService}
            onChange={handleChange}
            className="border border-slate-300 rounded px-2 py-1"
          />
          <input
            type="number"
            name="dealValue"
            placeholder="Deal value"
            value={form.dealValue}
            onChange={handleChange}
            min="0"
            step="0.01"
            className="border border-slate-300 rounded px-2 py-1"
            required
          />
          <input
            type="date"
            name="expectedClosingDate"
            value={form.expectedClosingDate}
            onChange={handleChange}
            className="border border-slate-300 rounded px-2 py-1"
          />

          {error && <p className="form-error sm:col-span-2">{error}</p>}
          <div className="sm:col-span-2">
            <Button type="submit">Add opportunity</Button>
          </div>
        </form>
      )}

      <div className="mb-4 flex flex-wrap gap-4">
        <input
          type="text"
          placeholder="Search by customer or product"
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
            {OPPORTUNITY_STAGE_OPTIONS.map((option) => (
              <option key={option.value} value={option.value}>
                {option.label}
              </option>
            ))}
          </select>
        </label>
      </div>

      {loading ? null : opportunities.length === 0 ? (
        <EmptyState
          title="No opportunities yet"
          message={
            canCreate
              ? 'Add your first deal to start tracking it through the sales pipeline.'
              : 'Opportunities you have access to will be listed here.'
          }
        />
      ) : (
        <div className="overflow-x-auto">
          <table className="w-full text-left text-sm border-collapse">
            <thead>
              <tr className="border-b border-slate-300">
                <th className="py-2 pr-4">Customer</th>
                <th className="py-2 pr-4">Product / service</th>
                <th className="py-2 pr-4">Value</th>
                <th className="py-2 pr-4">Expected close</th>
                <th className="py-2 pr-4">Stage</th>
                <th className="py-2 pr-4">Owner</th>
                {canDelete && <th className="py-2 pr-4"></th>}
              </tr>
            </thead>
            <tbody>
              {opportunities.map((o) => (
                <tr key={o.id} className="border-b border-slate-100">
                  <td className="py-2 pr-4">
                    <Link
                      to={RoutePath.EDIT_OPPORTUNITY.replace(':id', o.id)}
                      className="text-indigo-600 hover:underline font-medium"
                    >
                      {o.customerName}
                    </Link>
                  </td>
                  <td className="py-2 pr-4">{o.productService || '—'}</td>
                  <td className="py-2 pr-4">{formatCurrency(o.dealValue)}</td>
                  <td className="py-2 pr-4">
                    {o.expectedClosingDate ? new Date(o.expectedClosingDate).toLocaleDateString() : '—'}
                  </td>
                  <td className="py-2 pr-4">
                    {canEdit ? (
                      <select
                        value={o.stage}
                        onChange={(e) => handleStageChange(o, e.target.value)}
                        className="border border-slate-300 rounded px-2 py-1 text-xs"
                      >
                        {OPPORTUNITY_STAGE_OPTIONS.map((option) => (
                          <option key={option.value} value={option.value}>
                            {option.label}
                          </option>
                        ))}
                      </select>
                    ) : (
                      OPPORTUNITY_STAGE_LABELS[o.stage] || o.stage
                    )}
                  </td>
                  <td className="py-2 pr-4">{o.ownerName || '—'}</td>
                  {canDelete && (
                    <td className="py-2 pr-4">
                      <button
                        type="button"
                        onClick={() => handleDelete(o)}
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

export default Opportunities;
