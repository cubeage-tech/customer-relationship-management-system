import { CheckCircle2, CircleAlert, X } from 'lucide-react';

/** Same look as the Users page toast, shared by newer screens (see useToast). */
const Toast = ({ toast, onClose }) => {
  if (!toast) return null;
  const isError = toast.type === 'error';

  return (
    <div
      className={`fixed right-5 top-20 z-50 flex w-[min(26rem,calc(100vw-2.5rem))] items-start gap-3 rounded-lg border bg-white p-4 shadow-xl ${isError ? 'border-rose-200' : 'border-emerald-200'}`}
      role={isError ? 'alert' : 'status'}
      aria-live={isError ? 'assertive' : 'polite'}
    >
      <span className={`mt-0.5 grid size-8 shrink-0 place-items-center rounded-full ${isError ? 'bg-rose-50 text-rose-600' : 'bg-emerald-50 text-emerald-600'}`}>
        {isError ? <CircleAlert size={18} aria-hidden="true" /> : <CheckCircle2 size={18} aria-hidden="true" />}
      </span>
      <p className="flex-1 text-sm text-slate-700">{toast.message}</p>
      <button type="button" onClick={onClose} className="text-slate-400 hover:text-slate-600" aria-label="Dismiss notification">
        <X size={16} aria-hidden="true" />
      </button>
    </div>
  );
};

export default Toast;
