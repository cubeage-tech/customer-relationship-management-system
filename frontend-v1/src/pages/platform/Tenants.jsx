import { useEffect, useMemo, useState } from 'react';
import { Search } from 'lucide-react';
import PageHeader from '../../components/common/PageHeader';
import EmptyState from '../../components/common/EmptyState';
import { listTenants, deactivateTenant, restoreTenant } from '../../core/services/tenant.service';

const STATUS_DOT = {
  active: 'bg-emerald-500',
  deactivated: 'bg-muted-foreground/40',
  suspended: 'bg-amber-500',
  pending_verification: 'bg-amber-400',
};

const formatDate = (value) => (value ? new Date(value).toLocaleDateString() : '—');

const Tenants = () => {
  const [tenants, setTenants] = useState([]);
  const [loading, setLoading] = useState(true);
  const [query, setQuery] = useState('');
  const [pendingId, setPendingId] = useState(null);

  const refresh = () => {
    listTenants()
      .then((data) => setTenants(data ?? []))
      .catch(() => setTenants([]))
      .finally(() => setLoading(false));
  };

  useEffect(() => {
    refresh();
  }, []);

  const filtered = useMemo(() => {
    const q = query.trim().toLowerCase();
    if (!q) return tenants;
    return tenants.filter((tenant) => tenant.companyName?.toLowerCase().includes(q));
  }, [tenants, query]);

  const activeCount = tenants.filter((t) => t.status === 'active').length;
  const deactivatedCount = tenants.filter((t) => t.status === 'deactivated').length;

  const toggleStatus = async (tenant) => {
    setPendingId(tenant.id);
    try {
      if (tenant.status === 'deactivated') {
        await restoreTenant(tenant.id);
      } else {
        await deactivateTenant(tenant.id);
      }
      refresh();
    } finally {
      setPendingId(null);
    }
  };

  return (
    <section>
      <PageHeader
        title="Tenants / Clients"
        subtitle="Every company running on SmartCRM AI."
        actions={
          <div className="relative">
            <Search
              size={15}
              className="pointer-events-none absolute left-3 top-1/2 -translate-y-1/2 text-muted-foreground"
            />
            <input
              type="text"
              value={query}
              onChange={(e) => setQuery(e.target.value)}
              placeholder="Search companies"
              className="w-56 rounded-md border border-border bg-transparent py-1.5 pl-8 pr-3 text-sm text-foreground placeholder:text-muted-foreground focus:outline-none focus:ring-1 focus:ring-ring"
            />
          </div>
        }
      />

      {loading ? (
        <div className="flex justify-center py-16">
          <span className="h-6 w-6 animate-spin rounded-full border-2 border-border border-t-primary" />
        </div>
      ) : tenants.length === 0 ? (
        <EmptyState
          title="No tenants yet"
          message="Companies that sign up will appear here once they register."
        />
      ) : (
        <>
          <p className="mb-4 text-sm text-muted-foreground">
            {tenants.length} tenant{tenants.length === 1 ? '' : 's'}
            <span className="mx-2 text-border">·</span>
            {activeCount} active
            <span className="mx-2 text-border">·</span>
            {deactivatedCount} deactivated
          </p>

          {filtered.length === 0 ? (
            <EmptyState title="No matches" message={`No tenant company matches "${query}".`} />
          ) : (
            <div className="overflow-x-auto border-t border-border">
              <table className="w-full text-left text-sm">
                <thead>
                  <tr className="text-xs font-medium tracking-wide text-muted-foreground">
                    <th className="py-3 pr-4 font-medium">Company</th>
                    <th className="py-3 pr-4 font-medium">Plan</th>
                    <th className="py-3 pr-4 font-medium">Status</th>
                    <th className="py-3 pr-4 font-medium">Joined</th>
                    <th className="py-3 pr-4 font-medium text-right">Action</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-border">
                  {filtered.map((tenant) => {
                    const isDeactivated = tenant.status === 'deactivated';
                    const isPending = pendingId === tenant.id;

                    return (
                      <tr key={tenant.id} className="group transition-colors hover:bg-muted/40">
                        <td className="py-3 pr-4">
                          <p className="font-medium text-foreground">{tenant.companyName}</p>
                          {tenant.legalName && (
                            <p className="text-xs text-muted-foreground">{tenant.legalName}</p>
                          )}
                        </td>
                        <td className="py-3 pr-4 capitalize text-muted-foreground">{tenant.plan}</td>
                        <td className="py-3 pr-4">
                          <span className="inline-flex items-center gap-2 capitalize text-foreground">
                            <span
                              className={`h-1.5 w-1.5 rounded-full ${
                                STATUS_DOT[tenant.status] || 'bg-muted-foreground/40'
                              }`}
                              aria-hidden="true"
                            />
                            {tenant.status?.replace('_', ' ')}
                          </span>
                        </td>
                        <td className="py-3 pr-4 text-muted-foreground">{formatDate(tenant.createdAt)}</td>
                        <td className="py-3 pr-4 text-right">
                          <button
                            type="button"
                            disabled={isPending}
                            onClick={() => toggleStatus(tenant)}
                            className={`text-sm font-medium hover:underline disabled:opacity-40 disabled:hover:no-underline ${
                              isDeactivated ? 'text-primary' : 'text-destructive'
                            }`}
                          >
                            {isPending ? '…' : isDeactivated ? 'Restore' : 'Deactivate'}
                          </button>
                        </td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
            </div>
          )}
        </>
      )}
    </section>
  );
};

export default Tenants;
