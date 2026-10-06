import { useEffect, useRef, useState } from 'react';
import { useSearchParams, useNavigate } from 'react-router-dom';
import Button from '../../components/common/Button';
import { verifyEmail, resendVerification } from '../../core/services/auth.service';
import RoutePath from '../../core/constants/routes.constant';
import { apiErrorMessage } from '../../core/utils/apiError';
import { validateEmail, sanitizeEmail } from '../../utils/validation';
import { CheckCircle2, XCircle, Loader2 } from 'lucide-react';

const REDIRECT_DELAY_MS = 2000;

const VerifyEmail = () => {
  const [searchParams] = useSearchParams();
  const navigate = useNavigate();
  const token = searchParams.get('token');

  // No token means there's nothing to verify — decide that during render rather
  // than via a setState-on-mount effect.
  const [status, setStatus] = useState(token ? 'verifying' : 'error'); // verifying | success | error
  const [error, setError] = useState(token ? '' : 'This verification link is missing its token.');
  const [resendEmail, setResendEmail] = useState('');
  const [resendNotice, setResendNotice] = useState('');
  // StrictMode runs effects twice in dev; reuse the first request so the single-use token isn't sent again.
  const request = useRef(null);

  useEffect(() => {
    if (!token) return;

    let cancelled = false;

    if (request.current?.token !== token) {
      request.current = { token, promise: verifyEmail(token) };
    }

    request.current.promise
      .then(() => {
        if (cancelled) return;
        setStatus('success');
        // Let the confirmation register before bouncing to login.
        setTimeout(() => {
          if (!cancelled) navigate(`${RoutePath.LOGIN}?verified=true`, { replace: true });
        }, REDIRECT_DELAY_MS);
      })
      .catch((err) => {
        if (cancelled) return;
        setStatus('error');
        setError(err.response?.data?.message || 'This verification link is invalid or has expired.');
      });

    return () => {
      cancelled = true;
    };
  }, [token, navigate]);

  const handleResend = async (e) => {
    e.preventDefault();
    const emailErr = validateEmail(resendEmail);
    if (emailErr) {
      setResendNotice(emailErr);
      return;
    }
    try {
      await resendVerification(resendEmail);
      setResendNotice('If that account is awaiting verification, a new link has been sent.');
    } catch (err) {
      setResendNotice(apiErrorMessage(err));
    }
  };

  return (
    <div className="flex min-h-screen items-center justify-center bg-[#FAFAFA] p-8">
      <div className="w-full max-w-[420px] text-center bg-white rounded-2xl shadow-sm border border-gray-100 p-10">
        {status === 'verifying' && (
          <>
            <Loader2 className="w-10 h-10 text-indigo-500 animate-spin mx-auto mb-4" />
            <h2 className="text-xl font-bold text-gray-900">Verifying your email…</h2>
            <p className="text-gray-500 text-sm mt-2">Hang tight, this only takes a second.</p>
          </>
        )}

        {status === 'success' && (
          <>
            <CheckCircle2 className="w-12 h-12 text-green-500 mx-auto mb-4" />
            <h2 className="text-xl font-bold text-gray-900">Email confirmed</h2>
            <p className="text-gray-500 text-sm mt-2">Redirecting you to sign in…</p>
          </>
        )}

        {status === 'error' && (
          <>
            <XCircle className="w-12 h-12 text-red-500 mx-auto mb-4" />
            <h2 className="text-xl font-bold text-gray-900">Verification failed</h2>
            <p className="text-gray-500 text-sm mt-2 mb-6">{error}</p>
            <form onSubmit={handleResend} className="mb-4 flex gap-2">
              <input
                type="email"
                aria-label="Your email"
                placeholder="you@company.com"
                className="min-w-0 flex-1 px-3 py-2 border border-gray-200 rounded-xl text-sm focus:ring-2 focus:ring-indigo-500 focus:border-indigo-500"
                value={resendEmail}
                onChange={(e) => setResendEmail(sanitizeEmail(e.target.value))}
              />
              <Button type="submit">Resend link</Button>
            </form>
            {resendNotice && <p className="text-gray-600 text-sm mb-4">{resendNotice}</p>}
            <Button to={RoutePath.LOGIN} variant="outline">Back to login</Button>
          </>
        )}
      </div>
    </div>
  );
};

export default VerifyEmail;
