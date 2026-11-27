import { useEffect, useState } from 'react';
import Button from '../common/Button';
import { apiErrorMessage } from '../../core/utils/apiError';
import { getCustomFieldValues, saveCustomFieldValues } from '../../core/services/customField.service';

const input = 'w-full border border-slate-300 rounded px-2 py-1.5 text-sm bg-white';

const display = (field, value) => {
  if (value == null || value === '') return '—';
  if (field.fieldType === 'checkbox') return value ? 'Yes' : 'No';
  if (field.fieldType === 'date') return new Date(`${value}T00:00:00`).toLocaleDateString();
  if (field.fieldType === 'number') return Number(value).toLocaleString();
  return String(value);
};

const FieldInput = ({ field, value, onChange }) => {
  const common = { id: `cf-${field.key}`, name: field.key, required: field.required };
  if (field.fieldType === 'checkbox') {
    return <input {...common} type="checkbox" required={false} checked={value === true} onChange={(e) => onChange(e.target.checked)} />;
  }
  if (field.fieldType === 'select') {
    return (
      <select {...common} value={value ?? ''} onChange={(e) => onChange(e.target.value)} className={input}>
        <option value="">—</option>
        {field.options.map((o) => <option key={o} value={o}>{o}</option>)}
      </select>
    );
  }
  const type = field.fieldType === 'number' ? 'number' : field.fieldType === 'date' ? 'date' : 'text';
  return <input {...common} type={type} step={type === 'number' ? 'any' : undefined} maxLength={type === 'text' ? 1000 : undefined}
    value={value ?? ''} onChange={(e) => onChange(e.target.value)} className={input} />;
};

/**
 * ARC-9: a record's industry-specific fields, shown on its detail page and edited in place.
 * Only the fields that apply to the record's industry appear; nothing shows when there are none.
 */
const CustomFieldsPanel = ({ entityType, recordId, canEdit, reloadKey }) => {
  const [data, setData] = useState({ key: null, value: null, error: '' });
  const [draft, setDraft] = useState(null);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState('');
  const key = `${entityType}:${recordId}:${reloadKey ?? ''}`;

  useEffect(() => {
    const k = `${entityType}:${recordId}:${reloadKey ?? ''}`;
    getCustomFieldValues(entityType, recordId)
      .then((value) => setData({ key: k, value, error: '' }))
      .catch((err) => setData({ key: k, value: null, error: apiErrorMessage(err, 'Could not load the extra details.') }));
  }, [entityType, recordId, reloadKey]);

  const current = data.key === key ? data.value : null;
  if (data.key === key && data.error) return <p className="text-sm text-red-600">{data.error}</p>;
  if (!current || current.fields.length === 0) return null;

  const save = async (e) => {
    e.preventDefault();
    setSaving(true);
    setError('');
    try {
      const value = await saveCustomFieldValues(entityType, recordId, draft);
      setData({ key, value, error: '' });
      setDraft(null);
    } catch (err) {
      setError(apiErrorMessage(err, 'Could not save the extra details.'));
    } finally {
      setSaving(false);
    }
  };

  return (
    <div className="bg-white border border-slate-200 rounded-lg p-5">
      <div className="flex items-center justify-between gap-2">
        <h2 className="font-semibold text-slate-900">Industry details</h2>
        {canEdit && !draft && <Button size="sm" variant="outline" onClick={() => setDraft({ ...current.values })}>Edit</Button>}
      </div>
      {error && <p className="text-sm text-red-600 mt-2">{error}</p>}
      {!draft ? (
        <dl className="grid gap-x-6 gap-y-3 sm:grid-cols-2 mt-3 text-sm">
          {current.fields.map((f) => (
            <div key={f.key}>
              <dt className="text-slate-500">{f.label}</dt>
              <dd className="text-slate-900">{display(f, current.values[f.key])}</dd>
            </div>
          ))}
        </dl>
      ) : (
        <form onSubmit={save} className="grid gap-3 sm:grid-cols-2 mt-3 text-sm">
          {current.fields.map((f) => (
            <label key={f.key} htmlFor={`cf-${f.key}`} className={f.fieldType === 'checkbox' ? 'flex items-center gap-2 sm:pt-6' : 'flex flex-col gap-1'}>
              {f.fieldType === 'checkbox' && <FieldInput field={f} value={draft[f.key]} onChange={(v) => setDraft({ ...draft, [f.key]: v })} />}
              <span className="text-slate-500">{f.label}{f.required ? ' *' : ''}</span>
              {f.fieldType !== 'checkbox' && <FieldInput field={f} value={draft[f.key]} onChange={(v) => setDraft({ ...draft, [f.key]: v })} />}
            </label>
          ))}
          <div className="flex gap-2 sm:col-span-2">
            <Button type="submit" size="sm" disabled={saving}>{saving ? 'Saving…' : 'Save'}</Button>
            <Button type="button" size="sm" variant="ghost" onClick={() => { setDraft(null); setError(''); }}>Cancel</Button>
          </div>
        </form>
      )}
    </div>
  );
};

export default CustomFieldsPanel;
