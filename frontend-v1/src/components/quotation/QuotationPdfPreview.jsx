import { useEffect, useRef, useState } from 'react';
import { X } from 'lucide-react';
import Button from '../common/Button';
import { apiErrorMessage } from '../../core/utils/apiError';
import { fetchQuotationPdf } from '../../core/services/quotation.service';

/**
 * NAV-M05: the quotation PDF shown inside the CRM (the same file the customer gets), with a
 * download button. Closes with Escape or the close button.
 */
const QuotationPdfPreview = ({ quotationId, quotationNumber, onClose, onDownload }) => {
  const [state, setState] = useState({ url: null, error: '' });
  const closeRef = useRef(null);

  useEffect(() => {
    let url = null;
    let cancelled = false;
    fetchQuotationPdf(quotationId)
      .then((blob) => {
        url = window.URL.createObjectURL(blob);
        if (cancelled) window.URL.revokeObjectURL(url);
        else setState({ url, error: '' });
      })
      .catch((err) => { if (!cancelled) setState({ url: null, error: apiErrorMessage(err, 'Could not load the PDF.') }); });
    return () => {
      cancelled = true;
      if (url) window.URL.revokeObjectURL(url);
    };
  }, [quotationId]);

  // The latest onClose, so the Escape listener is added once (focus stays put while the page re-renders).
  const onCloseRef = useRef(onClose);
  useEffect(() => {
    onCloseRef.current = onClose;
  }, [onClose]);
  useEffect(() => {
    closeRef.current?.focus();
    const onKey = (e) => { if (e.key === 'Escape') onCloseRef.current(); };
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, []);

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-900/50 p-4" role="dialog" aria-modal="true"
      aria-label={`PDF preview of ${quotationNumber}`} onClick={onClose}>
      <div className="flex h-[90vh] w-full max-w-4xl flex-col rounded-lg bg-white shadow-xl" onClick={(e) => e.stopPropagation()}>
        <div className="flex items-center justify-between gap-3 border-b border-slate-200 px-4 py-3">
          <h2 className="font-semibold text-slate-900">{quotationNumber} · PDF preview</h2>
          <div className="flex items-center gap-2">
            <Button size="sm" variant="outline" onClick={onDownload}>Download</Button>
            <button ref={closeRef} type="button" onClick={onClose} aria-label="Close preview"
              className="grid size-8 place-items-center rounded-md text-slate-500 hover:bg-slate-100 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary">
              <X size={18} aria-hidden="true" />
            </button>
          </div>
        </div>
        <div className="flex-1 bg-slate-100">
          {state.error && <p className="p-4 text-sm text-red-600">{state.error}</p>}
          {!state.error && !state.url && <p className="p-4 text-sm text-slate-500">Loading…</p>}
          {state.url && <iframe title={`${quotationNumber} PDF`} src={state.url} className="h-full w-full rounded-b-lg" />}
        </div>
      </div>
    </div>
  );
};

export default QuotationPdfPreview;
