import { useEffect, useRef, useState } from 'react';
import { AlertTriangle, Check, CheckCircle2, Clock, XCircle } from 'lucide-react';
import PageHeader from '../../components/common/PageHeader';
import EmptyState from '../../components/common/EmptyState';
import Button from '../../components/common/Button';
import {
  getCurrentSubscription,
  getPlanOptions,
  startRenewal,
  startUpgrade,
  verifyPayment,
} from '../../core/services/subscription.service';
import { CheckoutDismissedError, openCheckout } from '../../core/utils/paymentCheckout';

// Status values come from the backend (lowercase, like every other enum in the API).
const STATUS_STYLES = {
  trial: { label: 'Trial', className: 'bg-blue-50 text-blue-700 border-blue-200' },
  active: { label: 'Active', className: 'bg-emerald-50 text-emerald-700 border-emerald-200' },
  past_due: { label: 'Past due', className: 'bg-amber-50 text-amber-700 border-amber-200' },
  cancelled: { label: 'Cancelled', className: 'bg-slate-100 text-slate-700 border-slate-300' },
  expired: { label: 'Expired', className: 'bg-red-50 text-red-700 border-red-200' },
};

const CYCLE_LABEL = { monthly: 'month', annual: 'year' };
const WARN_DAYS = 7;

const formatDate = (value) =>
  value
    ? new Date(value).toLocaleDateString(undefined, { year: 'numeric', month: 'short', day: 'numeric' })
    : '—';

const formatMoney = (amount, currency) => {
  if (amount == null) return null;
  try {
    return new Intl.NumberFormat(undefined, { style: 'currency', currency: currency || 'INR' }).format(amount);
  } catch {
    return `${currency || ''} ${amount}`;
  }
};

const errorMessage = (err, fallback) => err?.response?.data?.message || err?.message || fallback;

// ==================== Pieces ====================

const StatusBadge = ({ status }) => {
  const style = STATUS_STYLES[status] || STATUS_STYLES.expired;
  return (
    <span className={`inline-flex items-center rounded-full border px-2.5 py-0.5 text-xs font-semibold ${style.className}`}>
      {style.label}
    </span>
  );
};

const Banner = ({ tone, children, onClose }) => {
  const tones = {
    success: 'border-emerald-200 bg-emerald-50 text-emerald-800',
    error: 'border-red-200 bg-red-50 text-red-800',
    warning: 'border-amber-200 bg-amber-50 text-amber-800',
  };
  const Icon = tone === 'success' ? CheckCircle2 : tone === 'error' ? XCircle : AlertTriangle;
  return (
    <div className={`mb-4 flex items-start gap-3 rounded-lg border p-4 text-sm ${tones[tone]}`} role="status">
      <Icon size={18} className="mt-0.5 flex-shrink-0" aria-hidden="true" />
      <div className="flex-1">{children}</div>
      {onClose && (
        <button type="button" onClick={onClose} className="text-current opacity-60 hover:opacity-100" aria-label="Dismiss">
          ×
        </button>
      )}
    </div>
  );
};

const UsageBar = ({ label, item }) => {
  const unlimited = item?.limit == null;
  const used = item?.used ?? 0;
  const percent = unlimited ? 0 : Math.min(100, item.limit === 0 ? 100 : Math.round((used / item.limit) * 100));
  const barColor = percent >= 90 ? 'bg-destructive' : percent >= 70 ? 'bg-warning' : 'bg-primary';

  return (
    <div>
      <div className="flex items-baseline justify-between text-sm">
        <span className="font-medium text-slate-700">{label}</span>
        <span className="text-slate-500">
          {unlimited ? `${used.toLocaleString()} · Unlimited` : `${used.toLocaleString()} / ${item.limit.toLocaleString()}`}
        </span>
      </div>
      <div className="mt-2 h-2 w-full overflow-hidden rounded-full bg-slate-100">
        {!unlimited && <div className={`h-full rounded-full ${barColor}`} style={{ width: `${percent}%` }} />}
      </div>
      {!unlimited && percent >= 90 && (
        <p className="mt-1 text-xs text-red-600">
          {used >= item.limit ? 'Limit reached — upgrade to add more.' : 'Almost at your limit.'}
        </p>
      )}
    </div>
  );
};

