import { useEffect, useState } from 'react';
import { getReportFilterOptions } from '../../core/services/analytics.service';

const inputClass = 'border border-slate-300 rounded px-2 py-1 text-sm bg-white';

/**
 * FR-9.6: date range, industry, team and salesperson filters. The team and user choices are only
 * those the caller may see, and the backend re-checks them. salesFilters = false shows only the dates
 * (for reports that don't take industry/team/user); children add report-specific filters.
 */
const ReportFilters = ({ value, onChange, salesFilters = true, children }) => {
  const [options, setOptions] = useState({ teams: [], users: [], industries: [] });

  useEffect(() => {
    getReportFilterOptions().then((data) => data && setOptions(data)).catch(() => {});
  }, []);

  const set = (key) => (e) => onChange({ ...value, [key]: e.target.value });

  return (
    <div className="flex flex-wrap items-end gap-3 bg-white border border-slate-200 rounded-lg p-4 mb-6 text-sm">
      <label className="flex flex-col gap-1">
        <span className="text-slate-500">From</span>
        <input type="date" value={value.from} onChange={set('from')} className={inputClass} />
      </label>
      <label className="flex flex-col gap-1">
        <span className="text-slate-500">To</span>
        <input type="date" value={value.to} onChange={set('to')} className={inputClass} />
      </label>
      {salesFilters && (
        <label className="flex flex-col gap-1">
          <span className="text-slate-500">Industry</span>
          <select value={value.industry} onChange={set('industry')} className={inputClass}>
            <option value="">All industries</option>
            {options.industries.map((i) => <option key={i.code} value={i.code}>{i.label}</option>)}
          </select>
        </label>
      )}
      {salesFilters && options.teams.length > 0 && (
        <label className="flex flex-col gap-1">
          <span className="text-slate-500">Team</span>
          <select value={value.teamId} onChange={set('teamId')} className={inputClass}>
            <option value="">All teams</option>
            {options.teams.map((t) => <option key={t.id} value={t.id}>{t.label}</option>)}
          </select>
        </label>
      )}
      {salesFilters && options.users.length > 1 && (
        <label className="flex flex-col gap-1">
          <span className="text-slate-500">Salesperson</span>
          <select value={value.userId} onChange={set('userId')} className={inputClass}>
            <option value="">Everyone</option>
            {options.users.map((u) => <option key={u.id} value={u.id}>{u.label}</option>)}
          </select>
        </label>
      )}
      {children}
    </div>
  );
};

export default ReportFilters;
