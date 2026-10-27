import { useEffect, useState } from 'react';
import QRCode from 'qrcode';

/**
 * SEC-1c: what to add to the authenticator app — a QR code of the otpauth link, and the key to
 * type in by hand. Tapping the link on a phone opens the app directly.
 */
const MfaSetupCode = ({ secret, otpauthUri }) => {
  const [qr, setQr] = useState({ uri: null, src: null });

  useEffect(() => {
    let active = true;
    QRCode.toDataURL(otpauthUri, { margin: 1, width: 192 })
      .then((src) => { if (active) setQr({ uri: otpauthUri, src }); })
      .catch(() => { if (active) setQr({ uri: otpauthUri, src: null }); });
    return () => { active = false; };
  }, [otpauthUri]);

  const grouped = secret.match(/.{1,4}/g)?.join(' ') ?? secret;

  return (
    <div className="flex flex-wrap items-center gap-4">
      {qr.uri === otpauthUri && qr.src && (
        <img src={qr.src} alt="QR code to add SmartCRM to your authenticator app" width={192} height={192} className="rounded border border-slate-200" />
      )}
      <div className="text-sm min-w-0">
        <p className="text-slate-600">Scan the code with an authenticator app (Google Authenticator, Microsoft Authenticator, 1Password…), or enter this key:</p>
        <p className="mt-2 font-mono text-base tracking-wider text-slate-900 break-all select-all">{grouped}</p>
        <a href={otpauthUri} className="mt-2 inline-block text-primary hover:underline sm:hidden">Open in authenticator app</a>
      </div>
    </div>
  );
};

export default MfaSetupCode;