const TimeRemaining = ({ subscription, onRenew }) => {
  const { status, daysRemaining, totalDays, expiresAt } = subscription;
  const isTrial = status === 'trial';
  const elapsedPercent = totalDays > 0
    ? Math.min(100, Math.max(0, Math.round(((totalDays - daysRemaining) / totalDays) * 100)))
    : 100;

  if (status === 'expired') {
    return (
      <div className="rounded-lg border border-red-200 bg-red-50 p-5">
        <p className="font-semibold text-red-800">Your plan expired on {formatDate(expiresAt)}</p>
        <p className="mt-1 text-sm text-red-700">
          Your data is safe but read-only. Renew or upgrade to keep adding customers, leads and campaigns.
        </p>
        <Button className="mt-3" variant="destructive" onClick={onRenew}>Renew or upgrade</Button>
      </div>
    );
  }

  if (status === 'past_due') {
    return (
      <div className="rounded-lg border border-amber-200 bg-amber-50 p-5">
        <p className="font-semibold text-amber-800">Payment overdue since {formatDate(expiresAt)}</p>
        <p className="mt-1 text-sm text-amber-700">
          You still have full access for a short grace period. Renew now to avoid your workspace becoming read-only.
        </p>
        <Button className="mt-3" variant="warning" onClick={onRenew}>Renew now</Button>
      </div>
    );
  }

  const warn = daysRemaining <= WARN_DAYS;
  const endLabel = isTrial ? 'Trial ends' : status === 'cancelled' ? 'Access ends' : 'Renews on';

  return (
    <div>
      <div className="flex items-baseline justify-between">
        <p className={`text-2xl font-bold ${warn ? 'text-amber-600' : 'text-slate-900'}`}>
          {daysRemaining} {daysRemaining === 1 ? 'day' : 'days'} left
          {isTrial && <span className="ml-2 text-sm font-medium text-slate-500">in your free trial</span>}
        </p>
        <p className="text-sm text-slate-500">
          {endLabel} {formatDate(expiresAt)}
        </p>
      </div>
      <div className="mt-3 h-2 w-full overflow-hidden rounded-full bg-slate-100">
        <div className={`h-full rounded-full ${warn ? 'bg-warning' : 'bg-primary'}`} style={{ width: `${elapsedPercent}%` }} />
      </div>
      {warn && (
        <p className="mt-2 flex items-center gap-1.5 text-sm text-amber-700">
          <Clock size={14} aria-hidden="true" />
          {isTrial
            ? 'Your trial is ending soon — choose a plan to keep full access.'
            : status === 'cancelled'
              ? 'Your subscription is cancelled and ends soon.'
              : 'Your plan ends soon. Renew to avoid interruption.'}
        </p>
      )}
    </div>
  );
};

const PlanCard = ({ plan, cycle, onUpgrade, busy }) => {
  const price = cycle === 'annual' ? plan.annualPrice : plan.monthlyPrice;
  const limit = (value) => (value == null ? 'Unlimited' : value.toLocaleString());

  return (
    <div className={`flex flex-col rounded-lg border p-5 ${plan.current ? 'border-primary bg-blue-50/40' : 'border-slate-200 bg-white'}`}>
      <div className="flex items-center justify-between">
        <h3 className="text-lg font-semibold text-slate-900">{plan.name}</h3>
        {plan.current && (
          <span className="rounded-full bg-slate-200 px-2 py-0.5 text-xs font-medium text-slate-700">Current Plan</span>
        )}
      </div>
      {plan.description && <p className="mt-1 text-sm text-slate-500">{plan.description}</p>}
      <p className="mt-4 text-2xl font-bold text-slate-900">
        {price != null ? formatMoney(price, plan.currency) : 'Contact us'}
        {price != null && <span className="ml-1 text-sm font-medium text-slate-500">/ {CYCLE_LABEL[cycle]}</span>}
      </p>
      <ul className="mt-4 space-y-1.5 text-sm text-slate-600">
        <li>{limit(plan.maxUsers)} users</li>
        <li>{limit(plan.maxCustomers)} customers</li>
        <li>{limit(plan.maxCampaigns)} campaigns</li>
      </ul>
      <div className="mt-5 flex-1" />
      {plan.current ? (
        <Button variant="outline" disabled fullWidth>Current Plan</Button>
      ) : (
        <Button fullWidth disabled={busy || price == null} onClick={() => onUpgrade(plan)}>
          Upgrade
        </Button>
      )}
    </div>
  );
};

const ConfirmDialog = ({ title, children, confirmLabel, onConfirm, onCancel, busy }) => (
  <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-900/50 p-4" role="dialog" aria-modal="true" aria-labelledby="confirm-title">
    <div className="w-full max-w-md rounded-lg bg-white p-6 shadow-xl">
      <h2 id="confirm-title" className="text-lg font-semibold text-slate-900">{title}</h2>
      <div className="mt-2 text-sm text-slate-600">{children}</div>
      <div className="mt-6 flex justify-end gap-3">
        <Button variant="outline" onClick={onCancel} disabled={busy}>Cancel</Button>
        <Button onClick={onConfirm} loading={busy}>{confirmLabel}</Button>
      </div>
    </div>
  </div>
);

