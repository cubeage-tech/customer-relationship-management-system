import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import PageHeader from '../../components/common/PageHeader';
import EmptyState from '../../components/common/EmptyState';
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
  const [error, setError] = useState('');

  const [showProductForm, setShowProductForm] = useState(false);
  const [productForm, setProductForm] = useState(INITIAL_PRODUCT_FORM);

  const refresh = () => {
    listQuotations({ status: statusFilter, search })
      .then((data) => setQuotations(data ?? []))
      .catch(() => setQuotations([]))
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
    setError('');
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
    } catch (err) {
      setError(err.response?.data?.message || 'Could not create this quotation. Check the details and try again.');
    }
  };

  const handleAddProduct = async (e) => {
    e.preventDefault();
    await createProduct({ ...productForm, unitPrice: Number(productForm.unitPrice) });
    setProductForm(INITIAL_PRODUCT_FORM);
    refreshProducts();
  };

  const customerOpportunities = form.customerId
    ? opportunities.filter((o) => String(o.customerId) === String(form.customerId))
    : opportunities;

  return (
    <section>
      <PageHeader
        title="Quotations"
        subtitle={`Quotes raised against opportunities — ${SCOPE_LABELS[scopeFor(MODULES.QUOTATIONS)]}`}
        actions={
          canCreate && (
            <Button onClick={() => setShowForm((prev) => !prev)}>
              {showForm ? 'Cancel' : 'Add quotation'}
            </Button>
          )
        }
      />

      {canManageProducts && (
        <div className="mb-6 bg-white border border-slate-200 rounded-lg p-4">
          <div className="flex items-center justify-between">
            <h2 className="font-semibold text-slate-900 text-sm">Product price list</h2>
            <button
              type="button"
              onClick={() => setShowProductForm((prev) => !prev)}
              className="text-xs text-indigo-600 hover:underline"
            >
              {showProductForm ? 'Close' : 'Manage products'}
            </button>
          </div>

          {showProductForm && (
            <>
              <form onSubmit={handleAddProduct} className="mt-3 grid gap-2 sm:grid-cols-4">
                <input
                  type="text"
                  placeholder="Product name"
                  value={productForm.name}
                  onChange={(e) => setProductForm((prev) => ({ ...prev, name: e.target.value }))}
                  className="border border-slate-300 rounded px-2 py-1 text-sm"
                  required
                />
                <input
                  type="text"
                  placeholder="Description (optional)"
                  value={productForm.description}
                  onChange={(e) => setProductForm((prev) => ({ ...prev, description: e.target.value }))}
                  className="border border-slate-300 rounded px-2 py-1 text-sm"
                />
                <input
                  type="number"
                  placeholder="Unit price"
                  value={productForm.unitPrice}
                  onChange={(e) => setProductForm((prev) => ({ ...prev, unitPrice: e.target.value }))}
                  min="0"
                  step="0.01"
                  className="border border-slate-300 rounded px-2 py-1 text-sm"
                  required
                />
                <Button type="submit" size="sm">Add product</Button>
              </form>

              <ul className="mt-3 text-xs text-slate-600 divide-y divide-slate-100">
                {products.map((p) => (
                  <li key={p.id} className="py-1.5 flex justify-between">
                    <span>{p.name}{!p.active && ' (inactive)'}</span>
                    <span>{formatCurrency(p.unitPrice)}</span>
                  </li>
                ))}
              </ul>
            </>
          )}
        </div>
      )}

      {canCreate && showForm && (
        <form onSubmit={handleAddQuotation} className="mb-6 border border-slate-300 rounded p-4">
          <div className="grid gap-3 sm:grid-cols-3 mb-4">
            <select
              name="customerId"
              value={form.customerId}
              onChange={handleFormChange}
              className="border border-slate-300 rounded px-2 py-1"
              required
            >
              <option value="">Select customer</option>
              {customers.map((c) => (
                <option key={c.id} value={c.id}>{c.companyName}</option>
              ))}
            </select>
            <select
              name="opportunityId"
              value={form.opportunityId}
              onChange={handleFormChange}
              className="border border-slate-300 rounded px-2 py-1"
            >
              <option value="">No linked opportunity</option>
              {customerOpportunities.map((o) => (
                <option key={o.id} value={o.id}>{o.customerName} — {o.productService || 'Opportunity'} #{o.id}</option>
              ))}
            </select>
            <input
              type="date"
              name="validUntil"
              value={form.validUntil}
              onChange={handleFormChange}
              className="border border-slate-300 rounded px-2 py-1"
              placeholder="Valid until"
            />
          </div>

          <h3 className="text-sm font-semibold text-slate-700 mb-2">Line items</h3>
          <div className="space-y-2 mb-3">
            {lineItems.map((item, index) => (
              <div key={index} className="grid gap-2 sm:grid-cols-6 items-center">
                {products.length > 0 && (
                  <select
                    onChange={(e) => handlePickProduct(index, e.target.value)}
                    className="border border-slate-300 rounded px-2 py-1 text-sm"
                    defaultValue=""
                  >
                    <option value="" disabled>From catalog…</option>
                    {products.filter((p) => p.active).map((p) => (
                      <option key={p.id} value={p.id}>{p.name}</option>
                    ))}
                  </select>
                )}
                <input
                  type="text"
                  placeholder="Product / service"
                  value={item.productName}
                  onChange={(e) => handleLineItemChange(index, 'productName', e.target.value)}
                  className="border border-slate-300 rounded px-2 py-1 text-sm"
                  required
                />
                <input
                  type="number"
                  placeholder="Qty"
                  value={item.quantity}
                  onChange={(e) => handleLineItemChange(index, 'quantity', e.target.value)}
                  min="0.01"
                  step="0.01"
                  className="border border-slate-300 rounded px-2 py-1 text-sm"
                  required
                />
                <input
                  type="number"
                  placeholder="Unit price"
                  value={item.unitPrice}
                  onChange={(e) => handleLineItemChange(index, 'unitPrice', e.target.value)}
                  min="0"
                  step="0.01"
                  className="border border-slate-300 rounded px-2 py-1 text-sm"
                  required
                />
                <input
                  type="number"
                  placeholder="Discount %"
                  value={item.discountPercent}
                  onChange={(e) => handleLineItemChange(index, 'discountPercent', e.target.value)}
                  min="0"
                  max="100"
                  step="0.01"
                  className="border border-slate-300 rounded px-2 py-1 text-sm"
                />
                {lineItems.length > 1 && (
                  <button type="button" onClick={() => removeLineItem(index)} className="text-xs text-red-600 hover:underline">
                    Remove
                  </button>
                )}
              </div>
            ))}
          </div>
          <button type="button" onClick={addLineItem} className="text-xs text-indigo-600 hover:underline mb-4">
            + Add line item
          </button>

          <textarea
            name="notes"
            placeholder="Notes (optional)"
            value={form.notes}
            onChange={handleFormChange}
            className="border border-slate-300 rounded px-2 py-1 w-full text-sm mb-3"
            rows={2}
          />

          {error && <p className="form-error mb-3">{error}</p>}
          <Button type="submit">Create quotation</Button>
        </form>
      )}

      <div className="mb-4 flex flex-wrap gap-4">
        <input
          type="text"
          placeholder="Search by quotation # or customer"
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
            {Object.entries(QUOTATION_STATUS_LABELS).map(([value, label]) => (
              <option key={value} value={value}>{label}</option>
            ))}
          </select>
        </label>
      </div>

      {loading ? null : quotations.length === 0 ? (
        <EmptyState
          title="No quotations yet"
          message={
            canCreate
              ? 'Create your first quotation to start tracking customer approval.'
              : 'Quotations you have access to will be listed here.'
          }
        />
      ) : (
        <div className="overflow-x-auto">
          <table className="w-full text-left text-sm border-collapse">
            <thead>
              <tr className="border-b border-slate-300">
                <th className="py-2 pr-4">Quotation #</th>
                <th className="py-2 pr-4">Customer</th>
                <th className="py-2 pr-4">Total</th>
                <th className="py-2 pr-4">Status</th>
                <th className="py-2 pr-4">Discount approval</th>
                <th className="py-2 pr-4">Owner</th>
              </tr>
            </thead>
            <tbody>
              {quotations.map((q) => (
                <tr key={q.id} className="border-b border-slate-100">
                  <td className="py-2 pr-4">
                    <Link
                      to={RoutePath.EDIT_QUOTATION.replace(':id', q.id)}
                      className="text-indigo-600 hover:underline font-medium"
                    >
                      {q.quotationNumber}
                    </Link>
                  </td>
                  <td className="py-2 pr-4">{q.customerName}</td>
                  <td className="py-2 pr-4">{formatCurrency(q.grandTotal)}</td>
                  <td className="py-2 pr-4">{QUOTATION_STATUS_LABELS[q.status] || q.status}</td>
                  <td className={`py-2 pr-4 ${q.discountApprovalStatus === 'pending' ? 'text-amber-600 font-medium' : ''}`}>
                    {DISCOUNT_APPROVAL_STATUS_LABELS[q.discountApprovalStatus] || q.discountApprovalStatus}
                  </td>
                  <td className="py-2 pr-4">{q.ownerName || '—'}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </section>
  );
};

export default Quotations;
