import { useCallback, useEffect, useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { AlarmClock, BadgePercent, Bell, Bot, KeyRound, MessageSquare, ShieldAlert, UserPlus } from 'lucide-react';
import { getNotifications, markAllNotificationsRead, markNotificationRead } from '../../core/services/notification.service';

const POLL_MS = 60_000;

const ICONS = {
  assignment: UserPlus,
  follow_up: AlarmClock,
  message: MessageSquare,
  sla_escalation: ShieldAlert,
  discount_approval: BadgePercent,
  ai_alert: Bot,
  integration: KeyRound,
};

const PATHS = {
  lead: (id) => `/leads/${id}`,
  opportunity: (id) => `/opportunities/${id}`,
  quotation: (id) => `/quotations/${id}`,
  service_ticket: (id) => `/service-tickets/${id}`,
  customer: (id) => `/customers/${id}`,
  integration: () => '/platform/settings',
};

const timeAgo = (iso) => {
  const minutes = Math.round((Date.now() - new Date(iso).getTime()) / 60000);
  if (minutes < 1) return 'just now';
  if (minutes < 60) return `${minutes} min ago`;
  if (minutes < 60 * 24) return `${Math.round(minutes / 60)} h ago`;
  return new Date(iso).toLocaleDateString();
};

/**
 * NOT-5: the navbar bell — SLA warnings, discount approval requests, AI alerts, assignments,
 * follow-ups and manager messages (the same inbox the mobile app shows). Refreshes every minute.
 */
const NotificationBell = () => {
  const navigate = useNavigate();
  const [inbox, setInbox] = useState({ unread: 0, items: [] });
  const [open, setOpen] = useState(false);
  const ref = useRef(null);

  const load = useCallback(() => getNotifications().then((data) => setInbox(data ?? { unread: 0, items: [] })).catch(() => {}), []);

  useEffect(() => {
    load();
    const timer = window.setInterval(load, POLL_MS);
    return () => window.clearInterval(timer);
  }, [load]);

  useEffect(() => {
    if (!open) return undefined;
    const close = (e) => { if (ref.current && !ref.current.contains(e.target)) setOpen(false); };
    const esc = (e) => { if (e.key === 'Escape') setOpen(false); };
    document.addEventListener('mousedown', close);
    document.addEventListener('keydown', esc);
    return () => {
      document.removeEventListener('mousedown', close);
      document.removeEventListener('keydown', esc);
    };
  }, [open]);

  const openItem = async (n) => {
    if (!n.read) {
      await markNotificationRead(n.id).catch(() => {});
      load();
    }
    const path = n.entityType && PATHS[n.entityType]?.(n.entityId);
    if (path) {
      setOpen(false);
      navigate(path);
    }
  };

  const readAll = async () => {
    await markAllNotificationsRead().catch(() => {});
    load();
  };

  return (
    <div className="relative" ref={ref}>
      <button
        type="button"
        onClick={() => { setOpen((v) => !v); if (!open) load(); }}
        aria-label={inbox.unread > 0 ? `Notifications, ${inbox.unread} unread` : 'Notifications'}
        aria-expanded={open}
        className="relative w-10 h-10 rounded-full flex items-center justify-center text-slate-500 hover:bg-slate-100 transition"
      >
        <Bell size={20} />
        {inbox.unread > 0 && (
          <span className="absolute -top-0.5 -right-0.5 min-w-[18px] h-[18px] px-1 rounded-full bg-red-500 text-white text-[10px] font-semibold flex items-center justify-center">
            {inbox.unread > 99 ? '99+' : inbox.unread}
          </span>
        )}
      </button>

      {open && (
        <div className="absolute right-0 mt-2 w-[min(24rem,calc(100vw-2rem))] bg-white border border-slate-200 rounded-xl shadow-lg z-50">
          <div className="flex items-center justify-between px-4 py-3 border-b border-slate-100">
            <p className="text-sm font-semibold text-slate-900">Notifications</p>
            {inbox.unread > 0 && (
              <button type="button" onClick={readAll} className="text-xs font-medium text-primary hover:underline">Mark all read</button>
            )}
          </div>
          {inbox.items.length === 0 ? (
            <p className="px-4 py-8 text-center text-sm text-slate-400">You're all caught up.</p>
          ) : (
            <ul className="max-h-[26rem] overflow-y-auto divide-y divide-slate-100">
              {inbox.items.slice(0, 30).map((n) => {
                const Icon = ICONS[n.type] || Bell;
                return (
                  <li key={n.id}>
                    <button type="button" onClick={() => openItem(n)}
                      className={`w-full text-left flex gap-3 px-4 py-3 hover:bg-slate-50 ${n.read ? '' : 'bg-primary/5'}`}>
                      <Icon size={18} className={`mt-0.5 shrink-0 ${n.type === 'sla_escalation' || n.type === 'integration' ? 'text-red-500' : 'text-slate-400'}`} aria-hidden="true" />
                      <span className="min-w-0 flex-1">
                        <span className={`block text-sm ${n.read ? 'text-slate-700' : 'font-semibold text-slate-900'}`}>{n.title}</span>
                        {n.body && <span className="block text-xs text-slate-500 line-clamp-2">{n.body}</span>}
                        <span className="block text-[11px] text-slate-400 mt-0.5">
                          {timeAgo(n.createdAt)}{n.senderName ? ` · ${n.senderName}` : ''}
                        </span>
                      </span>
                    </button>
                  </li>
                );
              })}
            </ul>
          )}
        </div>
      )}
    </div>
  );
};

export default NotificationBell;
