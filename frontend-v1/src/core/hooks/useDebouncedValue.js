import { useEffect, useState } from 'react';

/** `value`, updated only after it stops changing for `delayMs` — keeps search boxes from firing a request per keystroke. */
export const useDebouncedValue = (value, delayMs = 300) => {
  const [debounced, setDebounced] = useState(value);

  useEffect(() => {
    const timeoutId = window.setTimeout(() => setDebounced(value), delayMs);
    return () => window.clearTimeout(timeoutId);
  }, [value, delayMs]);

  return debounced;
};
