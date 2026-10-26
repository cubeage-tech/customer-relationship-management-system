import { useCallback, useEffect, useState } from 'react';
import Button from '../common/Button';
import { apiErrorMessage } from '../../core/utils/apiError';
import { useIndustries } from '../../core/hooks/useIndustries';
import {
  applyCustomFieldTemplate,
  createCustomField,
  listAllCustomFields,
  listCustomFieldTemplates,
  updateCustomField,
} from '../../core/services/customField.service';

const ENTITY_LABELS = { customer: 'Customers', lead: 'Leads', opportunity: 'Opportunities' };
const TYPE_LABELS = { text: 'Text', number: 'Number', date: 'Date', select: 'Choice list', checkbox: 'Yes / no' };
const EMPTY = { entityType: 'customer', label: '', fieldType: 'text', options: '', required: false, industry: '' };
const input = 'border border-slate-300 rounded px-2 py-1 text-sm bg-white';

/**
 * ARC-9: the tenant admin configures the data model per industry: extra fields on customers,
 * leads and opportunities, for every industry or one, starting from a built-in industry template
 * if wanted. Fields are switched off, never deleted, so stored values are kept.
 */
const CustomFieldSettings = () => {
  const { options: industries, labelOf } = useIndustries();
  const [fields, setFields] = useState([]);
  const [templates, setTemplates] = useState([]);
  const [template, setTemplate] = useState('');
  const [form, setForm] = useState(EMPTY);
  const [message, setMessage] = useState('');
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);

  const reload = useCallback(
    () => listAllCustomFields().then((d) => setFields(d ?? [])).catch((err) => setError(apiErrorMessage(err, 'Could not load custom fields.'))),
    [],
  );

  useEffect(() => {
    reload();
    listCustomFieldTemplates().then((d) => setTemplates(d ?? [])).catch(() => setTemplates([]));
  }, [reload]);

  const run = async (action, fallback) => {
    setBusy(true);
    setError('');
    setMessage('');
    try {
      const result = await action();
      await reload();
      return result ?? true;
    } catch (err) {
      setError(apiErrorMessage(err, fallback));
      return null;
    } finally {
      setBusy(false);
    }
  };

  const add = async (e) => {
    e.preventDefault();
    const body = {
      ...form,
      industry: form.industry || null,
      options: form.fieldType === 'select' ? form.options.split(',').map((o) => o.trim()).filter(Boolean) : null,
    };
    if (await run(() => createCustomField(body), 'Could not add this field.')) setForm({ ...EMPTY, entityType: form.entityType });
  };

  const toggle = (f) => run(() => updateCustomField(f.id, { ...f, active: !f.active }), 'Could not update this field.');

  const apply = async () => {
    const result = await run(() => applyCustomFieldTemplate(template), 'Could not apply this template.');
    if (result) setMessage(`${result.created} field(s) added${result.skipped ? `, ${result.skipped} already there` : ''}.`);
  };

  const chosen = templates.find((t) => t.industry === template);

  return (
    <div className="bg-white border border-slate-200 rounded-lg p-5 mb-4">
      <h2 className="font-semibold text-slate-900">Custom fields &amp; industry templates</h2>
      <p className="text-sm text-slate-500 mt-1">
        Add the details your industry needs to customers, leads and opportunities. A field can apply to every industry or to one;
        an opportunity uses its customer's industry.
      </p>
      {error && <p className="text-sm text-red-600 mt-3">{error}</p>}
      {message && <p className="text-sm text-green-700 mt-3">{message}</p>}

      <div className="flex flex-wrap items-end gap-2 mt-4 text-sm">
        <label className="flex flex-col gap-1">
          <span className="text-slate-500">Start from an industry template</span>
          <select value={template} onChange={(e) => setTemplate(e.target.value)} className={input}>
            <option value="">Choose an industry</option>
            {templates.map((t) => <option key={t.industry} value={t.industry}>{labelOf(t.industry) || t.label}</option>)}
          </select>
        </label>
        <Button size="sm" variant="outline" disabled={!template || busy} onClick={apply}>Add template fields</Button>
      </div>
      {chosen && (
        <p className="text-xs text-slate-500 mt-1">
          Adds: {chosen.fields.map((f) => `${f.label} (${ENTITY_LABELS[f.entityType].toLowerCase()})`).join(', ')}
        </p>
      )}

      {Object.entries(ENTITY_LABELS).map(([entity, title]) => {
        const rows = fields.filter((f) => f.entityType === entity);
        return (
          <div key={entity} className="mt-4">
            <h3 className="text-sm font-semibold text-slate-700">{title}</h3>
            {rows.length === 0 && <p className="text-sm text-slate-400 mt-1">No custom fields.</p>}
            <ul className="divide-y divide-slate-100">
              {rows.map((f) => (
                <li key={f.id} className="flex flex-wrap items-center justify-between gap-2 py-2 text-sm">
                  <span className={f.active ? 'text-slate-900' : 'text-slate-400 line-through'}>
                    {f.label}
                    <span className="text-slate-400"> · {TYPE_LABELS[f.fieldType]}{f.required ? ' · required' : ''}
                      {' · '}{f.industry ? labelOf(f.industry) : 'All industries'}
                      {f.options ? ` · ${f.options.join(' / ')}` : ''}</span>
                  </span>
                  <Button size="sm" variant="ghost" disabled={busy} onClick={() => toggle(f)}>{f.active ? 'Switch off' : 'Switch on'}</Button>
                </li>
              ))}
            </ul>
          </div>
        );
      })}

      <form onSubmit={add} className="flex flex-wrap items-end gap-2 mt-5 pt-4 border-t border-slate-100 text-sm">
        <label className="flex flex-col gap-1">
          <span className="text-slate-500">Record</span>
          <select value={form.entityType} onChange={(e) => setForm({ ...form, entityType: e.target.value })} className={input}>
            {Object.entries(ENTITY_LABELS).map(([k, v]) => <option key={k} value={k}>{v}</option>)}
          </select>
        </label>
        <label className="flex flex-col gap-1">
          <span className="text-slate-500">Field name</span>
          <input name="label" value={form.label} onChange={(e) => setForm({ ...form, label: e.target.value })} required maxLength={100} className={input} />
        </label>
        <label className="flex flex-col gap-1">
          <span className="text-slate-500">Type</span>
          <select value={form.fieldType} onChange={(e) => setForm({ ...form, fieldType: e.target.value })} className={input}>
            {Object.entries(TYPE_LABELS).map(([k, v]) => <option key={k} value={k}>{v}</option>)}
          </select>
        </label>
        {form.fieldType === 'select' && (
          <label className="flex flex-col gap-1">
            <span className="text-slate-500">Choices (comma separated)</span>
            <input name="options" value={form.options} onChange={(e) => setForm({ ...form, options: e.target.value })} required className={input} />
          </label>
        )}
        <label className="flex flex-col gap-1">
          <span className="text-slate-500">Industry</span>
          <select value={form.industry} onChange={(e) => setForm({ ...form, industry: e.target.value })} className={input}>
            <option value="">All industries</option>
            {industries.map((i) => <option key={i.value} value={i.value}>{i.label}</option>)}
          </select>
        </label>
        <label className="flex items-center gap-1.5 pb-1">
          <input type="checkbox" checked={form.required} onChange={(e) => setForm({ ...form, required: e.target.checked })} />
          Required
        </label>
        <Button type="submit" size="sm" disabled={busy}>Add field</Button>
      </form>
    </div>
  );
};

export default CustomFieldSettings;
