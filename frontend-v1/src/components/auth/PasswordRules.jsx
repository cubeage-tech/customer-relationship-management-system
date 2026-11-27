import { useEffect, useState } from 'react';
import { CheckCircle2, Circle } from 'lucide-react';
import { getPasswordPolicy } from '../../core/services/admin.service';

const DEFAULT_RULES = { minLength: 10, minCharacterKinds: 3, breachCheck: true, history: 5 };

const kinds = (password) => [/[a-z]/, /[A-Z]/, /\d/, /[^A-Za-z0-9]/].filter((re) => re.test(password)).length;

/**
 * SEC-1b: the password rules, ticked off as the user types. The server checks them again (plus
 * common, breached and recently used passwords) and names the rule a refused password breaks.
 */
const PasswordRules = ({ password = '', name = '', email = '' }) => {
  const [rules, setRules] = useState(DEFAULT_RULES);

  useEffect(() => {
    getPasswordPolicy().then((r) => r && setRules(r)).catch(() => {});
  }, []);

  const lower = password.toLowerCase();
  const personal = [...name.toLowerCase().split(/[^\p{L}0-9]+/u), email.split('@')[0]?.toLowerCase()]
    .filter((part) => part && part.length >= 4);
  const checks = [
    { ok: password.length >= rules.minLength, label: `At least ${rules.minLength} characters` },
    { ok: kinds(password) >= rules.minCharacterKinds, label: 'Three of: lowercase, capitals, numbers, symbols' },
    { ok: password.length > 0 && !personal.some((part) => lower.includes(part)), label: 'Not your name or email address' },
  ];

  return (
    <ul className="mt-2 space-y-1 text-xs" aria-label="Password rules">
      {checks.map((c) => (
        <li key={c.label} className={`flex items-center gap-1.5 ${c.ok ? 'text-emerald-700' : 'text-slate-500'}`}>
          {c.ok ? <CheckCircle2 size={13} aria-hidden="true" /> : <Circle size={13} aria-hidden="true" />}
          {c.label}
        </li>
      ))}
      <li className="text-slate-400">
        {rules.breachCheck ? 'Common or breached passwords' : 'Common passwords'}
        {rules.history > 0 ? ` and your last ${rules.history} passwords` : ''} are refused.
      </li>
    </ul>
  );
};

export default PasswordRules;
