import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { Archive, Building2, CheckCircle2, CircleAlert, Mail, Phone, Plus, RotateCcw, Search, Users, X } from 'lucide-react';
import PageHeader from '../../components/common/PageHeader';
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
  const [toast, setToast] = useState(null);

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

  useEffect(() => {
    if (!toast) return undefined;
    const timeoutId = window.setTimeout(() => setToast(null), 4000);
    return () => window.clearTimeout(timeoutId);
  }, [toast]);

  const handleChange = (e) => setForm((prev) => ({ ...prev, [e.target.name]: e.target.value }));

  const handleAddCustomer = async (e) => {
    e.preventDefault();
    try {
      await createCustomer(form);
      setForm(INITIAL_FORM);
      setShowForm(false);
      refresh();
      setToast({ type: 'success', message: `${form.companyName} was added to your customers.` });
    } catch (err) {
      setToast({
        type: 'error',
        message: err.response?.data?.message || 'Could not add this customer. Check the details and try again.',
      });
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
      setToast({
        type: 'success',
        message: `${customer.companyName} was ${customer.status === 'archived' ? 'restored' : 'archived'}.`,
      });
    } catch (err) {
      setToast({
        type: 'error',
        message: err.response?.data?.message || `Could not update ${customer.companyName}. Please try again.`,
      });
    }
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
        title="Customers"
        subtitle={`Accounts and contacts — ${SCOPE_LABELS[scopeFor(MODULES.CUSTOMERS)]}`}
        actions={
          canCreate && (
            <Button onClick={() => setShowForm((prev) => !prev)} icon={showForm ? X : Plus}>
              {showForm ? 'Close form' : 'Add customer'}
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
            aria-label="Close customer form"
            tabIndex={-1}
          />
          <div
            role="dialog"
            aria-modal="true"
            aria-labelledby="new-customer-title"
            className="relative z-10 my-auto w-full max-w-2xl overflow-hidden rounded-lg border border-white/70 bg-white shadow-2xl"
          >
            <form onSubmit={handleAddCustomer}>
              <div className="flex items-center gap-3 border-b border-slate-100 bg-slate-50/80 px-5 py-4 sm:px-6">
                <span className="grid size-10 place-items-center rounded-lg bg-violet-100 text-violet-700"><Building2 size={19} /></span>
                <div className="min-w-0 flex-1">
                  <h2 id="new-customer-title" className="text-base font-bold text-slate-900">New customer account</h2>
                  <p className="mt-0.5 text-xs text-slate-500">Add company details to your tenant directory.</p>
                </div>
                <button
                  type="button"
                  onClick={() => setShowForm(false)}
                  className="grid size-9 shrink-0 place-items-center rounded-md text-slate-400 transition hover:bg-slate-200/70 hover:text-slate-700 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary"
                  aria-label="Close customer form"
                >
                  <X size={18} />
                </button>
              </div>
              <div className="grid gap-4 p-5 sm:grid-cols-2 sm:p-6">
                <label className="space-y-1.5 text-sm font-medium text-slate-700">
                  Company name <span className="text-rose-500">*</span>
                  <input
                    autoFocus
                    type="text"
                    name="companyName"
                    placeholder="e.g. Acme Industries"
                    value={form.companyName}
                    onChange={handleChange}
                    className="h-10 w-full rounded-md border border-slate-200 bg-white px-3 text-sm font-normal text-slate-900 outline-none transition focus:border-violet-400 focus:ring-2 focus:ring-violet-100"
                    required
                  />
                </label>
                <label className="space-y-1.5 text-sm font-medium text-slate-700">
                  Industry
                  <select
                    name="industry"
                    value={form.industry}
                    onChange={handleChange}
                    className="h-10 w-full rounded-md border border-slate-200 bg-white px-3 text-sm font-normal text-slate-900 outline-none transition focus:border-violet-400 focus:ring-2 focus:ring-violet-100"
                  >
                    {INDUSTRY_OPTIONS.map((option) => (
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
                      name="email"
                      placeholder="company@example.com"
                      value={form.email}
                      onChange={handleChange}
                      className="h-10 w-full rounded-md border border-slate-200 bg-white pl-9 pr-3 text-sm font-normal text-slate-900 outline-none transition focus:border-violet-400 focus:ring-2 focus:ring-violet-100"
                    />
                  </span>
                </label>
                <label className="space-y-1.5 text-sm font-medium text-slate-700">
                  Phone <span className="font-normal text-slate-400">Optional</span>
                  <span className="relative block">
                    <Phone size={16} className="absolute left-3 top-1/2 -translate-y-1/2 text-slate-400" aria-hidden="true" />
                    <input
                      type="tel"
                      name="phone"
                      placeholder="+1 555 0100"
                      value={form.phone}
                      onChange={handleChange}
                      className="h-10 w-full rounded-md border border-slate-200 bg-white pl-9 pr-3 text-sm font-normal text-slate-900 outline-none transition focus:border-violet-400 focus:ring-2 focus:ring-violet-100"
                    />
                  </span>
                </label>
                <div className="flex flex-wrap justify-end gap-2 border-t border-slate-100 pt-4 sm:col-span-2">
                  <Button variant="outline" onClick={() => setShowForm(false)}>Cancel</Button>
                  <Button type="submit" icon={Plus}>Add customer</Button>
                </div>
              </div>
            </form>
          </div>
        </div>
      )}

      <section className="overflow-hidden rounded-lg border border-slate-200 bg-white shadow-sm">
        <div className="flex flex-col gap-4 border-b border-slate-100 p-4 sm:flex-row sm:items-center sm:justify-between">
          <div>
            <h2 className="text-base font-bold text-slate-900">Customer directory</h2>
            <p className="mt-1 text-xs text-slate-500">Browse and manage your company relationships.</p>
          </div>
          <div className="flex flex-wrap items-center gap-2">
            <label className="relative min-w-48 flex-1 sm:flex-none">
              <Search size={16} className="absolute left-3 top-1/2 -translate-y-1/2 text-slate-400" aria-hidden="true" />
              <span className="sr-only">Search by company name</span>
              <input
                type="search"
                placeholder="Search companies"
                value={search}
                onChange={(e) => setSearch(e.target.value)}
                className="h-9 w-full rounded-md border border-slate-200 bg-white pl-9 pr-3 text-sm outline-none focus:border-violet-400 focus:ring-2 focus:ring-violet-100"
              />
            </label>
            <label className="sr-only" htmlFor="customer-industry-filter">Filter by industry</label>
          <select
            id="customer-industry-filter"
            value={industryFilter}
            onChange={(e) => setIndustryFilter(e.target.value)}
            className="h-9 rounded-md border border-slate-200 bg-white px-3 text-sm text-slate-700 outline-none focus:border-violet-400 focus:ring-2 focus:ring-violet-100"
          >
            <option value="">All industries</option>
            {INDUSTRY_OPTIONS.map((option) => (
              <option key={option.value} value={option.value}>
                {option.label}
              </option>
            ))}
          </select>
            <label className="sr-only" htmlFor="customer-status-filter">Filter by status</label>
          <select
            id="customer-status-filter"
            value={statusFilter}
            onChange={(e) => setStatusFilter(e.target.value)}
            className="h-9 rounded-md border border-slate-200 bg-white px-3 text-sm text-slate-700 outline-none focus:border-violet-400 focus:ring-2 focus:ring-violet-100"
          >
            <option value="">All statuses</option>
            <option value="active">Active</option>
            <option value="archived">Archived</option>
          </select>
          </div>
        </div>

        {loading ? (
          <div className="space-y-3 p-5" role="status" aria-label="Loading customers">
            {[0, 1, 2].map((row) => <div key={row} className="h-12 animate-pulse rounded-md bg-slate-100" />)}
          </div>
        ) : customers.length === 0 ? (
          <div className="px-5 py-14 text-center">
            <span className="mx-auto grid size-12 place-items-center rounded-xl bg-violet-50 text-violet-700"><Users size={22} /></span>
            <h3 className="mt-4 text-base font-bold text-slate-900">{search || industryFilter || statusFilter ? 'No matching customers' : 'No customers yet'}</h3>
            <p className="mx-auto mt-1 max-w-md text-sm text-slate-500">
              {search || industryFilter || statusFilter
                ? 'Try changing your search or filters to see more accounts.'
                : canCreate
                  ? 'Add your first customer account to start tracking contacts, opportunities, and service history.'
                  : 'Customer accounts you have access to will appear here.'}
            </p>
            {canCreate && !search && !industryFilter && !statusFilter && (
              <Button className="mt-5" onClick={() => setShowForm(true)} icon={Plus}>Add first customer</Button>
            )}
          </div>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full min-w-[680px] text-left text-sm">
              <thead className="bg-slate-50 text-xs uppercase text-slate-500">
                <tr>
                  <th className="px-5 py-3 font-semibold">Company</th>
                  <th className="px-4 py-3 font-semibold">Industry</th>
                  <th className="px-4 py-3 font-semibold">Owner</th>
                  <th className="px-4 py-3 font-semibold">Status</th>
                  <th className="px-4 py-3 font-semibold">Contacts</th>
                  {canArchive && <th className="px-4 py-3" aria-label="Actions" />}
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100">
                {customers.map((c) => (
                  <tr key={c.id} className="transition-colors hover:bg-slate-50/70">
                    <td className="px-5 py-3.5">
                      <div className="flex items-center gap-3">
                        <span className="grid size-9 shrink-0 place-items-center rounded-lg bg-violet-50 text-sm font-bold text-violet-700">
                          {c.companyName?.trim()?.charAt(0)?.toUpperCase() || <Building2 size={17} />}
                        </span>
                        <Link
                          to={RoutePath.EDIT_CUSTOMER.replace(':id', c.id)}
                          className="font-semibold text-slate-800 hover:text-violet-700 hover:underline"
                        >
                          {c.companyName}
                        </Link>
                      </div>
                    </td>
                    <td className="px-4 py-3.5 text-slate-600">{INDUSTRY_LABELS[c.industry] || c.industry}</td>
                    <td className="px-4 py-3.5 text-slate-600">{c.ownerName || '—'}</td>
                    <td className="px-4 py-3.5">
                      <span className={`inline-flex items-center gap-1.5 rounded-full px-2.5 py-1 text-xs font-semibold ${c.status === 'archived' ? 'bg-slate-100 text-slate-600' : 'bg-emerald-50 text-emerald-700'}`}>
                        <span className={`size-1.5 rounded-full ${c.status === 'archived' ? 'bg-slate-400' : 'bg-emerald-500'}`} />
                        {c.status === 'archived' ? 'Archived' : 'Active'}
                      </span>
                    </td>
                    <td className="px-4 py-3.5 text-slate-600">{c.contacts?.length ?? 0}</td>
                    {canArchive && (
                      <td className="px-4 py-3.5 text-right">
                        <button
                          type="button"
                          onClick={() => handleArchiveToggle(c)}
                          className="inline-flex items-center gap-1.5 rounded-md px-2.5 py-1.5 text-xs font-semibold text-slate-500 transition hover:bg-slate-100 hover:text-slate-800 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary"
                        >
                          {c.status === 'archived' ? <RotateCcw size={14} /> : <Archive size={14} />}
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
        {!loading && customers.length > 0 && (
          <div className="border-t border-slate-100 px-5 py-3 text-xs text-slate-500">
            Showing {customers.length} {customers.length === 1 ? 'account' : 'accounts'}
          </div>
        )}
      </section>
    </section>
  );
};

export default Customers;
