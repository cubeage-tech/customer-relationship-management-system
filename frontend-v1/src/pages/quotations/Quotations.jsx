import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { CalendarDays, CheckCircle2, CircleAlert, FileText, Package, Plus, Search, Trash2, UserRound, X } from 'lucide-react';
import PageHeader from '../../components/common/PageHeader';
import Button from '../../components/common/Button';
import { usePermissions } from '../../core/hooks/usePermissions';
import { MODULES, PERMISSIONS, SCOPE_LABELS } from '../../core/constants/permission.constant';
import {
  QUOTATION_STATUS_LABELS,
  DISCOUNT_APPROVAL_STATUS_LABELS,
  USER_ROLES,
} from '../../core/constants/app.constant';
import RoutePath from '../../core/constants/routes.constant';
import { listQuotations, createQuotation } from '../../core/services/quotation.service';
import { apiErrorMessage } from '../../core/utils/apiError';
import { listCustomers } from '../../core/services/customer.service';
import { listOpportunities } from '../../core/services/opportunity.service';
import { listProducts, createProduct } from '../../core/services/product.service';

const formatCurrency = (value) => `₹${Number(value ?? 0).toLocaleString('en-IN')}`;

const EMPTY_LINE_ITEM = { productName: '', quantity: 1, unitPrice: '', discountPercent: 0 };

const INITIAL_FORM = { customerId: '', opportunityId: '', validUntil: '', notes: '' };
const INITIAL_PRODUCT_FORM = { name: '', description: '', unitPrice: '' };

