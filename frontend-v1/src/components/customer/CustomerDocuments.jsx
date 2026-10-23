import { useEffect, useRef, useState } from 'react';
import Button from '../common/Button';
import { apiErrorMessage } from '../../core/utils/apiError';
import {
  listCustomerDocuments,
  uploadCustomerDocument,
  deleteCustomerDocument,
  openCustomerDocument,
} from '../../core/services/customer.service';

const MAX_BYTES = 25 * 1024 * 1024;
const ACCEPT = '.pdf,.docx,.xlsx,.jpg,.jpeg,.png';
const ALLOWED_EXTENSIONS = ['pdf', 'docx', 'xlsx', 'jpg', 'jpeg', 'png'];

const formatSize = (bytes) =>
  bytes >= 1024 * 1024 ? `${(bytes / (1024 * 1024)).toFixed(1)} MB` : `${Math.max(1, Math.round(bytes / 1024))} KB`;

const isViewable = (doc) => doc.contentType === 'application/pdf' || doc.contentType.startsWith('image/');

/**
 * FR-1.5: documents (contracts, purchase orders…) on a customer record — upload (FR-1.5a) and
 * view / download / delete (FR-1.5b). PDF, DOCX, XLSX, JPG or PNG up to 25 MB.
 */
const CustomerDocuments = ({ customerId, canEdit }) => {
  const [documents, setDocuments] = useState([]);
  const [error, setError] = useState('');
  const [uploading, setUploading] = useState(false);
  const fileInput = useRef(null);

  const refresh = () =>
    listCustomerDocuments(customerId)
      .then((data) => setDocuments(data ?? []))
      .catch((err) => setError(apiErrorMessage(err, 'Could not load documents.')));

  useEffect(() => {
    refresh();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [customerId]);

  const handleFile = async (e) => {
    const file = e.target.files?.[0];
    e.target.value = '';
    if (!file) return;
    setError('');
    const extension = file.name.includes('.') ? file.name.split('.').pop().toLowerCase() : '';
    if (!ALLOWED_EXTENSIONS.includes(extension)) {
      setError('Only PDF, DOCX, XLSX, JPG and PNG files can be uploaded.');
      return;
    }
    if (file.size > MAX_BYTES) {
      setError('File is too large — the limit is 25 MB.');
      return;
    }
    setUploading(true);
    try {
      await uploadCustomerDocument(customerId, file);
      refresh();
    } catch (err) {
      setError(apiErrorMessage(err, 'Could not upload this file.'));
    } finally {
      setUploading(false);
    }
  };

  const handleOpen = async (doc, view) => {
    setError('');
    try {
      await openCustomerDocument(customerId, doc, view);
    } catch (err) {
      setError(apiErrorMessage(err, 'Could not open this document.'));
    }
  };

  const handleDelete = async (doc) => {
    if (!window.confirm(`Delete "${doc.fileName}"? This cannot be undone.`)) return;
    setError('');
    try {
      await deleteCustomerDocument(customerId, doc.id);
      refresh();
    } catch (err) {
      setError(apiErrorMessage(err, 'Could not delete this document.'));
    }
  };

  return (
    <div className="bg-white border border-slate-200 rounded-lg p-5 mb-6">
      <div className="flex flex-wrap items-center justify-between gap-3 mb-4">
        <h2 className="font-semibold text-slate-900">Documents</h2>
        {canEdit && (
          <>
            <input ref={fileInput} type="file" accept={ACCEPT} onChange={handleFile} className="hidden" aria-label="Upload document" />
            <Button type="button" variant="secondary" disabled={uploading} onClick={() => fileInput.current?.click()}>
              {uploading ? 'Uploading…' : 'Upload document'}
            </Button>
          </>
        )}
      </div>
      {error && <p className="text-sm text-red-600 mb-3">{error}</p>}
      {documents.length === 0 ? (
        <p className="text-sm text-slate-500">
          No documents yet.{canEdit ? ' Upload contracts, purchase orders or other files (PDF, DOCX, XLSX, JPG, PNG — up to 25 MB).' : ''}
        </p>
      ) : (
        <div className="overflow-x-auto">
          <table className="w-full text-left text-sm border-collapse">
            <thead>
              <tr className="border-b border-slate-300">
                <th className="py-2 pr-4">Name</th>
                <th className="py-2 pr-4">Size</th>
                <th className="py-2 pr-4">Uploaded</th>
                <th className="py-2 pr-4"></th>
              </tr>
            </thead>
            <tbody>
              {documents.map((doc) => (
                <tr key={doc.id} className="border-b border-slate-100">
                  <td className="py-2 pr-4 break-all">{doc.fileName}</td>
                  <td className="py-2 pr-4 whitespace-nowrap">{formatSize(doc.sizeBytes)}</td>
                  <td className="py-2 pr-4 whitespace-nowrap">
                    {new Date(doc.uploadedAt).toLocaleDateString()}
                    {doc.uploadedByName ? ` · ${doc.uploadedByName}` : ''}
                  </td>
                  <td className="py-2 pr-4 whitespace-nowrap">
                    <div className="flex gap-3">
                      {isViewable(doc) && (
                        <button type="button" onClick={() => handleOpen(doc, true)} className="text-xs text-indigo-600 hover:underline">
                          View
                        </button>
                      )}
                      <button type="button" onClick={() => handleOpen(doc, false)} className="text-xs text-indigo-600 hover:underline">
                        Download
                      </button>
                      {canEdit && (
                        <button type="button" onClick={() => handleDelete(doc)} className="text-xs text-red-600 hover:underline">
                          Delete
                        </button>
                      )}
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
};

export default CustomerDocuments;
