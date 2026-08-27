import { useEffect, useState } from 'react';
import { useSearchParams, useNavigate } from 'react-router-dom';
import Button from '../../components/common/Button';
import { verifyEmail } from '../../core/services/auth.service';
import RoutePath from '../../core/constants/routes.constant';
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

  useEffect(() => {
    if (!token) return;

    let cancelled = false;

    verifyEmail(token)
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
            <Button to={RoutePath.LOGIN}>Back to login</Button>
          </>
        )}
      </div>
    </div>
  );
};

export default VerifyEmail;
