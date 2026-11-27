import { useEffect, useState } from 'react';
import { checkDuplicates } from '../services/admin.service';

/**
 * VAL-1/VAL-3: as a customer, lead or contact is typed in, the records of this company that look
 * like the same one (same email, or the same company name ignoring case, punctuation and "Pvt Ltd").
 * Returns the matches, so the form can warn and ask before saving a likely duplicate.
 */
export const useDuplicateCheck = (type, { companyName, email }, excludeId) => {
  const name = (companyName || '').trim();
  const mail = (email || '').trim();
  const key = name.length >= 2 || mail.includes('@') ? JSON.stringify([type, name, mail, excludeId ?? null]) : null;
  const [result, setResult] = useState({ key: null, matches: [] });

  useEffect(() => {
    if (!key) return undefined;
    const [kind, companyNameQuery, emailQuery, exclude] = JSON.parse(key);
    let cancelled = false;
    const timer = window.setTimeout(() => {
      checkDuplicates({ type: kind, companyName: companyNameQuery || null, email: emailQuery || null, excludeId: exclude })
        .then((found) => !cancelled && setResult({ key, matches: found ?? [] }))
        .catch(() => !cancelled && setResult({ key, matches: [] }));
    }, 500);
    return () => {
      cancelled = true;
      window.clearTimeout(timer);
    };
  }, [key]);

  return result.key === key ? result.matches : [];
};

/** The confirmation to show before saving despite the warning. */
export const duplicateConfirmText = (matches) =>
  `This looks like an existing record (${matches.map((m) => m.name || `a ${m.recordType} you can't open`).join('; ')}). Save it anyway?`;
