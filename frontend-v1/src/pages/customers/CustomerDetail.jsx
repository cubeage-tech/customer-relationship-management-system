import { useEffect, useState } from 'react';
import { useParams, Link } from 'react-router-dom';
import PageHeader from '../../components/common/PageHeader';
import EmptyState from '../../components/common/EmptyState';
import Button from '../../components/common/Button';
import { usePermissions } from '../../core/hooks/usePermissions';
import { PERMISSIONS } from '../../core/constants/permission.constant';
import { INDUSTRY_LABELS, INDUSTRY_OPTIONS } from '../../core/constants/app.constant';
import RoutePath from '../../core/constants/routes.constant';
import {
  getCustomer,
  updateCustomer,
  archiveCustomer,
  restoreCustomer,
  addCustomerContact,
  updateCustomerContact,
  deleteCustomerContact,
} from '../../core/services/customer.service';
import { listUsers } from '../../core/services/user.service';

const PROFILE_FIELDS = [
  { name: 'companyName', label: 'Company name', required: true },
  { name: 'email', label: 'Email' },
  { name: 'phone', label: 'Phone' },
  { name: 'website', label: 'Website' },
  { name: 'address', label: 'Address' },
];

const EMPTY_CONTACT_FORM = { name: '', designation: '', phone: '', email: '' };

const toProfileForm = (customer) => ({
  companyName: customer.companyName || '',
  industry: customer.industry,
  email: customer.email || '',
  phone: customer.phone || '',
  website: customer.website || '',
  address: customer.address || '',
  notes: customer.notes || '',
  ownerId: customer.ownerId || '',
});

