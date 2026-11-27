import { useEffect, useRef, useState } from 'react';
import Button from './Button';
import { apiErrorMessage } from '../../core/utils/apiError';
import { attachmentBase, deleteAttachment, listAttachments, openAttachment, uploadAttachment } from '../../core/services/attachment.service';

const MAX_BYTES = 25 * 1024 * 1024;
const ACCEPT = '.pdf,.docx,.xlsx,.jpg,.jpeg,.png';
const ALLOWED_EXTENSIONS = ['pdf', 'docx', 'xlsx', 'jpg', 'jpeg', 'png'];

const formatSize = (bytes) =>
  bytes >= 1024 * 1024 ? `${(bytes / (1024 * 1024)).toFixed(1)} MB` : `${Math.max(1, Math.round(bytes / 1024))} KB`;

const isViewable = (file) => file.contentType === 'application/pdf' || file.contentType.startsWith('image/');

/**
 * DB-E16: files attached to a quotation (kind="quotation") or service ticket (kind="ticket") —
 * PDF, DOCX, XLSX, JPG or PNG up to 25 MB, the same rules as customer documents.
 */
const AttachmentsPanel = ({ kind, recordId, canEdit, hint }) => {
  const base = attachmentBase(kind, recordId);
  const [files, setFiles] = useState([]);
  const [error, setError] = useState('');
  const [uploading, setUploading] = useState(false);
  const fileInput = useRef(null);

  const refresh = () =>
    listAttachments(base)
      .then((data) => setFiles(data ?? []))
      .catch((err) => setError(apiErrorMessage(err, 'Could not load attachments.')));

  useEffect(() => {
    refresh();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [base]);

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
      await uploadAttachment(base, file);
      refresh();
    } catch (err) {
      setError(apiErrorMessage(err, 'Could not upload this file.'));
    } finally {
      setUploading(false);
    }
  };

  const handleOpen = async (file, view) => {
    setError('');
    try {
      await openAttachment(base, file, view);
    } catch (err) {
      setError(apiErrorMessage(err, 'Could not open this file.'));
    }
  };

  const handleDelete = async (file) => {
    if (!window.confirm(`Remove "${file.fileName}"? This cannot be undone.`)) return;
    setError('');
    try {
      await deleteAttachment(base, file.id);
      refresh();
    } catch (err) {
      setError(apiErrorMessage(err, 'Could not remove this file.'));
    }
  };

  return (
    <div className="bg-white border border-slate-200 rounded-lg p-5 mb-6">
      <div className="flex flex-wrap items-center justify-between gap-3 mb-4">
        <h2 className="font-semibold text-slate-900">Attachments</h2>
        {canEdit && (
          <>
            <input ref={fileInput} type="file" accept={ACCEPT} onChange={handleFile} className="hidden" aria-label="Attach file" />
            <Button type="button" variant="secondary" disabled={uploading} onClick={() => fileInput.current?.click()}>
              {uploading ? 'Uploading…' : 'Attach file'}
            </Button>
          </>
        )}
      </div>
      {error && <p className="text-sm text-red-600 mb-3">{error}</p>}
      {files.length === 0 ? (
        <p className="text-sm text-slate-500">
          No attachments yet.{canEdit ? ` ${hint ?? 'Attach files'} (PDF, DOCX, XLSX, JPG, PNG — up to 25 MB).` : ''}
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
              {files.map((file) => (
                <tr key={file.id} className="border-b border-slate-100">
                  <td className="py-2 pr-4 break-all">{file.fileName}</td>
                  <td className="py-2 pr-4 whitespace-nowrap">{formatSize(file.sizeBytes)}</td>
                  <td className="py-2 pr-4 whitespace-nowrap">
                    {new Date(file.uploadedAt).toLocaleDateString()}
                    {file.uploadedByName ? ` · ${file.uploadedByName}` : ''}
                  </td>
                  <td className="py-2 pr-4 whitespace-nowrap">
                    <div className="flex gap-3">
                      {isViewable(file) && (
                        <button type="button" onClick={() => handleOpen(file, true)} className="text-xs text-primary hover:underline">
                          View
                        </button>
                      )}
                      <button type="button" onClick={() => handleOpen(file, false)} className="text-xs text-primary hover:underline">
                        Download
                      </button>
                      {canEdit && (
                        <button type="button" onClick={() => handleDelete(file)} className="text-xs text-red-600 hover:underline">
                          Remove
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

export default AttachmentsPanel;
