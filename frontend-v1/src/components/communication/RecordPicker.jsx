import { useEffect, useState } from 'react';
import { usePermissions } from '../../core/hooks/usePermissions';
import { PERMISSIONS } from '../../core/constants/permission.constant';
import { listCustomers } from '../../core/services/customer.service';
import { listLeads } from '../../core/services/lead.service';

const input = 'border border-slate-300 rounded px-2 py-1 text-sm bg-white';

/**
 * NAV-M06: pick the customer or lead a call or meeting is with (search within the records the user
 * can see). Calls onPick({ type: 'customer' | 'lead', id, name }).
 */
const RecordPicker = ({ onPick }) => {
  const { can } = usePermissions();
  const types = [
    can(PERMISSIONS.CUSTOMERS_VIEW) && { value: 'customer', label: 'Customer' },
    can(PERMISSIONS.LEADS_VIEW) && { value: 'lead', label: 'Lead' },
  ].filter(Boolean);
  const [type, setType] = useState(types[0]?.value ?? 'customer');
  const [search, setSearch] = useState('');
  const [result, setResult] = useState({ key: null, rows: [] });
  const key = `${type}|${search.trim()}`;

  useEffect(() => {
    const term = search.trim();
    if (term.length < 2) return undefined;
    const k = `${type}|${term}`;
    const timer = window.setTimeout(() => {
      const load = type === 'customer'
        ? listCustomers({ search: term }).then((d) => (d ?? []).map((c) => ({ id: c.id, name: c.companyName, detail: c.email || c.phone || '' })))
        : listLeads({ search: term }).then((d) => (d ?? []).map((l) => ({ id: l.id, name: l.leadName, detail: l.companyName })));
      load.then((rows) => setResult({ key: k, rows: rows.slice(0, 8) })).catch(() => setResult({ key: k, rows: [] }));
    }, 250);
    return () => window.clearTimeout(timer);
  }, [type, search]);

  if (types.length === 0) return null;
  const rows = result.key === key && search.trim().length >= 2 ? result.rows : [];

  return (
    <div className="text-sm">
      <div className="flex flex-wrap gap-2">
        {types.length > 1 && (
          <select value={type} onChange={(e) => setType(e.target.value)} className={input} aria-label="Record type">
            {types.map((t) => <option key={t.value} value={t.value}>{t.label}</option>)}
          </select>
        )}
        <input type="search" value={search} onChange={(e) => setSearch(e.target.value)} className={`${input} flex-1 min-w-[14rem]`}
          placeholder={`Search ${type === 'customer' ? 'customers' : 'leads'} by name`} aria-label="Search records" />
      </div>
      {rows.length > 0 && (
        <ul className="mt-2 border border-slate-200 rounded divide-y divide-slate-100 bg-white">
          {rows.map((r) => (
            <li key={r.id}>
              <button type="button" onClick={() => onPick({ type, id: r.id, name: r.name })}
                className="w-full text-left px-3 py-2 hover:bg-slate-50 focus-visible:outline-none focus-visible:bg-primary/5">
                <span className="font-medium text-slate-900">{r.name}</span>
                {r.detail && <span className="text-slate-400"> · {r.detail}</span>}
              </button>
            </li>
          ))}
        </ul>
      )}
      {result.key === key && search.trim().length >= 2 && rows.length === 0 && <p className="mt-2 text-slate-400">No matches.</p>}
    </div>
  );
};

export default RecordPicker;
