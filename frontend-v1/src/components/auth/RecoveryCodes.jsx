import { useState } from 'react';

/** SEC-1c: one-time recovery codes, shown once — each signs in once if the phone is lost. */
const RecoveryCodes = ({ codes }) => {
  const [copied, setCopied] = useState(false);
  const copy = async () => {
    try {
      await navigator.clipboard.writeText(codes.join('\n'));
      setCopied(true);
    } catch {
      setCopied(false);
    }
  };
  return (
    <div className="rounded-lg border border-amber-200 bg-amber-50 p-4 text-sm">
      <p className="font-medium text-amber-900">Save your recovery codes</p>
      <p className="text-amber-800 mt-1">If you lose your phone, each code signs you in once. They won&apos;t be shown again.</p>
      <ul className="grid grid-cols-2 gap-x-6 gap-y-1 mt-3 font-mono text-slate-900">
        {codes.map((c) => <li key={c}>{c}</li>)}
      </ul>
      <button type="button" onClick={copy} className="mt-3 text-xs font-medium text-amber-900 underline">
        {copied ? 'Copied' : 'Copy codes'}
      </button>
    </div>
  );
};

export default RecoveryCodes;
