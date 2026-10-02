import { useEffect, useState } from 'react';
import {
  CheckCheck,
  ChevronRight,
  Clock3,
  LifeBuoy,
  MessageSquareText,
  Search,
  Send,
  ShieldCheck,
  UserRound,
} from 'lucide-react';
import PageHeader from '../../components/common/PageHeader';
import { useAuth } from '../../core/hooks/useAuth';

const STORAGE_KEY = 'crm-superadmin-support-inbox';

const INCOMING_REQUESTS = [
  {
    id: 'HC-2084',
    subject: 'Unable to invite a new team member',
    category: 'Account & access',
    requester: 'Aarav Desai',
    email: 'aarav@northstar.example',
    organization: 'Northstar Labs',
    role: 'Tenant administrator',
    status: 'needs reply',
    updatedAt: '2026-10-02T09:35:00.000Z',
    messages: [
      { id: 'm-2084-1', author: 'Aarav Desai', role: 'user', body: 'Hi, I am trying to invite a teammate but the invite button is disabled. I am the workspace admin. Could you help me figure out what is blocking it?', createdAt: '2026-10-02T09:35:00.000Z' },
    ],
  },
  {
    id: 'HC-2081',
    subject: 'Question about trial renewal',
    category: 'Billing & plans',
    requester: 'Nina Patel',
    email: 'nina@brightpath.example',
    organization: 'Brightpath Group',
    role: 'Executive owner',
    status: 'needs reply',
    updatedAt: '2026-10-02T08:52:00.000Z',
    messages: [
      { id: 'm-2081-1', author: 'Nina Patel', role: 'user', body: 'Our trial is ending soon. Can you confirm whether our customer data will stay available if we choose a plan after the trial?', createdAt: '2026-10-02T08:52:00.000Z' },
    ],
  },
  {
    id: 'HC-2076',
    subject: 'Customer import field mapping',
    category: 'Getting started',
    requester: 'Miguel Santos',
    email: 'miguel@foundry.example',
    organization: 'Foundry Works',
    role: 'Sales manager',
    status: 'replied',
    updatedAt: '2026-10-01T16:18:00.000Z',
    messages: [
      { id: 'm-2076-1', author: 'Miguel Santos', role: 'user', body: 'We imported our customer list, but the contact phone numbers did not appear. Is there a specific column name the importer expects?', createdAt: '2026-10-01T15:40:00.000Z' },
      { id: 'm-2076-2', author: 'Platform Support', role: 'admin', body: 'Use the “Phone” column in the CSV template, then map it to Contact phone during the import preview. You can reopen the import preview without creating duplicate customers.', createdAt: '2026-10-01T16:18:00.000Z' },
    ],
  },
];

const FILTERS = ['All requests', 'Needs reply', 'Replied'];

const formatTime = (value) => new Intl.DateTimeFormat(undefined, {
  month: 'short', day: 'numeric', hour: 'numeric', minute: '2-digit',
}).format(new Date(value));

