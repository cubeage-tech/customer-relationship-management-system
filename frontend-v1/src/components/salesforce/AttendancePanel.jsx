import { useEffect, useState } from 'react';
import { MapPin } from 'lucide-react';
import Button from '../common/Button';
import { apiErrorMessage } from '../../core/utils/apiError';
import { checkIn, checkOut, currentLocation, getAttendance, getMyAttendance } from '../../core/services/salesforce.service';

const today = () => new Date().toISOString().slice(0, 10);
const time = (v) => (v ? new Date(v).toLocaleTimeString([], { timeStyle: 'short' }) : '—');
const mapLink = (lat, lng) => (lat != null ? `https://www.openstreetmap.org/?mlat=${lat}&mlon=${lng}#map=16/${lat}/${lng}` : null);

const Where = ({ lat, lng }) => {
  const href = mapLink(lat, lng);
  return href ? <a href={href} target="_blank" rel="noreferrer" className="inline-flex items-center gap-0.5 text-blue-600 hover:underline"><MapPin size={12} aria-hidden="true" />map</a> : null;
};

/** FR-8.4: check in / out (with location when allowed); managers also see their team's day. */
const AttendancePanel = ({ canSeeTeam }) => {
  const [mine, setMine] = useState(null);
  const [team, setTeam] = useState([]);
  const [day, setDay] = useState(today());
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);

  const loadMine = () => getMyAttendance().then(setMine).catch((err) => setError(apiErrorMessage(err, 'Could not load attendance.')));

  useEffect(() => {
    loadMine();
  }, []);

  useEffect(() => {
    if (canSeeTeam) getAttendance({ from: day, to: day }).then((data) => setTeam(data ?? [])).catch(() => setTeam([]));
  }, [canSeeTeam, day, mine]);

  const act = async (fn) => {
    setBusy(true);
    setError('');
    try {
      await fn(await currentLocation());
      await loadMine();
    } catch (err) {
      setError(apiErrorMessage(err, 'Could not record attendance.'));
    } finally {
      setBusy(false);
    }
  };

  const current = mine?.current;

  return (
    <div className="space-y-6">
      <div className="bg-white border border-slate-200 rounded-lg p-5">
        <h2 className="font-semibold text-slate-900">My attendance</h2>
        <p className="text-sm text-slate-600 mt-1">
          {current ? `Checked in at ${time(current.checkInAt)}.` : 'You are not checked in.'} Your location is saved only if you allow it.
        </p>
        {error && <p className="text-sm text-red-600 mt-2">{error}</p>}
        <div className="mt-3">
          {current
            ? <Button disabled={busy} onClick={() => act(checkOut)}>{busy ? 'Saving…' : 'Check out'}</Button>
            : <Button disabled={busy} onClick={() => act(checkIn)}>{busy ? 'Saving…' : 'Check in'}</Button>}
        </div>
        {mine?.recent?.length > 0 && (
          <div className="overflow-x-auto mt-4">
          <table className="w-full text-left text-sm">
            <thead className="border-b border-slate-200 text-xs text-slate-500"><tr><th className="py-1.5">Day</th><th>In</th><th>Out</th><th>Hours</th></tr></thead>
            <tbody>
              {mine.recent.map((r) => (
                <tr key={r.id} className="border-b border-slate-100">
                  <td className="py-1.5">{new Date(r.checkInAt).toLocaleDateString()}</td>
                  <td>{time(r.checkInAt)} <Where lat={r.checkInLat} lng={r.checkInLng} /></td>
                  <td>{time(r.checkOutAt)} <Where lat={r.checkOutLat} lng={r.checkOutLng} /></td>
                  <td>{r.hours ?? '—'}</td>
                </tr>
              ))}
            </tbody>
          </table>
          </div>
        )}
      </div>

      {canSeeTeam && (
        <div className="bg-white border border-slate-200 rounded-lg p-5">
          <div className="flex flex-wrap items-center justify-between gap-3">
            <h2 className="font-semibold text-slate-900">Team attendance</h2>
            <input type="date" value={day} onChange={(e) => setDay(e.target.value)} className="border border-slate-300 rounded px-2 py-1 text-sm" aria-label="Day" />
          </div>
          {team.length === 0 ? <p className="mt-3 text-sm text-slate-500">Nobody checked in on this day.</p> : (
            <div className="overflow-x-auto mt-3">
            <table className="w-full text-left text-sm">
              <thead className="border-b border-slate-200 text-xs text-slate-500"><tr><th className="py-1.5">Person</th><th>In</th><th>Out</th><th>Hours</th></tr></thead>
              <tbody>
                {team.map((r) => (
                  <tr key={r.id} className="border-b border-slate-100">
                    <td className="py-1.5">{r.userName}</td>
                    <td>{time(r.checkInAt)} <Where lat={r.checkInLat} lng={r.checkInLng} /></td>
                    <td>{time(r.checkOutAt)} <Where lat={r.checkOutLat} lng={r.checkOutLng} /></td>
                    <td>{r.hours ?? (r.checkOutAt ? '—' : 'still in')}</td>
                  </tr>
                ))}
              </tbody>
            </table>
            </div>
          )}
        </div>
      )}
    </div>
  );
};

export default AttendancePanel;