const Quotations = () => {
  const { can, is, scopeFor } = usePermissions();
  const canCreate = can(PERMISSIONS.QUOTATIONS_CREATE);
  // FR-4.2's price list is managed by admin/sales_manager — the same roles the backend
  // gates ProductController's write endpoints to; no dedicated permission key exists for it.
  const canManageProducts = is([USER_ROLES.ADMIN, USER_ROLES.SALES_MANAGER]);

  const [quotations, setQuotations] = useState([]);
  const [customers, setCustomers] = useState([]);
  const [opportunities, setOpportunities] = useState([]);
  const [products, setProducts] = useState([]);
  const [loading, setLoading] = useState(true);
  const [statusFilter, setStatusFilter] = useState('');
  const [search, setSearch] = useState('');

  const [showForm, setShowForm] = useState(false);
  const [form, setForm] = useState(INITIAL_FORM);
  const [lineItems, setLineItems] = useState([{ ...EMPTY_LINE_ITEM }]);
  const [toast, setToast] = useState(null);
  const [loadError, setLoadError] = useState('');

  const [showProductForm, setShowProductForm] = useState(false);
  const [productForm, setProductForm] = useState(INITIAL_PRODUCT_FORM);

  const refresh = () => {
    listQuotations({ status: statusFilter, search })
      .then((data) => {
        setQuotations(data ?? []);
        setLoadError('');
      })
      .catch((err) => {
        setQuotations([]);
        setLoadError(apiErrorMessage(err));
      })
      .finally(() => setLoading(false));
  };

  const refreshProducts = () => listProducts().then((data) => setProducts(data ?? [])).catch(() => setProducts([]));

  useEffect(() => {
    refresh();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [statusFilter, search]);

  useEffect(() => {
    if (canCreate) {
      listCustomers({}).then((data) => setCustomers(data ?? [])).catch(() => setCustomers([]));
      listOpportunities({}).then((data) => setOpportunities(data ?? [])).catch(() => setOpportunities([]));
      refreshProducts();
    }
  }, [canCreate]);

  useEffect(() => {
    if (!toast) return undefined;
    const timeoutId = window.setTimeout(() => setToast(null), 4000);
    return () => window.clearTimeout(timeoutId);
  }, [toast]);

  const handleFormChange = (e) => setForm((prev) => ({ ...prev, [e.target.name]: e.target.value }));

  const handleLineItemChange = (index, field, value) => {
    setLineItems((prev) => prev.map((item, i) => (i === index ? { ...item, [field]: value } : item)));
  };

  const handlePickProduct = (index, productId) => {
    const product = products.find((p) => String(p.id) === productId);
    if (!product) return;
    setLineItems((prev) =>
      prev.map((item, i) => (i === index ? { ...item, productName: product.name, unitPrice: product.unitPrice } : item))
    );
  };

  const addLineItem = () => setLineItems((prev) => [...prev, { ...EMPTY_LINE_ITEM }]);
  const removeLineItem = (index) => setLineItems((prev) => prev.filter((_, i) => i !== index));

  const handleAddQuotation = async (e) => {
    e.preventDefault();
    try {
      await createQuotation({
        ...form,
        opportunityId: form.opportunityId || null,
        lineItems: lineItems.map((item) => ({
          ...item,
          quantity: Number(item.quantity),
          unitPrice: Number(item.unitPrice),
          discountPercent: Number(item.discountPercent) || 0,
        })),
      });
      setForm(INITIAL_FORM);
      setLineItems([{ ...EMPTY_LINE_ITEM }]);
      setShowForm(false);
      refresh();
      setToast({ type: 'success', message: 'Quotation created successfully.' });
    } catch (err) {
      setToast({
        type: 'error',
        message: err.response?.data?.message || 'Could not create this quotation. Check the details and try again.',
      });
    }
  };

  const handleAddProduct = async (e) => {
    e.preventDefault();
    try {
      await createProduct({ ...productForm, unitPrice: Number(productForm.unitPrice) });
      setProductForm(INITIAL_PRODUCT_FORM);
      refreshProducts();
      setToast({ type: 'success', message: `${productForm.name} was added to the product catalog.` });
    } catch (err) {
      setToast({
        type: 'error',
        message: err.response?.data?.message || 'Could not add this product. Please try again.',
      });
    }
  };

  const customerOpportunities = form.customerId
    ? opportunities.filter((o) => String(o.customerId) === String(form.customerId))
    : opportunities;
  const estimatedSubtotal = lineItems.reduce((total, item) => (
    total + Number(item.quantity || 0) * Number(item.unitPrice || 0) * (1 - (Number(item.discountPercent) || 0) / 100)
  ), 0);

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
        title="Quotations"
        subtitle={`Quotes raised against opportunities — ${SCOPE_LABELS[scopeFor(MODULES.QUOTATIONS)]}`}
        actions={
          canCreate && (
            <Button onClick={() => setShowForm((prev) => !prev)} icon={showForm ? X : Plus}>
              {showForm ? 'Close form' : 'Add quotation'}
            </Button>
          )
        }
      />

      {canManageProducts && (
        <section className="overflow-hidden rounded-lg border border-slate-200 bg-white shadow-sm">
          <div className="flex flex-wrap items-center justify-between gap-3 border-b border-slate-100 px-5 py-4">
            <div className="flex items-center gap-3">
              <span className="grid size-9 place-items-center rounded-lg bg-emerald-50 text-emerald-700"><Package size={18} /></span>
              <div>
                <h2 className="text-sm font-bold text-slate-900">Product price list</h2>
                <p className="mt-0.5 text-xs text-slate-500">Reusable products for quotation line items.</p>
              </div>
            </div>
            <Button variant="outline" size="sm" onClick={() => setShowProductForm((prev) => !prev)} icon={showProductForm ? X : Plus}>
              {showProductForm ? 'Close' : 'Manage products'}
            </Button>
          </div>

          {showProductForm && (
            <div className="p-5">
              <form onSubmit={handleAddProduct} className="grid gap-3 rounded-md bg-slate-50 p-4 sm:grid-cols-2 xl:grid-cols-[1fr_1.4fr_0.7fr_auto]">
                <label className="space-y-1 text-xs font-semibold text-slate-600">Product name
                  <input type="text" placeholder="e.g. Professional plan" value={productForm.name} onChange={(e) => setProductForm((prev) => ({ ...prev, name: e.target.value }))} className="h-9 w-full rounded-md border border-slate-200 bg-white px-3 text-sm font-normal text-slate-900 outline-none focus:border-violet-400 focus:ring-2 focus:ring-violet-100" required />
                </label>
                <label className="space-y-1 text-xs font-semibold text-slate-600">Description <span className="font-normal">Optional</span>
                  <input type="text" placeholder="Short product description" value={productForm.description} onChange={(e) => setProductForm((prev) => ({ ...prev, description: e.target.value }))} className="h-9 w-full rounded-md border border-slate-200 bg-white px-3 text-sm font-normal text-slate-900 outline-none focus:border-violet-400 focus:ring-2 focus:ring-violet-100" />
                </label>
                <label className="space-y-1 text-xs font-semibold text-slate-600">Unit price
                  <input type="number" placeholder="0.00" value={productForm.unitPrice} onChange={(e) => setProductForm((prev) => ({ ...prev, unitPrice: e.target.value }))} min="0" step="0.01" className="h-9 w-full rounded-md border border-slate-200 bg-white px-3 text-sm font-normal text-slate-900 outline-none focus:border-violet-400 focus:ring-2 focus:ring-violet-100" required />
                </label>
                <div className="flex items-end"><Button type="submit" size="sm" icon={Plus}>Add product</Button></div>
              </form>

              <ul className="mt-4 divide-y divide-slate-100 text-sm text-slate-700">
                {products.map((p) => (
                  <li key={p.id} className="flex items-center justify-between gap-4 py-3">
                    <span className="flex min-w-0 items-center gap-2 font-medium"><Package size={15} className="shrink-0 text-slate-400" />{p.name}{!p.active && <span className="text-xs font-normal text-slate-400">Inactive</span>}</span>
                    <span className="shrink-0 font-semibold">{formatCurrency(p.unitPrice)}</span>
                  </li>
                ))}
              </ul>
            </div>
          )}
        </section>
      )}

      {canCreate && showForm && (
        <div className="fixed inset-0 z-40 flex items-center justify-center overflow-y-auto p-3 sm:p-6" onKeyDown={(event) => {
          if (event.key === 'Escape') setShowForm(false);
        }}>
          <button type="button" className="fixed inset-0 bg-slate-950/45 backdrop-blur-sm" onClick={() => setShowForm(false)} aria-label="Close quotation form" tabIndex={-1} />
          <div role="dialog" aria-modal="true" aria-labelledby="new-quotation-title" className="relative z-10 my-auto max-h-[94vh] w-full max-w-4xl overflow-y-auto rounded-lg border border-white/70 bg-white shadow-2xl">
            <form onSubmit={handleAddQuotation}>
              <div className="sticky top-0 z-10 flex items-center gap-3 border-b border-slate-100 bg-white/95 px-5 py-4 backdrop-blur sm:px-6">
                <span className="grid size-10 place-items-center rounded-lg bg-violet-100 text-violet-700"><FileText size={19} /></span>
                <div className="min-w-0 flex-1">
                  <h2 id="new-quotation-title" className="text-base font-bold text-slate-900">Create a quotation</h2>
                  <p className="mt-0.5 text-xs text-slate-500">Choose a customer and build a clear, itemized quote.</p>
                </div>
                <button type="button" onClick={() => setShowForm(false)} className="grid size-9 shrink-0 place-items-center rounded-md text-slate-400 transition hover:bg-slate-100 hover:text-slate-700 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary" aria-label="Close quotation form"><X size={18} /></button>
              </div>

              <div className="space-y-6 p-5 sm:p-6">
                <div className="grid gap-4 rounded-md border border-slate-100 bg-slate-50/70 p-4 sm:grid-cols-2 lg:grid-cols-3">
                  <label className="space-y-1.5 text-sm font-medium text-slate-700">Customer <span className="text-rose-500">*</span>
                    <select autoFocus name="customerId" value={form.customerId} onChange={handleFormChange} className="h-10 w-full rounded-md border border-slate-200 bg-white px-3 text-sm font-normal text-slate-900 outline-none focus:border-violet-400 focus:ring-2 focus:ring-violet-100" required>
                      <option value="">Select customer</option>
                      {customers.map((c) => <option key={c.id} value={c.id}>{c.companyName}</option>)}
                    </select>
                  </label>
                  <label className="space-y-1.5 text-sm font-medium text-slate-700">Linked opportunity <span className="font-normal text-slate-400">Optional</span>
                    <select name="opportunityId" value={form.opportunityId} onChange={handleFormChange} className="h-10 w-full rounded-md border border-slate-200 bg-white px-3 text-sm font-normal text-slate-900 outline-none focus:border-violet-400 focus:ring-2 focus:ring-violet-100">
                      <option value="">No linked opportunity</option>
                      {customerOpportunities.map((o) => <option key={o.id} value={o.id}>{o.customerName} — {o.productService || 'Opportunity'} #{o.id}</option>)}
                    </select>
                  </label>
                  <label className="space-y-1.5 text-sm font-medium text-slate-700">Valid until <span className="font-normal text-slate-400">Optional</span>
                    <span className="relative block"><CalendarDays size={16} className="absolute left-3 top-1/2 -translate-y-1/2 text-slate-400" aria-hidden="true" /><input type="date" name="validUntil" value={form.validUntil} onChange={handleFormChange} className="h-10 w-full rounded-md border border-slate-200 bg-white pl-9 pr-3 text-sm font-normal text-slate-900 outline-none focus:border-violet-400 focus:ring-2 focus:ring-violet-100" /></span>
                  </label>
                </div>

                <section aria-labelledby="quotation-items-title">
                  <div className="mb-3 flex flex-wrap items-center justify-between gap-3">
                    <div>
                      <h3 id="quotation-items-title" className="text-sm font-bold text-slate-900">Line items</h3>
                      <p className="mt-1 text-xs text-slate-500">Add products, quantities, pricing, and any discount.</p>
                    </div>
                    <Button type="button" variant="outline" size="sm" onClick={addLineItem} icon={Plus}>Add line item</Button>
                  </div>
                  <div className="space-y-3">
                    {lineItems.map((item, index) => (
                      <div key={index} className="grid grid-cols-2 gap-3 rounded-md border border-slate-200 bg-white p-3 sm:grid-cols-6 sm:p-4">
                        {products.length > 0 && (
                          <label className="col-span-2 space-y-1 text-xs font-semibold text-slate-600 sm:col-span-2">Catalog product
                            <select onChange={(e) => handlePickProduct(index, e.target.value)} className="h-9 w-full rounded-md border border-slate-200 bg-white px-2 text-sm font-normal text-slate-800 outline-none focus:border-violet-400 focus:ring-2 focus:ring-violet-100" defaultValue="">
                              <option value="" disabled>Choose product</option>
                              {products.filter((p) => p.active).map((p) => <option key={p.id} value={p.id}>{p.name}</option>)}
                            </select>
                          </label>
                        )}
                        <label className={`${products.length > 0 ? 'col-span-2 sm:col-span-2' : 'col-span-2 sm:col-span-3'} space-y-1 text-xs font-semibold text-slate-600`}>Product / service <span className="text-rose-500">*</span>
                          <input type="text" placeholder="Product or service" value={item.productName} onChange={(e) => handleLineItemChange(index, 'productName', e.target.value)} className="h-9 w-full rounded-md border border-slate-200 bg-white px-2 text-sm font-normal text-slate-800 outline-none focus:border-violet-400 focus:ring-2 focus:ring-violet-100" required />
                        </label>
                        <label className="space-y-1 text-xs font-semibold text-slate-600">Qty <span className="text-rose-500">*</span>
                          <input type="number" placeholder="1" value={item.quantity} onChange={(e) => handleLineItemChange(index, 'quantity', e.target.value)} min="0.01" step="0.01" className="h-9 w-full rounded-md border border-slate-200 bg-white px-2 text-sm font-normal text-slate-800 outline-none focus:border-violet-400 focus:ring-2 focus:ring-violet-100" required />
                        </label>
                        <label className="space-y-1 text-xs font-semibold text-slate-600">Unit price <span className="text-rose-500">*</span>
                          <input type="number" placeholder="0.00" value={item.unitPrice} onChange={(e) => handleLineItemChange(index, 'unitPrice', e.target.value)} min="0" step="0.01" className="h-9 w-full rounded-md border border-slate-200 bg-white px-2 text-sm font-normal text-slate-800 outline-none focus:border-violet-400 focus:ring-2 focus:ring-violet-100" required />
                        </label>
                        <div className="flex items-end gap-2">
                          <label className="min-w-0 flex-1 space-y-1 text-xs font-semibold text-slate-600">Discount %
                            <input type="number" placeholder="0" value={item.discountPercent} onChange={(e) => handleLineItemChange(index, 'discountPercent', e.target.value)} min="0" max="100" step="0.01" className="h-9 w-full rounded-md border border-slate-200 bg-white px-2 text-sm font-normal text-slate-800 outline-none focus:border-violet-400 focus:ring-2 focus:ring-violet-100" />
                          </label>
                          {lineItems.length > 1 && <button type="button" onClick={() => removeLineItem(index)} className="mb-0.5 grid size-9 shrink-0 place-items-center rounded-md text-slate-400 transition hover:bg-rose-50 hover:text-rose-600 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary" aria-label={`Remove line item ${index + 1}`}><Trash2 size={16} /></button>}
                        </div>
                      </div>
                    ))}
                  </div>
                </section>

                <label className="block space-y-1.5 text-sm font-medium text-slate-700">Notes <span className="font-normal text-slate-400">Optional</span>
                  <textarea name="notes" placeholder="Add terms or details for the customer" value={form.notes} onChange={handleFormChange} className="w-full resize-y rounded-md border border-slate-200 bg-white px-3 py-2 text-sm font-normal text-slate-900 outline-none focus:border-violet-400 focus:ring-2 focus:ring-violet-100" rows={3} />
                </label>

                <div className="flex flex-wrap items-center justify-between gap-3 border-t border-slate-100 pt-4">
                  <div><p className="text-xs text-slate-500">Estimated line-item subtotal</p><p className="mt-0.5 text-lg font-bold text-slate-900">{formatCurrency(estimatedSubtotal)}</p></div>
                  <div className="flex gap-2"><Button variant="outline" onClick={() => setShowForm(false)}>Cancel</Button><Button type="submit" icon={FileText}>Create quotation</Button></div>
                </div>
              </div>
            </form>
          </div>
        </div>
      )}

      <section className="overflow-hidden rounded-lg border border-slate-200 bg-white shadow-sm">
        <div className="flex flex-col gap-4 border-b border-slate-100 p-4 sm:flex-row sm:items-center sm:justify-between">
          <div><h2 className="text-base font-bold text-slate-900">Quotation register</h2><p className="mt-1 text-xs text-slate-500">Review quote totals, approval status, and ownership.</p></div>
          <div className="flex flex-wrap items-center gap-2">
            <label className="relative min-w-48 flex-1 sm:flex-none">
              <Search size={16} className="absolute left-3 top-1/2 -translate-y-1/2 text-slate-400" aria-hidden="true" />
              <span className="sr-only">Search by quotation number or customer</span>
              <input type="search" placeholder="Search quotations" value={search} onChange={(e) => setSearch(e.target.value)} className="h-9 w-full rounded-md border border-slate-200 bg-white pl-9 pr-3 text-sm outline-none focus:border-violet-400 focus:ring-2 focus:ring-violet-100" />
            </label>
            <label className="sr-only" htmlFor="quotation-status-filter">Filter by status</label>
          <select
            id="quotation-status-filter"
            value={statusFilter}
            onChange={(e) => setStatusFilter(e.target.value)}
            className="h-9 rounded-md border border-slate-200 bg-white px-3 text-sm text-slate-700 outline-none focus:border-violet-400 focus:ring-2 focus:ring-violet-100"
          >
            <option value="">All statuses</option>
            {Object.entries(QUOTATION_STATUS_LABELS).map(([value, label]) => (
              <option key={value} value={value}>{label}</option>
            ))}
          </select>
          </div>
        </div>

        {loading ? (
          <div className="space-y-3 p-5" role="status" aria-label="Loading quotations">{[0, 1, 2].map((row) => <div key={row} className="h-12 animate-pulse rounded-md bg-slate-100" />)}</div>
        ) : loadError ? (
          <div className="px-5 py-14 text-center"><span className="mx-auto grid size-12 place-items-center rounded-xl bg-rose-50 text-rose-700"><CircleAlert size={22} /></span><h3 className="mt-4 text-base font-bold text-slate-900">Couldn't load quotations</h3><p className="mx-auto mt-1 max-w-md text-sm text-slate-500">{loadError}</p><Button className="mt-5" variant="outline" onClick={() => { setLoading(true); refresh(); }}>Retry</Button></div>
        ) : quotations.length === 0 ? (
          <div className="px-5 py-14 text-center">
            <span className="mx-auto grid size-12 place-items-center rounded-xl bg-violet-50 text-violet-700"><FileText size={22} /></span>
            <h3 className="mt-4 text-base font-bold text-slate-900">{search || statusFilter ? 'No matching quotations' : 'No quotations yet'}</h3>
            <p className="mx-auto mt-1 max-w-md text-sm text-slate-500">{search || statusFilter ? 'Try changing your search or status filter.' : canCreate ? 'Create your first quotation to start tracking customer approval.' : 'Quotations you have access to will be listed here.'}</p>
            {canCreate && !search && !statusFilter && <Button className="mt-5" onClick={() => setShowForm(true)} icon={Plus}>Add first quotation</Button>}
          </div>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full min-w-[800px] text-left text-sm">
              <thead className="bg-slate-50 text-xs uppercase text-slate-500"><tr><th className="px-5 py-3 font-semibold">Quotation #</th><th className="px-4 py-3 font-semibold">Customer</th><th className="px-4 py-3 font-semibold">Total</th><th className="px-4 py-3 font-semibold">Status</th><th className="px-4 py-3 font-semibold">Discount approval</th><th className="px-4 py-3 font-semibold">Owner</th></tr></thead>
              <tbody className="divide-y divide-slate-100">
                {quotations.map((q) => (
                  <tr key={q.id} className="transition-colors hover:bg-slate-50/70">
                    <td className="px-5 py-3.5"><div className="flex items-center gap-3"><span className="grid size-9 shrink-0 place-items-center rounded-lg bg-violet-50 text-violet-700"><FileText size={17} /></span><Link to={RoutePath.EDIT_QUOTATION.replace(':id', q.id)} className="font-semibold text-slate-800 hover:text-violet-700 hover:underline">{q.quotationNumber}</Link></div></td>
                    <td className="px-4 py-3.5 text-slate-600">{q.customerName}</td>
                    <td className="px-4 py-3.5 font-semibold text-slate-800">{formatCurrency(q.grandTotal)}</td>
                    <td className="px-4 py-3.5"><span className={`inline-flex rounded-full px-2.5 py-1 text-xs font-semibold ${q.status === 'approved' ? 'bg-emerald-50 text-emerald-700' : ['rejected', 'expired'].includes(q.status) ? 'bg-rose-50 text-rose-700' : 'bg-blue-50 text-blue-700'}`}>{QUOTATION_STATUS_LABELS[q.status] || q.status}</span></td>
                    <td className={`px-4 py-3.5 ${q.discountApprovalStatus === 'pending' ? 'font-semibold text-amber-600' : 'text-slate-600'}`}>{DISCOUNT_APPROVAL_STATUS_LABELS[q.discountApprovalStatus] || q.discountApprovalStatus}</td>
                    <td className="px-4 py-3.5"><span className="inline-flex items-center gap-2 text-slate-600"><UserRound size={15} className="text-slate-400" />{q.ownerName || '—'}</span></td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
        {!loading && quotations.length > 0 && <div className="border-t border-slate-100 px-5 py-3 text-xs text-slate-500">Showing {quotations.length} {quotations.length === 1 ? 'quotation' : 'quotations'}</div>}
      </section>
    </section>
  );
};

export default Quotations;