// ==================== Page ====================

const MyPlan = () => {
  const [subscription, setSubscription] = useState(null);
  const [plans, setPlans] = useState([]);
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState(null);
  const [cycle, setCycle] = useState('monthly');
  const [pending, setPending] = useState(null); // { kind: 'upgrade' | 'renew', plan }
  const [busy, setBusy] = useState(false);
  const [notice, setNotice] = useState(null); // { tone, text }
  const [reloadKey, setReloadKey] = useState(0); // bump to refetch (after payment, on retry)
  const upgradeSectionRef = useRef(null);

  useEffect(() => {
    let cancelled = false;
    Promise.all([getCurrentSubscription(), getPlanOptions()])
      .then(([current, options]) => {
        if (cancelled) return;
        setSubscription(current);
        setPlans(options || []);
        setLoadError(null);
      })
      .catch((err) => {
        if (!cancelled) setLoadError(err);
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, [reloadKey]);

  const refetch = () => setReloadKey((key) => key + 1);

  const retry = () => {
    setLoading(true);
    refetch();
  };

  const runCheckout = async (orderPromise, description) => {
    setBusy(true);
    setNotice(null);
    try {
      const order = await orderPromise;
      setPending(null);
      const paymentResponse = await openCheckout(order, { description });
      const verified = await verifyPayment(paymentResponse);
      if (verified !== true) throw new Error('We could not confirm your payment.');
      setNotice({ tone: 'success', text: 'Payment successful — your plan has been updated.' });
    } catch (err) {
      if (err instanceof CheckoutDismissedError) {
        setNotice({ tone: 'warning', text: 'Payment was cancelled. Your plan has not changed.' });
      } else {
        setNotice({ tone: 'error', text: errorMessage(err, 'Payment failed. Your plan has not changed.') });
      }
    } finally {
      setPending(null);
      setBusy(false);
      refetch();
    }
  };

  const confirmPending = () => {
    const { kind, plan } = pending;
    if (kind === 'upgrade') {
      runCheckout(startUpgrade(plan.id, cycle), `Upgrade to ${plan.name} (${cycle})`);
    } else {
      runCheckout(startRenewal(plan.code, cycle), `Renew ${plan.name} (${cycle})`);
    }
  };

  const scrollToUpgrades = () => upgradeSectionRef.current?.scrollIntoView({ behavior: 'smooth' });

  // ---------- states ----------

  if (loading) {
    return (
      <div className="flex min-h-[40vh] items-center justify-center" role="status" aria-label="Loading your plan">
        <span className="h-8 w-8 animate-spin rounded-full border-2 border-border border-t-primary" />
      </div>
    );
  }

  if (loadError) {
    const notFound = loadError.response?.data?.errorCode === 'SUBSCRIPTION_NOT_FOUND';
    return (
      <section>
        <PageHeader title="My Current Plan" />
        <EmptyState
          title={notFound ? 'No subscription yet' : 'Could not load your plan'}
          message={notFound
            ? 'Your company does not have a subscription yet. It starts when the account owner verifies their email.'
            : errorMessage(loadError, 'Something went wrong while loading your plan.')}
          action={!notFound && <Button variant="outline" onClick={retry}>Try again</Button>}
        />
      </section>
    );
  }

  const currentOption = plans.find((plan) => plan.current);
  const visiblePlans = plans.filter((plan) => plan.current || plan.upgrade);
  const lapsed = subscription.status === 'expired' || subscription.status === 'past_due';
  const priceText = formatMoney(subscription.price, subscription.currency);

  return (
    <section>
      <PageHeader title="My Current Plan" subtitle="Your company's subscription, usage and upgrade options." />

      {notice && (
        <Banner tone={notice.tone} onClose={() => setNotice(null)}>{notice.text}</Banner>
      )}

      <div className="grid gap-4 lg:grid-cols-3">
        {/* Current plan */}
        <div className="rounded-lg border border-slate-200 bg-white p-5 lg:col-span-1">
          <div className="flex items-center justify-between">
            <p className="text-sm text-slate-500">Current plan</p>
            <StatusBadge status={subscription.status} />
          </div>
          <h2 className="mt-2 text-2xl font-bold text-slate-900">{subscription.planName}</h2>
          <p className="mt-1 text-sm text-slate-600">
            {subscription.status === 'trial'
              ? priceText ? `Free trial · then ${priceText} / month` : 'Free trial'
              : priceText ? `${priceText} / ${CYCLE_LABEL[subscription.billingCycle] || 'month'}` : '—'}
          </p>
          <dl className="mt-4 space-y-2 text-sm">
            <div className="flex justify-between">
              <dt className="text-slate-500">Started</dt>
              <dd className="text-slate-900">{formatDate(subscription.startedAt)}</dd>
            </div>
            <div className="flex justify-between">
              <dt className="text-slate-500">
                {subscription.status === 'trial' ? 'Trial ends' : lapsed ? 'Expired on' : 'Renews / expires'}
              </dt>
              <dd className="text-slate-900">{formatDate(subscription.expiresAt)}</dd>
            </div>
            <div className="flex justify-between">
              <dt className="text-slate-500">Auto-renew</dt>
              <dd className="text-slate-900">{subscription.autoRenew ? 'On' : 'Off — renew manually'}</dd>
            </div>
          </dl>
        </div>

        {/* Time remaining + usage */}
        <div className="space-y-4 lg:col-span-2">
          <div className="rounded-lg border border-slate-200 bg-white p-5">
            <h2 className="mb-3 font-semibold text-slate-900">Time remaining</h2>
            <TimeRemaining
              subscription={subscription}
              onRenew={() => (currentOption ? setPending({ kind: 'renew', plan: currentOption }) : scrollToUpgrades())}
            />
          </div>

          <div className="rounded-lg border border-slate-200 bg-white p-5">
            <h2 className="mb-4 font-semibold text-slate-900">Usage</h2>
            <div className="space-y-4">
              <UsageBar label="Users" item={subscription.usage?.users} />
              <UsageBar label="Customers" item={subscription.usage?.customers} />
              <UsageBar label="Campaigns" item={subscription.usage?.campaigns} />
            </div>
          </div>
        </div>
      </div>

      {/* Features */}
      {subscription.features?.length > 0 && (
        <div className="mt-4 rounded-lg border border-slate-200 bg-white p-5">
          <h2 className="mb-3 font-semibold text-slate-900">Included in {subscription.planName}</h2>
          <ul className="grid gap-2 sm:grid-cols-2">
            {subscription.features.map((feature) => (
              <li key={feature} className="flex items-start gap-2 text-sm text-slate-700">
                <Check size={16} className="mt-0.5 flex-shrink-0 text-emerald-600" aria-hidden="true" />
                {feature}
              </li>
            ))}
          </ul>
        </div>
      )}

      {/* Upgrade */}
      <div ref={upgradeSectionRef} className="mt-6">
        <div className="mb-4 flex flex-wrap items-center justify-between gap-3">
          <div>
            <h2 className="text-lg font-semibold text-slate-900">
              {subscription.canUpgrade ? 'Upgrade your plan' : 'Your plan'}
            </h2>
            <p className="text-sm text-slate-500">
              {subscription.canUpgrade
                ? 'Your new plan starts as soon as the payment is confirmed.'
                : "You're on our highest plan."}
            </p>
          </div>
          <div className="inline-flex rounded-md border border-slate-200 bg-white p-1" role="group" aria-label="Billing cycle">
            {['monthly', 'annual'].map((option) => (
              <button
                key={option}
                type="button"
                onClick={() => setCycle(option)}
                className={`rounded px-3 py-1.5 text-sm font-medium ${cycle === option ? 'bg-primary text-primary-foreground' : 'text-slate-600 hover:text-slate-900'}`}
                aria-pressed={cycle === option}
              >
                {option === 'monthly' ? 'Monthly' : 'Annual'}
              </button>
            ))}
          </div>
        </div>

        {visiblePlans.length === 0 ? (
          <EmptyState title="No plans available" message="Plans could not be listed right now. Please try again later." />
        ) : (
          <div className="grid gap-4 md:grid-cols-3">
            {visiblePlans.map((plan) => (
              <PlanCard
                key={plan.id}
                plan={plan}
                cycle={cycle}
                busy={busy}
                onUpgrade={(target) => setPending({ kind: 'upgrade', plan: target })}
              />
            ))}
          </div>
        )}
      </div>

      {pending && (
        <ConfirmDialog
          title={pending.kind === 'upgrade' ? `Upgrade to ${pending.plan.name}?` : `Renew ${pending.plan.name}?`}
          confirmLabel="Continue to payment"
          busy={busy}
          onCancel={() => setPending(null)}
          onConfirm={confirmPending}
        >
          <p>
            You'll pay{' '}
            <strong>
              {formatMoney(cycle === 'annual' ? pending.plan.annualPrice : pending.plan.monthlyPrice, pending.plan.currency)}
            </strong>{' '}
            per {CYCLE_LABEL[cycle]}.
            {pending.kind === 'upgrade'
              ? ' The new plan and its limits apply once the payment is confirmed; a new billing period starts then.'
              : ' Your plan is renewed once the payment is confirmed.'}
          </p>
        </ConfirmDialog>
      )}
    </section>
  );
};

export default MyPlan;
