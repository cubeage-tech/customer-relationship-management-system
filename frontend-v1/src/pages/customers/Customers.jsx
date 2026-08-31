import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import PageHeader from '../../components/common/PageHeader';
import EmptyState from '../../components/common/EmptyState';
import Button from '../../components/common/Button';
import { usePermissions } from '../../core/hooks/usePermissions';
import { MODULES, PERMISSIONS, SCOPE_LABELS } from '../../core/constants/permission.constant';
import { INDUSTRY_LABELS, INDUSTRY_OPTIONS } from '../../core/constants/app.constant';
import RoutePath from '../../core/constants/routes.constant';
import {
  listCustomers,
  createCustomer,
  archiveCustomer,
  restoreCustomer,
} from '../../core/services/customer.service';

const INITIAL_FORM = { companyName: '', industry: INDUSTRY_OPTIONS[0].value, email: '', phone: '' };

const Customers = () => {
  const { can, scopeFor } = usePermissions();
  const canCreate = can(PERMISSIONS.CUSTOMERS_CREATE);
  const canArchive = can(PERMISSIONS.CUSTOMERS_DELETE);

  const [customers, setCustomers] = useState([]);
  const [loading, setLoading] = useState(true);
  const [industryFilter, setIndustryFilter] = useState('');
  const [statusFilter, setStatusFilter] = useState('');
  const [search, setSearch] = useState('');
  const [showForm, setShowForm] = useState(false);
  const [form, setForm] = useState(INITIAL_FORM);
  const [error, setError] = useState('');

  const refresh = () => {
    listCustomers({ industry: industryFilter, status: statusFilter, search })
      .then((data) => setCustomers(data ?? []))
      .catch(() => setCustomers([]))
      .finally(() => setLoading(false));
  };

  useEffect(() => {
    refresh();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [industryFilter, statusFilter, search]);

  const handleChange = (e) => setForm((prev) => ({ ...prev, [e.target.name]: e.target.value }));

  const handleAddCustomer = async (e) => {
    e.preventDefault();
    setError('');
    try {
      await createCustomer(form);
      setForm(INITIAL_FORM);
      setShowForm(false);
      refresh();
    } catch (err) {
      setError(err.response?.data?.message || 'Could not add this customer. Check the details and try again.');
    }
  };

  const handleArchiveToggle = async (customer) => {
    try {
      if (customer.status === 'archived') {
        await restoreCustomer(customer.id);
      } else {
        await archiveCustomer(customer.id);
      }
      refresh();
    } catch {
      // Surfacing a toast here would need a notification system this app doesn't have yet —
      // a failed archive/restore just leaves the row as-is on refresh.
    }
  };

  return (
    <section>
      <PageHeader
        title="Customers"
        subtitle={`Accounts and contacts — ${SCOPE_LABELS[scopeFor(MODULES.CUSTOMERS)]}`}
        actions={
          canCreate && (
            <Button onClick={() => setShowForm((prev) => !prev)}>
              {showForm ? 'Cancel' : 'Add customer'}
            </Button>
          )
        }
      />

      {canCreate && showForm && (
        <form
          onSubmit={handleAddCustomer}
          className="mb-6 grid gap-3 sm:grid-cols-2 border border-slate-300 rounded p-4"
        >
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
          <input
            type="email"
            name="email"
            placeholder="Company email (optional)"
            value={form.email}
            onChange={handleChange}
            className="border border-slate-300 rounded px-2 py-1"
          />
          <input
            type="text"
            name="phone"
            placeholder="Phone (optional)"
            value={form.phone}
            onChange={handleChange}
            className="border border-slate-300 rounded px-2 py-1"
          />

          {error && <p className="form-error sm:col-span-2">{error}</p>}
          <div className="sm:col-span-2">
            <Button type="submit">Add customer</Button>
          </div>
        </form>
      )}

      <div className="mb-4 flex flex-wrap gap-4">
        <input
          type="text"
          placeholder="Search by company name"
          value={search}
          onChange={(e) => setSearch(e.target.value)}
          className="border border-slate-300 rounded px-2 py-1 text-sm"
        />
        <label className="text-sm text-slate-600">
          Industry
          <select
            value={industryFilter}
            onChange={(e) => setIndustryFilter(e.target.value)}
            className="ml-2 border border-slate-300 rounded px-2 py-1"
          >
            <option value="">All industries</option>
            {INDUSTRY_OPTIONS.map((option) => (
              <option key={option.value} value={option.value}>
                {option.label}
              </option>
            ))}
          </select>
        </label>
        <label className="text-sm text-slate-600">
          Status
          <select
            value={statusFilter}
            onChange={(e) => setStatusFilter(e.target.value)}
            className="ml-2 border border-slate-300 rounded px-2 py-1"
          >
            <option value="">All statuses</option>
            <option value="active">Active</option>
            <option value="archived">Archived</option>
          </select>
        </label>
      </div>

      {loading ? null : customers.length === 0 ? (
        <EmptyState
          title="No customers yet"
          message={
            canCreate
              ? 'Add your first customer account to start tracking contacts, opportunities and service history.'
              : 'Customer accounts you have access to will be listed here.'
          }
        />
      ) : (
        <div className="overflow-x-auto">
          <table className="w-full text-left text-sm border-collapse">
            <thead>
              <tr className="border-b border-slate-300">
                <th className="py-2 pr-4">Company</th>
                <th className="py-2 pr-4">Industry</th>
                <th className="py-2 pr-4">Owner</th>
                <th className="py-2 pr-4">Status</th>
                <th className="py-2 pr-4">Contacts</th>
                {canArchive && <th className="py-2 pr-4"></th>}
              </tr>
            </thead>
            <tbody>
              {customers.map((c) => (
                <tr key={c.id} className="border-b border-slate-100">
                  <td className="py-2 pr-4">
                    <Link
                      to={RoutePath.EDIT_CUSTOMER.replace(':id', c.id)}
                      className="text-indigo-600 hover:underline font-medium"
                    >
                      {c.companyName}
                    </Link>
                  </td>
                  <td className="py-2 pr-4">{INDUSTRY_LABELS[c.industry] || c.industry}</td>
                  <td className="py-2 pr-4">{c.ownerName || '—'}</td>
                  <td className="py-2 pr-4 capitalize">{c.status}</td>
                  <td className="py-2 pr-4">{c.contacts?.length ?? 0}</td>
                  {canArchive && (
                    <td className="py-2 pr-4">
                      <button
                        type="button"
                        onClick={() => handleArchiveToggle(c)}
                        className="text-xs text-slate-500 hover:text-slate-800 hover:underline"
                      >
                        {c.status === 'archived' ? 'Restore' : 'Archive'}
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

export default Customers;