const CustomerDetail = () => {
  const { id } = useParams();
  const { can } = usePermissions();
  const canEdit = can(PERMISSIONS.CUSTOMERS_EDIT);
  const canArchive = can(PERMISSIONS.CUSTOMERS_DELETE);

  const [customer, setCustomer] = useState(null);
  const [loading, setLoading] = useState(true);
  const [notFound, setNotFound] = useState(false);
  const [users, setUsers] = useState([]);

  const [editingProfile, setEditingProfile] = useState(false);
  const [profileForm, setProfileForm] = useState(null);
  const [profileError, setProfileError] = useState('');

  const [showContactForm, setShowContactForm] = useState(false);
  const [editingContactId, setEditingContactId] = useState(null);
  const [contactForm, setContactForm] = useState(EMPTY_CONTACT_FORM);
  const [contactError, setContactError] = useState('');

  const refresh = () => {
    getCustomer(id)
      .then((data) => {
        setCustomer(data);
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
      const saved = await updateCustomer(id, profileForm);
      setCustomer(saved);
      setProfileForm(toProfileForm(saved));
      setEditingProfile(false);
    } catch (err) {
      setProfileError(err.response?.data?.message || 'Could not save these changes.');
    }
  };

  const handleArchiveToggle = async () => {
    const updated = customer.status === 'archived'
      ? await restoreCustomer(id)
      : await archiveCustomer(id);
    setCustomer(updated);
  };

  const handleContactChange = (e) =>
    setContactForm((prev) => ({ ...prev, [e.target.name]: e.target.value }));

  const startAddContact = () => {
    setEditingContactId(null);
    setContactForm(EMPTY_CONTACT_FORM);
    setContactError('');
    setShowContactForm(true);
  };

  const startEditContact = (contact) => {
    setEditingContactId(contact.id);
    setContactForm({
      name: contact.name || '',
      designation: contact.designation || '',
      phone: contact.phone || '',
      email: contact.email || '',
    });
    setContactError('');
    setShowContactForm(true);
  };

  const handleContactSave = async (e) => {
    e.preventDefault();
    setContactError('');
    try {
      const saved = editingContactId
        ? await updateCustomerContact(id, editingContactId, contactForm)
        : await addCustomerContact(id, contactForm);
      setCustomer(saved);
      setShowContactForm(false);
      setEditingContactId(null);
    } catch (err) {
      setContactError(err.response?.data?.message || 'Could not save this contact.');
    }
  };

  const handleContactDelete = async (contactId) => {
    const saved = await deleteCustomerContact(id, contactId);
    setCustomer(saved);
  };

  if (loading) return null;

  if (notFound) {
    return (
      <EmptyState
        title="Customer not found"
        message="This customer may have been removed, or you may not have access to it."
        action={<Link to={RoutePath.CUSTOMERS} className="text-indigo-600 hover:underline">Back to customers</Link>}
      />
    );
  }

  return (
    <section>
      <PageHeader
        title={customer.companyName}
        subtitle={`${INDUSTRY_LABELS[customer.industry] || customer.industry} · ${customer.status === 'archived' ? 'Archived' : 'Active'}`}
        actions={
          <div className="flex items-center gap-3">
            <Link to={RoutePath.CUSTOMERS} className="text-sm text-slate-500 hover:underline">
              Back to customers
            </Link>
            {canArchive && (
              <Button variant="secondary" onClick={handleArchiveToggle}>
                {customer.status === 'archived' ? 'Restore' : 'Archive'}
              </Button>
            )}
          </div>
        }
      />

      {/* ---------------- Profile ---------------- */}
      <div className="bg-white border border-slate-200 rounded-lg p-5 mb-6">
        <div className="flex items-center justify-between mb-4">
          <h2 className="font-semibold text-slate-900">Company profile</h2>
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
            {PROFILE_FIELDS.map((field) => (
              <input
                key={field.name}
                type="text"
                name={field.name}
                placeholder={field.label}
                value={profileForm[field.name]}
                onChange={handleProfileChange}
                className="border border-slate-300 rounded px-2 py-1"
                required={field.required}
              />
            ))}
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
                  setProfileForm(toProfileForm(customer));
                  setEditingProfile(false);
                }}
              >
                Cancel
              </Button>
            </div>
          </form>
        ) : (
          <dl className="grid gap-3 sm:grid-cols-2 text-sm">
            <div><dt className="text-slate-500">Email</dt><dd>{customer.email || '—'}</dd></div>
            <div><dt className="text-slate-500">Phone</dt><dd>{customer.phone || '—'}</dd></div>
            <div><dt className="text-slate-500">Website</dt><dd>{customer.website || '—'}</dd></div>
            <div><dt className="text-slate-500">Address</dt><dd>{customer.address || '—'}</dd></div>
            <div><dt className="text-slate-500">Assigned to</dt><dd>{customer.ownerName || '—'}</dd></div>
            <div className="sm:col-span-2"><dt className="text-slate-500">Notes</dt><dd>{customer.notes || '—'}</dd></div>
          </dl>
        )}
      </div>

      {/* ---------------- Contacts ---------------- */}
      <div className="bg-white border border-slate-200 rounded-lg p-5 mb-6">
        <div className="flex items-center justify-between mb-4">
          <h2 className="font-semibold text-slate-900">Contact persons</h2>
          {canEdit && (
            <button type="button" onClick={startAddContact} className="text-sm text-indigo-600 hover:underline">
              Add contact
            </button>
          )}
        </div>

        {showContactForm && (
          <form onSubmit={handleContactSave} className="mb-4 grid gap-3 sm:grid-cols-2 border border-slate-200 rounded p-4">
            <input
              type="text"
              name="name"
              placeholder="Name"
              value={contactForm.name}
              onChange={handleContactChange}
              className="border border-slate-300 rounded px-2 py-1"
              required
            />
            <input
              type="text"
              name="designation"
              placeholder="Designation"
              value={contactForm.designation}
              onChange={handleContactChange}
              className="border border-slate-300 rounded px-2 py-1"
            />
            <input
              type="text"
              name="phone"
              placeholder="Phone"
              value={contactForm.phone}
              onChange={handleContactChange}
              className="border border-slate-300 rounded px-2 py-1"
            />
            <input
              type="email"
              name="email"
              placeholder="Email"
              value={contactForm.email}
              onChange={handleContactChange}
              className="border border-slate-300 rounded px-2 py-1"
            />
            {contactError && <p className="form-error sm:col-span-2">{contactError}</p>}
            <div className="sm:col-span-2 flex gap-3">
              <Button type="submit">{editingContactId ? 'Save contact' : 'Add contact'}</Button>
              <Button type="button" variant="secondary" onClick={() => setShowContactForm(false)}>
                Cancel
              </Button>
            </div>
          </form>
        )}

        {customer.contacts.length === 0 ? (
          <p className="text-sm text-slate-500">No contact persons added yet.</p>
        ) : (
          <table className="w-full text-left text-sm border-collapse">
            <thead>
              <tr className="border-b border-slate-300">
                <th className="py-2 pr-4">Name</th>
                <th className="py-2 pr-4">Designation</th>
                <th className="py-2 pr-4">Phone</th>
                <th className="py-2 pr-4">Email</th>
                {canEdit && <th className="py-2 pr-4"></th>}
              </tr>
            </thead>
            <tbody>
              {customer.contacts.map((contact) => (
                <tr key={contact.id} className="border-b border-slate-100">
                  <td className="py-2 pr-4">{contact.name}</td>
                  <td className="py-2 pr-4">{contact.designation || '—'}</td>
                  <td className="py-2 pr-4">{contact.phone || '—'}</td>
                  <td className="py-2 pr-4">{contact.email || '—'}</td>
                  {canEdit && (
                    <td className="py-2 pr-4 flex gap-3">
                      <button type="button" onClick={() => startEditContact(contact)} className="text-xs text-indigo-600 hover:underline">
                        Edit
                      </button>
                      <button type="button" onClick={() => handleContactDelete(contact.id)} className="text-xs text-red-600 hover:underline">
                        Remove
                      </button>
                    </td>
                  )}
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>

      {/* ---------------- Purchase history & communication timeline ----------------
          FR-1.3/FR-1.4: these read from the Quotation and Communication modules, which
          are not built yet — shown as honest empty states rather than fake data. */}
      <div className="grid gap-6 sm:grid-cols-2">
        <EmptyState
          title="Purchase history"
          message="Order history will appear here once the Quotation module is available."
        />
        <EmptyState
          title="Communication timeline"
          message="Email, WhatsApp, SMS and call history will appear here once the Communication module is available."
        />
      </div>
    </section>
  );
};

export default CustomerDetail;