const HelpCenter = () => {
  const { user } = useAuth();
  const adminName = user?.fullName || user?.name || 'Platform Support';
  const [requests, setRequests] = useState(() => {
    try {
      const saved = localStorage.getItem(STORAGE_KEY);
      return saved ? JSON.parse(saved) : INCOMING_REQUESTS;
    } catch {
      return INCOMING_REQUESTS;
    }
  });
  const [selectedId, setSelectedId] = useState(() => requests[0]?.id ?? null);
  const [filter, setFilter] = useState('All requests');
  const [search, setSearch] = useState('');
  const [response, setResponse] = useState('');
  const [sent, setSent] = useState(false);

  useEffect(() => {
    try {
      localStorage.setItem(STORAGE_KEY, JSON.stringify(requests));
    } catch {
      console.warn('[Help Center] Unable to save replies in this browser.');
    }
  }, [requests]);

  const needsReplyCount = requests.filter((request) => request.status === 'needs reply').length;
  const selectedRequest = requests.find((request) => request.id === selectedId);
  const visibleRequests = requests.filter((request) => {
    const matchesFilter = filter === 'All requests' || request.status === filter.toLowerCase();
    const searchText = `${request.subject} ${request.requester} ${request.organization} ${request.id}`.toLowerCase();
    return matchesFilter && searchText.includes(search.toLowerCase());
  });

  const handleSendResponse = (event) => {
    event.preventDefault();
    const messageBody = response.trim();
    if (!messageBody || !selectedRequest) return;
    const now = new Date().toISOString();
    const adminMessage = {
      id: `${selectedRequest.id}-${Date.now()}`,
      author: adminName,
      role: 'admin',
      body: messageBody,
      createdAt: now,
    };
    setRequests((current) => current.map((request) => request.id === selectedRequest.id
      ? { ...request, status: 'replied', updatedAt: now, messages: [...request.messages, adminMessage] }
      : request));
    setResponse('');
    setSent(true);
  };

  const statusStyles = {
    'needs reply': 'bg-amber-50 text-amber-700 ring-amber-600/15',
    replied: 'bg-emerald-50 text-emerald-700 ring-emerald-600/15',
  };

  return (
    <section className="space-y-5">
      <PageHeader
        title="Help Center"
        subtitle="Review requests from your users and reply directly in each conversation."
      />

      <div className="grid gap-3 sm:grid-cols-3">
        <div className="flex items-center gap-3 rounded-lg border border-slate-200 bg-white p-4">
          <span className="grid size-10 shrink-0 place-items-center rounded-lg bg-indigo-50 text-indigo-600"><MessageSquareText size={19} /></span>
          <div><p className="text-xl font-bold leading-none text-slate-900">{requests.length}</p><p className="mt-1 text-xs text-slate-500">User requests</p></div>
        </div>
        <div className="flex items-center gap-3 rounded-lg border border-amber-200 bg-amber-50/40 p-4">
          <span className="grid size-10 shrink-0 place-items-center rounded-lg bg-amber-100 text-amber-700"><Clock3 size={19} /></span>
          <div><p className="text-xl font-bold leading-none text-slate-900">{needsReplyCount}</p><p className="mt-1 text-xs text-slate-500">Waiting for your reply</p></div>
        </div>
        <div className="flex items-center gap-3 rounded-lg border border-slate-200 bg-white p-4">
          <span className="grid size-10 shrink-0 place-items-center rounded-lg bg-emerald-50 text-emerald-600"><ShieldCheck size={19} /></span>
          <div><p className="text-sm font-semibold leading-none text-slate-900">Admin response desk</p><p className="mt-1 text-xs text-slate-500">Replies are added to the thread</p></div>
        </div>
      </div>

      <div className="grid min-h-[590px] overflow-hidden rounded-lg border border-slate-200 bg-white lg:grid-cols-[340px_minmax(0,1fr)]">
        <aside className="flex min-h-0 flex-col border-b border-slate-200 lg:border-b-0 lg:border-r">
          <div className="border-b border-slate-100 p-4">
            <div className="mb-3 flex items-center justify-between gap-2">
              <div><h2 className="text-sm font-bold text-slate-900">Incoming requests</h2><p className="mt-0.5 text-xs text-slate-500">Select a conversation to respond</p></div>
              <span className="grid size-9 place-items-center rounded-md bg-indigo-50 text-indigo-600"><LifeBuoy size={17} /></span>
            </div>
            <label className="relative block">
              <Search size={16} className="absolute left-3 top-1/2 -translate-y-1/2 text-slate-400" aria-hidden="true" />
              <input value={search} onChange={(event) => setSearch(event.target.value)} placeholder="Search by user or request" className="h-10 w-full rounded-md border border-slate-200 bg-slate-50 pl-9 pr-3 text-sm text-slate-800 outline-none transition placeholder:text-slate-400 focus:border-indigo-400 focus:bg-white focus:ring-2 focus:ring-indigo-100" />
            </label>
            <div className="mt-3 flex gap-1 rounded-md bg-slate-100 p-1" role="group" aria-label="Filter requests">
              {FILTERS.map((item) => (
                <button key={item} type="button" onClick={() => setFilter(item)} className={`flex-1 rounded px-2 py-1.5 text-xs font-medium transition ${filter === item ? 'bg-white text-indigo-700 shadow-sm' : 'text-slate-500 hover:text-slate-800'}`}>{item}</button>
              ))}
            </div>
          </div>
          <div className="max-h-[280px] flex-1 overflow-y-auto p-2 lg:max-h-none">
            {visibleRequests.length ? visibleRequests.map((request) => (
              <button key={request.id} type="button" onClick={() => { setSelectedId(request.id); setSent(false); }} className={`mb-1 w-full rounded-md border p-3 text-left transition ${selectedId === request.id ? 'border-indigo-200 bg-indigo-50/70' : 'border-transparent hover:bg-slate-50'}`}>
                <span className="flex items-start justify-between gap-2"><span className="line-clamp-2 text-sm font-semibold leading-5 text-slate-800">{request.subject}</span><ChevronRight size={15} className="mt-0.5 shrink-0 text-slate-400" /></span>
                <span className="mt-2 flex items-center justify-between gap-2"><span className="truncate text-xs text-slate-500">{request.requester} · {request.organization}</span><span className={`shrink-0 rounded-full px-2 py-0.5 text-[10px] font-semibold ring-1 ring-inset ${statusStyles[request.status]}`}>{request.status}</span></span>
                <span className="mt-2 block truncate text-xs text-slate-500">{request.messages.at(-1)?.body}</span>
              </button>
            )) : <p className="px-3 py-8 text-center text-sm text-slate-500">No requests match this view.</p>}
          </div>
          <div className="hidden items-center gap-2 border-t border-slate-100 px-4 py-3 text-xs text-slate-500 lg:flex"><ShieldCheck size={15} className="text-emerald-600" />Only platform admins can respond.</div>
        </aside>

        <div className="flex min-h-[520px] min-w-0 flex-col">
          {selectedRequest ? (
            <>
              <div className="flex flex-wrap items-center gap-3 border-b border-slate-100 px-5 py-4 sm:px-6">
                <span className="grid size-10 shrink-0 place-items-center rounded-lg bg-slate-100 text-slate-600"><UserRound size={19} /></span>
                <div className="min-w-0 flex-1">
                  <div className="flex flex-wrap items-center gap-2"><h2 className="truncate text-sm font-bold text-slate-900 sm:text-base">{selectedRequest.subject}</h2><span className={`rounded-full px-2 py-0.5 text-[10px] font-semibold ring-1 ring-inset ${statusStyles[selectedRequest.status]}`}>{selectedRequest.status}</span></div>
                  <p className="mt-1 truncate text-xs text-slate-500">{selectedRequest.id} <span className="px-1 text-slate-300">·</span> {selectedRequest.category}</p>
                </div>
              </div>

              <div className="flex flex-wrap items-center gap-x-6 gap-y-2 border-b border-slate-100 bg-white px-5 py-3 text-xs sm:px-6">
                <div><p className="text-[10px] font-semibold uppercase text-slate-400">Requester</p><p className="mt-0.5 font-medium text-slate-800">{selectedRequest.requester}</p></div>
                <div><p className="text-[10px] font-semibold uppercase text-slate-400">Workspace</p><p className="mt-0.5 font-medium text-slate-800">{selectedRequest.organization}</p></div>
                <div><p className="text-[10px] font-semibold uppercase text-slate-400">Role</p><p className="mt-0.5 font-medium text-slate-800">{selectedRequest.role}</p></div>
                <div className="min-w-0"><p className="text-[10px] font-semibold uppercase text-slate-400">Email</p><p className="mt-0.5 truncate font-medium text-indigo-700">{selectedRequest.email}</p></div>
              </div>

              <div className="flex-1 space-y-5 overflow-y-auto bg-slate-50/60 px-4 py-5 sm:px-6">
                <div className="mx-auto flex w-fit items-center gap-2 rounded-full border border-slate-200 bg-white px-3 py-1.5 text-[11px] text-slate-500"><ShieldCheck size={13} className="text-emerald-600" />Private support conversation</div>
                {selectedRequest.messages.map((message) => {
                  const isAdmin = message.role === 'admin';
                  return (
                    <div key={message.id} className={`flex items-end gap-2.5 ${isAdmin ? 'flex-row-reverse' : ''}`}>
                      <span className={`grid size-8 shrink-0 place-items-center rounded-full text-xs font-bold ${isAdmin ? 'bg-indigo-600 text-white' : 'bg-sky-100 text-sky-800'}`}>{isAdmin ? <ShieldCheck size={15} /> : message.author.slice(0, 1).toUpperCase()}</span>
                      <div className={`max-w-[min(88%,38rem)] ${isAdmin ? 'text-right' : ''}`}>
                        <div className={`mb-1 flex items-center gap-2 text-[11px] text-slate-500 ${isAdmin ? 'justify-end' : ''}`}><span className="font-semibold text-slate-700">{isAdmin ? `${message.author} · Platform admin` : message.author}</span><span>{formatTime(message.createdAt)}</span></div>
                        <div className={`rounded-xl px-4 py-3 text-left text-sm leading-relaxed ${isAdmin ? 'rounded-br-sm bg-indigo-600 text-white' : 'rounded-bl-sm border border-slate-200 bg-white text-slate-700 shadow-sm'}`}>{message.body}</div>
                        {isAdmin && <span className="mt-1 inline-flex items-center gap-1 text-[10px] text-slate-400">Sent <CheckCheck size={12} className="text-indigo-500" /></span>}
                      </div>
                    </div>
                  );
                })}
              </div>

              <form onSubmit={handleSendResponse} className="border-t border-slate-100 bg-white p-4 sm:px-6">
                {sent && <p className="mb-2 flex items-center gap-1.5 text-xs font-medium text-emerald-700" role="status"><CheckCheck size={14} />Your response was added to the conversation.</p>}
                <label className="sr-only" htmlFor="admin-response">Write your response</label>
                <textarea id="admin-response" rows={3} maxLength={3000} value={response} onChange={(event) => setResponse(event.target.value)} placeholder="Write your response to the user…" className="w-full resize-y rounded-md border border-slate-200 px-3 py-2.5 text-sm text-slate-800 outline-none placeholder:text-slate-400 focus:border-indigo-400 focus:ring-2 focus:ring-indigo-100" />
                <div className="mt-2 flex flex-wrap items-center justify-between gap-2"><p className="text-xs text-slate-400">Your response will appear in the conversation thread.</p><button type="submit" disabled={!response.trim()} className="inline-flex h-9 items-center gap-2 rounded-md bg-indigo-600 px-3.5 text-sm font-semibold text-white transition hover:bg-indigo-700 disabled:cursor-not-allowed disabled:opacity-50"><Send size={14} />Send response</button></div>
              </form>
            </>
          ) : (
            <div className="flex flex-1 flex-col items-center justify-center px-6 py-14 text-center">
              <span className="grid size-14 place-items-center rounded-2xl bg-indigo-50 text-indigo-600"><MessageSquareText size={25} /></span>
              <h2 className="mt-4 text-base font-bold text-slate-900">Select a user request</h2>
              <p className="mt-1 max-w-sm text-sm text-slate-500">Incoming comments and requests will appear here for your team to review and answer.</p>
            </div>
          )}
        </div>
      </div>

      <p className="flex items-center gap-1.5 px-1 text-xs text-slate-500"><Clock3 size={13} className="text-indigo-500" />Demo inbox: replies are stored in this browser until a support API is connected.</p>
    </section>
  );
};

export default HelpCenter;
