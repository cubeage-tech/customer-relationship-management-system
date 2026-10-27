import { useEffect, useState } from 'react';
import Button from '../common/Button';
import { apiErrorMessage } from '../../core/utils/apiError';
import { getCampaignMetrics, getCampaignRecipients, launchCampaign, scheduleCampaign, updateCampaign } from '../../core/services/campaign.service';
import { listSegments } from '../../core/services/marketing.service';
import { campaignPayload } from '../../core/utils/campaignPayload';

// NOT-2: WhatsApp campaigns reach only customers who opted in (NOT-7).
const SENDABLE = new Set(['email', 'sms', 'whatsapp']);
const inputClass = 'border border-slate-300 rounded px-2 py-1 w-full';

const Stat = ({ label, value }) => (
  <div className="bg-white border border-slate-200 rounded-lg p-3">
    <p className="text-xs text-slate-500">{label}</p>
    <p className="text-lg font-semibold text-slate-900">{value}</p>
  </div>
);

/**
 * FR-7.1b/7.1c/7.2/7.4 on the campaign page: pick the segment and write the message (email/SMS),
 * launch now or schedule, then follow sent / opened / clicked / converted and each recipient.
 */
const CampaignExecution = ({ campaign, canManage, onChange }) => {
  const sendable = SENDABLE.has(campaign.channel);
  const sent = Boolean(campaign.launchedAt) && Boolean(campaign.segmentId);
  const editableStatus = campaign.status === 'draft' || campaign.status === 'scheduled';

  const [segments, setSegments] = useState([]);
  const [message, setMessage] = useState(() => ({
    segmentId: campaign.segmentId ?? '', messageSubject: campaign.messageSubject || '', messageBody: campaign.messageBody || '',
  }));
  const [scheduleAt, setScheduleAt] = useState('');
  const [metrics, setMetrics] = useState(null);
  const [recipients, setRecipients] = useState([]);
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    if (sendable && canManage) listSegments().then((data) => setSegments(data ?? [])).catch(() => setSegments([]));
  }, [sendable, canManage]);

  useEffect(() => {
    getCampaignMetrics(campaign.id).then(setMetrics).catch(() => setMetrics(null));
    getCampaignRecipients(campaign.id).then((data) => setRecipients(data ?? [])).catch(() => setRecipients([]));
  }, [campaign.id, campaign.status, campaign.launchedAt]);

  const run = async (action, fallback) => {
    setBusy(true);
    setError('');
    try {
      onChange(await action());
    } catch (err) {
      setError(apiErrorMessage(err, fallback));
    } finally {
      setBusy(false);
    }
  };

  const saveMessage = (e) => {
    e.preventDefault();
    run(() => updateCampaign(campaign.id, campaignPayload(campaign, {
      segmentId: message.segmentId ? Number(message.segmentId) : null,
      messageSubject: message.messageSubject,
      messageBody: message.messageBody,
    })), 'Could not save the message.');
  };

  const change = (e) => setMessage((prev) => ({ ...prev, [e.target.name]: e.target.value }));
  const segment = segments.find((s) => String(s.id) === String(message.segmentId));

  return (
    <>
      {sendable && (
        <div className="bg-white border border-slate-200 rounded-lg p-5 mb-6">
          <h2 className="font-semibold text-slate-900">Audience and message</h2>
          {sent ? (
            <div className="mt-2 text-sm text-slate-700 space-y-1">
              <p>Sent to segment <span className="font-medium">{campaign.segmentName || '—'}</span> on {new Date(campaign.launchedAt).toLocaleString()}.</p>
              {campaign.messageSubject && <p className="font-medium">{campaign.messageSubject}</p>}
              <p className="whitespace-pre-wrap text-slate-600">{campaign.messageBody}</p>
            </div>
          ) : canManage && editableStatus ? (
            <form onSubmit={saveMessage} className="mt-3 grid gap-3 text-sm">
              <label>Send to segment
                <select name="segmentId" value={message.segmentId} onChange={change} className={inputClass}>
                  <option value="">No segment (launching won't send anything)</option>
                  {segments.map((s) => <option key={s.id} value={s.id}>{s.name} ({s.customerCount} customers)</option>)}
                </select>
              </label>
              {campaign.channel === 'email' && (
                <label>Subject<input name="messageSubject" value={message.messageSubject} onChange={change} maxLength={200} className={inputClass} /></label>
              )}
              <label>{campaign.channel === 'email' ? 'Message (links are tracked)' : 'SMS text'}
                <textarea name="messageBody" value={message.messageBody} onChange={change} rows={campaign.channel === 'email' ? 8 : 3} className={inputClass}
                  placeholder={campaign.channel === 'email' ? 'Write your message. Paste links like https://example.com/offer — clicks are counted.' : 'Short text message'} />
              </label>
              {segment && <p className="text-xs text-slate-500">{segment.customerCount} customers match now; unsubscribed customers and those without a {campaign.channel === 'email' ? 'email address' : 'phone number'} are skipped. An unsubscribe link is added to every email.</p>}
              <div><Button type="submit" disabled={busy}>Save message</Button></div>
            </form>
          ) : (
            <p className="mt-2 text-sm text-slate-500">{campaign.segmentName ? `Segment: ${campaign.segmentName}` : 'No segment chosen.'}</p>
          )}
        </div>
      )}

      {canManage && editableStatus && (
        <div className="bg-white border border-slate-200 rounded-lg p-5 mb-6">
          <h2 className="font-semibold text-slate-900">Launch</h2>
          {campaign.status === 'scheduled' && (
            <p className="mt-1 text-sm text-slate-600">Scheduled to launch {new Date(campaign.scheduledAt).toLocaleString()}.</p>
          )}
          <div className="mt-3 flex flex-wrap items-center gap-3 text-sm">
            <Button disabled={busy} onClick={() => window.confirm(sendable && campaign.segmentId ? 'Send this campaign now?' : 'Launch this campaign now?')
              && run(() => launchCampaign(campaign.id), 'Could not launch the campaign.')}>Launch now</Button>
            <span className="text-slate-400">or</span>
            <input type="datetime-local" value={scheduleAt} onChange={(e) => setScheduleAt(e.target.value)} className="border border-slate-300 rounded px-2 py-1" aria-label="Launch at" />
            <Button variant="secondary" disabled={busy || !scheduleAt} onClick={() => run(() => scheduleCampaign(campaign.id, `${scheduleAt}:00`), 'Could not schedule the campaign.')}>
              {campaign.status === 'scheduled' ? 'Reschedule' : 'Schedule'}
            </Button>
          </div>
        </div>
      )}
      {error && <p className="form-error mb-4">{error}</p>}

      {metrics && metrics.recipients > 0 && (
        <>
          <div className="mb-3 grid gap-3 sm:grid-cols-4">
            <Stat label="Sent" value={`${metrics.sent}${metrics.queued ? ` (+${metrics.queued} queued)` : ''}`} />
            <Stat label="Opened" value={`${metrics.opened} · ${metrics.openRate}%`} />
            <Stat label="Clicked" value={`${metrics.clicked} · ${metrics.clickRate}%`} />
            <Stat label="Converted (30 days)" value={`${metrics.converted} · ${metrics.conversionRate}%`} />
          </div>
          <p className="mb-6 text-xs text-slate-500">
            {metrics.failed} failed · {metrics.skipped} skipped. Opens are counted when the email's images load; a click also counts as an open.
          </p>
          <div className="bg-white border border-slate-200 rounded-lg p-5 mb-6">
            <h2 className="font-semibold text-slate-900 mb-3">Recipients</h2>
            <div className="overflow-x-auto max-h-96">
              <table className="w-full text-left text-sm">
                <thead className="border-b border-slate-300">
                  <tr><th className="py-2 pr-4">Customer</th><th className="py-2 pr-4">Sent to</th><th className="py-2 pr-4">Status</th><th className="py-2 pr-4">Opened</th><th className="py-2 pr-4">Clicked</th></tr>
                </thead>
                <tbody>
                  {recipients.map((r) => (
                    <tr key={r.customerId} className="border-b border-slate-100">
                      <td className="py-2 pr-4">{r.customerName}</td>
                      <td className="py-2 pr-4 text-slate-600">{r.address || '—'}</td>
                      <td className="py-2 pr-4">{r.status}{r.errorMessage ? <span className="block text-xs text-slate-500">{r.errorMessage}</span> : null}</td>
                      <td className="py-2 pr-4">{r.openedAt ? new Date(r.openedAt).toLocaleString() : '—'}</td>
                      <td className="py-2 pr-4">{r.clickedAt ? new Date(r.clickedAt).toLocaleString() : '—'}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </div>
        </>
      )}
    </>
  );
};

export default CampaignExecution;
