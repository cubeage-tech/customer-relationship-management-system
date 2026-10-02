import { useCallback, useEffect, useState } from 'react';

const TOAST_DURATION_MS = 4000;

/** Transient success/error message, auto-dismissed. Pair with <Toast toast={toast} onClose={hide} />. */
export const useToast = () => {
  const [toast, setToast] = useState(null);

  useEffect(() => {
    if (!toast) return undefined;
    const timeoutId = window.setTimeout(() => setToast(null), TOAST_DURATION_MS);
    return () => window.clearTimeout(timeoutId);
  }, [toast]);

  const showSuccess = useCallback((message) => setToast({ type: 'success', message }), []);
  const showError = useCallback((message) => setToast({ type: 'error', message }), []);
  const hide = useCallback(() => setToast(null), []);

  return { toast, showSuccess, showError, hide };
};
