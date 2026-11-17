import { useEffect, useState } from 'react';
import { useParams, Link } from 'react-router-dom';
import PageHeader from '../../components/common/PageHeader';
import EmptyState from '../../components/common/EmptyState';
import Button from '../../components/common/Button';
import { usePermissions } from '../../core/hooks/usePermissions';
import { PERMISSIONS } from '../../core/constants/permission.constant';
import { INDUSTRY_LABELS, INDUSTRY_OPTIONS } from '../../core/constants/app.constant';
import RoutePath from '../../core/constants/routes.constant';
import ApiService from '../../core/services/api.service';
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
const EMPTY_COMMUNICATION_FORM = {
  channel: 'email',
  direction: 'outbound',
  subject: '',
  content: '',
  contactName: '',
  contactAddress: '',
  status: 'logged',
  occurredAt: '',
};

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
  const [purchaseHistory, setPurchaseHistory] = useState({
    customerId: null,
    loading: true,
    purchases: [],
    error: '',
  });
  const [communicationHistory, setCommunicationHistory] = useState({
    customerId: null,
    loading: true,
    communications: [],
    error: '',
  });
  const [showCommunicationForm, setShowCommunicationForm] = useState(false);
  const [communicationForm, setCommunicationForm] = useState(EMPTY_COMMUNICATION_FORM);
  const [communicationError, setCommunicationError] = useState('');
  const [communicationSaving, setCommunicationSaving] = useState(false);
  const [documentHistory, setDocumentHistory] = useState({
    customerId: null,
    loading: true,
    documents: [],
    error: '',
  });
  const [documentUploadError, setDocumentUploadError] = useState('');
  const [documentUploadLoading, setDocumentUploadLoading] = useState(false);
  const [documentActionError, setDocumentActionError] = useState('');
  const [deletingDocumentId, setDeletingDocumentId] = useState(null);

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

  useEffect(() => {
    let cancelled = false;

    ApiService.apiget(`/api/customers/${id}/purchases`)
      .then((data) => {
        if (!Array.isArray(data)) {
          throw new Error('The purchase history response was invalid.');
        }
        if (!cancelled) {
          setPurchaseHistory({ customerId: id, loading: false, purchases: data, error: '' });
        }
      })
      .catch((err) => {
        if (!cancelled) {
          setPurchaseHistory({
            customerId: id,
            loading: false,
            purchases: [],
            error: err.response?.data?.message || err.message || 'Could not load purchase history.',
          });
        }
      })

    return () => {
      cancelled = true;
    };
  }, [id]);

  useEffect(() => {
    let cancelled = false;

    ApiService.getCustomerDocuments(id)
      .then((data) => {
        if (!Array.isArray(data)) {
          throw new Error('The customer document response was invalid.');
        }
        if (!cancelled) {
          setDocumentHistory({ customerId: id, loading: false, documents: data, error: '' });
        }
      })
      .catch((err) => {
        if (!cancelled) {
          setDocumentHistory({
            customerId: id,
            loading: false,
            documents: [],
            error: err.response?.data?.message || err.message || 'Could not load customer documents.',
          });
        }
      });

    return () => {
      cancelled = true;
    };
  }, [id]);

  useEffect(() => {
    let cancelled = false;

    ApiService.getCustomerCommunications(id)
      .then((data) => {
        if (!Array.isArray(data)) {
          throw new Error('The communication history response was invalid.');
        }
        if (!cancelled) {
          setCommunicationHistory({
            customerId: id,
            loading: false,
            communications: data,
            error: '',
          });
        }
      })
      .catch((err) => {
        if (!cancelled) {
          setCommunicationHistory({
            customerId: id,
            loading: false,
            communications: [],
            error: err.response?.data?.message || err.message || 'Could not load communication history.',
          });
        }
      });

    return () => {
      cancelled = true;
    };
  }, [id]);

  const purchasesLoading = purchaseHistory.customerId !== id || purchaseHistory.loading;
  const purchases = purchaseHistory.customerId === id ? purchaseHistory.purchases : [];
  const purchasesError = purchaseHistory.customerId === id ? purchaseHistory.error : '';
  const communicationsLoading = communicationHistory.customerId !== id || communicationHistory.loading;
  const communications = communicationHistory.customerId === id
    ? communicationHistory.communications
    : [];
  const communicationsError = communicationHistory.customerId === id ? communicationHistory.error : '';
  const documentsLoading = documentHistory.customerId !== id || documentHistory.loading;
  const documents = documentHistory.customerId === id ? documentHistory.documents : [];
  const documentsError = documentHistory.customerId === id ? documentHistory.error : '';

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

  const handleCommunicationChange = (e) =>
    setCommunicationForm((prev) => ({ ...prev, [e.target.name]: e.target.value }));

  const handleCommunicationSave = async (e) => {
    e.preventDefault();
    setCommunicationError('');
    setCommunicationSaving(true);
    try {
      const saved = await ApiService.createCustomerCommunication(id, {
        ...communicationForm,
        occurredAt: communicationForm.occurredAt || null,
      });
      setCommunicationHistory((previous) => {
        const existing = previous.customerId === id ? previous.communications : [];
        const communications = [saved, ...existing].sort(
          (a, b) => new Date(b.occurredAt).getTime() - new Date(a.occurredAt).getTime()
            || Number(b.id) - Number(a.id),
        );
        return { customerId: id, loading: false, communications, error: '' };
      });
      setCommunicationForm(EMPTY_COMMUNICATION_FORM);
      setShowCommunicationForm(false);
    } catch (err) {
      setCommunicationError(
        err.response?.data?.message || 'Could not save this communication record.',
      );
    } finally {
      setCommunicationSaving(false);
    }
  };

  const handleDocumentUpload = async (e) => {
    e.preventDefault();
    const form = e.currentTarget;
    const file = form.elements.namedItem('customer-document')?.files?.[0];
    if (!file) {
      setDocumentUploadError('Choose a document to upload.');
      return;
    }
    setDocumentUploadError('');
    setDocumentActionError('');
    setDocumentUploadLoading(true);
    try {
      const saved = await ApiService.uploadCustomerDocument(id, file);
      setDocumentHistory((previous) => {
        const existing = previous.customerId === id ? previous.documents : [];
        const documents = [saved, ...existing].sort(
          (a, b) => new Date(b.uploadedAt).getTime() - new Date(a.uploadedAt).getTime()
            || Number(b.id) - Number(a.id),
        );
        return { customerId: id, loading: false, documents, error: '' };
      });
      form.reset();
    } catch (err) {
      setDocumentUploadError(
        err.response?.data?.message || 'Could not upload this document.',
      );
    } finally {
      setDocumentUploadLoading(false);
    }
  };

  const handleDocumentDownload = async (document) => {
    setDocumentActionError('');
    try {
      const response = await ApiService.downloadCustomerDocument(id, document.id);
      const url = window.URL.createObjectURL(new Blob([response.data], {
        type: document.contentType || 'application/octet-stream',
      }));
      const link = window.document.createElement('a');
      link.href = url;
      link.download = document.originalFilename;
      window.document.body.appendChild(link);
      link.click();
      link.remove();
      window.URL.revokeObjectURL(url);
    } catch (err) {
      setDocumentActionError(err.response?.data?.message || 'Could not download this document.');
    }
  };

  const handleDocumentDelete = async (document) => {
    if (!window.confirm(`Delete "${document.originalFilename}"? This cannot be undone.`)) return;
    setDocumentActionError('');
    setDeletingDocumentId(document.id);
    try {
      await ApiService.deleteCustomerDocument(id, document.id);
      setDocumentHistory((previous) => ({
        ...previous,
        documents: previous.customerId === id
          ? previous.documents.filter((item) => item.id !== document.id)
          : previous.documents,
      }));
    } catch (err) {
      setDocumentActionError(err.response?.data?.message || 'Could not delete this document.');
    } finally {
      setDeletingDocumentId(null);
    }
  };

  const formatFileSize = (size) => {
    if (size < 1024) return `${size} B`;
    if (size < 1024 * 1024) return `${(size / 1024).toFixed(1)} KB`;
    return `${(size / (1024 * 1024)).toFixed(1)} MB`;
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

      {/* ---------------- Purchase history & communication timeline ---------------- */}
      <div className="grid gap-6 sm:grid-cols-2">
        <div className="bg-white border border-slate-200 rounded-lg p-5">
          <div className="mb-4 flex items-center justify-between gap-3">
            <h2 className="font-semibold text-slate-900">Customer documents</h2>
          </div>
          {canEdit && (
            <form onSubmit={handleDocumentUpload} className="mb-4 flex flex-col gap-3 sm:flex-row">
              <input
                type="file"
                name="customer-document"
                accept=".pdf,.png,.jpg,.jpeg,.txt,.docx,.xlsx"
                required
                className="min-w-0 flex-1 rounded border border-slate-300 px-2 py-1 text-sm"
                aria-label="Choose document to upload"
              />
              <Button type="submit" loading={documentUploadLoading} disabled={documentUploadLoading}>
                Upload
              </Button>
            </form>
          )}
          <p className="mb-3 text-xs text-slate-500">
            PDF, PNG, JPG, TXT, DOCX or XLSX · maximum 10 MB
          </p>
          {documentUploadError && (
            <p role="alert" className="mb-3 text-sm text-red-600">{documentUploadError}</p>
          )}
          {documentActionError && (
            <p role="alert" className="mb-3 text-sm text-red-600">{documentActionError}</p>
          )}
          {documentsLoading ? (
            <p className="text-sm text-slate-500">Loading documents…</p>
          ) : documentsError ? (
            <p role="alert" className="text-sm text-red-600">{documentsError}</p>
          ) : documents.length === 0 ? (
            <p className="text-sm text-slate-500">No documents attached to this customer.</p>
          ) : (
            <ul className="divide-y divide-slate-100">
              {documents.map((document) => (
                <li key={document.id} className="flex flex-wrap items-center justify-between gap-3 py-3">
                  <div className="min-w-0">
                    <p className="break-all text-sm font-medium text-slate-900">
                      {document.originalFilename}
                    </p>
                    <p className="text-xs text-slate-500">
                      {formatFileSize(document.fileSize)}
                      {document.uploadedAt
                        ? ` · ${new Date(document.uploadedAt).toLocaleDateString()}`
                        : ''}
                      {document.uploadedByName ? ` · ${document.uploadedByName}` : ''}
                    </p>
                  </div>
                  <div className="flex shrink-0 gap-3">
                    <button
                      type="button"
                      onClick={() => handleDocumentDownload(document)}
                      className="text-sm text-indigo-600 hover:underline"
                    >
                      Download
                    </button>
                    {canEdit && (
                      <button
                        type="button"
                        onClick={() => handleDocumentDelete(document)}
                        disabled={deletingDocumentId === document.id}
                        className="text-sm text-red-600 hover:underline disabled:opacity-50"
                      >
                        {deletingDocumentId === document.id ? 'Deleting…' : 'Delete'}
                      </button>
                    )}
                  </div>
                </li>
              ))}
            </ul>
          )}
        </div>
        <div className="bg-white border border-slate-200 rounded-lg p-5">
          <h2 className="font-semibold text-slate-900 mb-3">Communication timeline</h2>
          <div className="mb-4">
            <Button
              type="button"
              variant="secondary"
              onClick={() => {
                setCommunicationError('');
                setShowCommunicationForm((open) => !open);
              }}
            >
              {showCommunicationForm ? 'Cancel' : 'Log communication'}
            </Button>
          </div>
          {showCommunicationForm && (
            <form onSubmit={handleCommunicationSave} className="mb-5 grid gap-3 sm:grid-cols-2">
              <label className="grid gap-1 text-sm text-slate-600">
                Channel
                <select
                  name="channel"
                  value={communicationForm.channel}
                  onChange={handleCommunicationChange}
                  className="border border-slate-300 rounded px-2 py-1 text-slate-900"
                >
                  <option value="email">Email</option>
                  <option value="whatsapp">WhatsApp</option>
                  <option value="sms">SMS</option>
                  <option value="call">Call</option>
                </select>
              </label>
              <label className="grid gap-1 text-sm text-slate-600">
                Direction
                <select
                  name="direction"
                  value={communicationForm.direction}
                  onChange={handleCommunicationChange}
                  className="border border-slate-300 rounded px-2 py-1 text-slate-900"
                >
                  <option value="outbound">Outbound</option>
                  <option value="inbound">Inbound</option>
                </select>
              </label>
              <input
                type="text"
                name="subject"
                placeholder="Subject"
                maxLength={255}
                value={communicationForm.subject}
                onChange={handleCommunicationChange}
                className="border border-slate-300 rounded px-2 py-1"
              />
              <input
                type="text"
                name="contactName"
                placeholder="Contact name"
                maxLength={150}
                value={communicationForm.contactName}
                onChange={handleCommunicationChange}
                className="border border-slate-300 rounded px-2 py-1"
              />
              <input
                type="text"
                name="contactAddress"
                placeholder="Email address or phone number"
                maxLength={255}
                value={communicationForm.contactAddress}
                onChange={handleCommunicationChange}
                className="border border-slate-300 rounded px-2 py-1"
              />
              <label className="grid gap-1 text-sm text-slate-600">
                Status
                <select
                  name="status"
                  value={communicationForm.status}
                  onChange={handleCommunicationChange}
                  className="border border-slate-300 rounded px-2 py-1 text-slate-900"
                >
                  <option value="logged">Logged</option>
                  <option value="sent">Sent</option>
                  <option value="delivered">Delivered</option>
                  <option value="received">Received</option>
                  <option value="failed">Failed</option>
                  <option value="missed">Missed</option>
                </select>
              </label>
              <label className="grid gap-1 text-sm text-slate-600 sm:col-span-2">
                Date and time (optional)
                <input
                  type="datetime-local"
                  name="occurredAt"
                  value={communicationForm.occurredAt}
                  onChange={handleCommunicationChange}
                  className="border border-slate-300 rounded px-2 py-1 text-slate-900"
                />
              </label>
              <textarea
                name="content"
                placeholder="Message or call notes"
                value={communicationForm.content}
                onChange={handleCommunicationChange}
                maxLength={10000}
                required
                className="border border-slate-300 rounded px-2 py-1 sm:col-span-2"
                rows={3}
              />
              {communicationError && (
                <p role="alert" className="text-sm text-red-600 sm:col-span-2">
                  {communicationError}
                </p>
              )}
              <div className="flex gap-3 sm:col-span-2">
                <Button type="submit" loading={communicationSaving} disabled={communicationSaving}>
                  Save communication
                </Button>
                <Button type="button" variant="secondary" onClick={() => setShowCommunicationForm(false)}>
                  Cancel
                </Button>
              </div>
            </form>
          )}
          {communicationsLoading ? (
            <p className="text-sm text-slate-500">Loading communication history…</p>
          ) : communicationsError ? (
            <p role="alert" className="text-sm text-red-600">{communicationsError}</p>
          ) : communications.length === 0 ? (
            <p className="text-sm text-slate-500">No communication history recorded yet.</p>
          ) : (
            <ol className="space-y-3" aria-label="Communication history">
              {communications.map((entry) => (
                <li key={entry.id} className="border-l-2 border-indigo-200 pl-3">
                  <div className="flex flex-wrap items-center justify-between gap-2">
                    <p className="font-medium text-slate-900">
                      {entry.channel?.toUpperCase()} · {entry.direction}
                      {entry.subject ? ` · ${entry.subject}` : ''}
                    </p>
                    <time className="text-xs text-slate-500" dateTime={entry.occurredAt}>
                      {entry.occurredAt ? new Date(entry.occurredAt).toLocaleString() : '—'}
                    </time>
                  </div>
                  {(entry.contactName || entry.contactAddress) && (
                    <p className="text-xs text-slate-500">
                      {[entry.contactName, entry.contactAddress].filter(Boolean).join(' · ')}
                    </p>
                  )}
                  <p className="mt-1 whitespace-pre-wrap text-sm text-slate-700">{entry.content}</p>
                  <p className="mt-1 text-xs text-slate-500">
                    {entry.status} · Logged by {entry.createdByName || '—'}
                  </p>
                </li>
              ))}
            </ol>
          )}
        </div>
        <div className="bg-white border border-slate-200 rounded-lg p-5">
          <h2 className="font-semibold text-slate-900 mb-3">Purchase history</h2>
          {purchasesLoading ? (
            <p className="text-sm text-slate-500">Loading purchase history…</p>
          ) : purchasesError ? (
            <p role="alert" className="text-sm text-red-600">{purchasesError}</p>
          ) : purchases.length === 0 ? (
            <p className="text-sm text-slate-500">No purchases recorded yet.</p>
          ) : (
            <div className="overflow-x-auto">
              <table className="w-full text-left text-sm border-collapse">
                <thead>
                  <tr className="border-b border-slate-300">
                    <th className="py-2 pr-4">Purchase</th>
                    <th className="py-2 pr-4">Date</th>
                    <th className="py-2 pr-4">Status</th>
                    <th className="py-2 pr-4">Total</th>
                  </tr>
                </thead>
                <tbody>
                  {purchases.map((purchase, index) => {
                    const purchaseDate = purchase.purchaseDate || purchase.purchasedAt || purchase.createdAt;
                    const parsedDate = purchaseDate ? new Date(purchaseDate) : null;
                    const total = purchase.totalAmount ?? purchase.grandTotal ?? purchase.amount;

                    return (
                      <tr
                        key={purchase.id || purchase.orderNumber || purchase.purchaseNumber || index}
                        className="border-b border-slate-100"
                      >
                        <td className="py-2 pr-4">
                          {purchase.orderNumber || purchase.purchaseNumber || purchase.quotationNumber || purchase.id || '—'}
                        </td>
                        <td className="py-2 pr-4">
                          {parsedDate && !Number.isNaN(parsedDate.getTime())
                            ? parsedDate.toLocaleDateString()
                            : '—'}
                        </td>
                        <td className="py-2 pr-4">{purchase.status || '—'}</td>
                        <td className="py-2 pr-4">{total ?? '—'}</td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
            </div>
          )}
        </div>
      </div>
    </section>
  );
};

export default CustomerDetail;
