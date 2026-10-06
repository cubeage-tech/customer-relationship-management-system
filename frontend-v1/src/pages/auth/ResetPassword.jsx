import { useState } from 'react';
import { useSearchParams, useNavigate } from 'react-router-dom';
import Button from '../../components/common/Button';
import { resetPassword } from '../../core/services/auth.service';
import RoutePath from '../../core/constants/routes.constant';
import { VALIDATION_PATTERNS, VALIDATION_MESSAGES } from '../../core/constants/validation.constant';
import { CheckCircle2, XCircle } from 'lucide-react';

const REDIRECT_DELAY_MS = 2000;

const ResetPassword = () => {
  const [searchParams] = useSearchParams();
  const navigate = useNavigate();
  const token = searchParams.get('token');

  const [form, setForm] = useState({ newPassword: '', confirmPassword: '' });
  const [error, setError] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [done, setDone] = useState(false);

  const handleChange = (e) => {
    const { name, value } = e.target;
    setForm((prev) => ({ ...prev, [name]: value }));
  };

  const handleSubmit = async (e) => {
    e.preventDefault();

    if (form.newPassword.length < VALIDATION_PATTERNS.PASSWORD_MIN_LENGTH) {
      setError(VALIDATION_MESSAGES.PASSWORD_TOO_SHORT);
      return;
    }
    if (form.newPassword !== form.confirmPassword) {
      setError('Passwords do not match');
      return;
    }

    setError('');
    setSubmitting(true);
    try {
      await resetPassword(token, form.newPassword);
      setDone(true);
      setTimeout(() => navigate(RoutePath.LOGIN, { replace: true }), REDIRECT_DELAY_MS);
    } catch (err) {
      setError(err.response?.data?.message || 'This reset link is invalid or has expired.');
    } finally {
      setSubmitting(false);
    }
  };

  const inputClass =
    'block w-full px-3 py-3 border border-gray-200 rounded-xl focus:ring-2 focus:ring-indigo-500 focus:border-indigo-500 text-sm bg-white shadow-sm';

  return (
    <div className="flex min-h-screen items-center justify-center bg-[#FAFAFA] p-8">
      <div className="w-full max-w-[420px] bg-white rounded-2xl shadow-sm border border-gray-100 p-10">
        {!token && (
          <div className="text-center">
            <XCircle className="w-12 h-12 text-red-500 mx-auto mb-4" />
            <h2 className="text-xl font-bold text-gray-900">Invalid reset link</h2>
            <p className="text-gray-500 text-sm mt-2 mb-6">This reset link is missing its token.</p>
            <Button to={RoutePath.FORGOT_PASSWORD}>Request a new link</Button>
          </div>
        )}

        {token && done && (
          <div className="text-center">
            <CheckCircle2 className="w-12 h-12 text-green-500 mx-auto mb-4" />
            <h2 className="text-xl font-bold text-gray-900">Password updated</h2>
            <p className="text-gray-500 text-sm mt-2">Redirecting you to sign in…</p>
          </div>
        )}

        {token && !done && (
          <>
            <h2 className="text-2xl font-bold text-gray-900 mb-2">Set a new password</h2>
            <p className="text-gray-500 text-sm mb-6">Choose a password with at least {VALIDATION_PATTERNS.PASSWORD_MIN_LENGTH} characters.</p>

            <form onSubmit={handleSubmit} className="space-y-5">
              <div>
                <label className="block text-sm font-semibold text-gray-700 mb-1.5" htmlFor="newPassword">New password</label>
                <input
                  id="newPassword"
                  type="password"
                  name="newPassword"
                  autoComplete="new-password"
                  className={inputClass}
                  value={form.newPassword}
                  onChange={handleChange}
                  required
                />
              </div>

              <div>
                <label className="block text-sm font-semibold text-gray-700 mb-1.5" htmlFor="confirmPassword">Confirm password</label>
                <input
                  id="confirmPassword"
                  type="password"
                  name="confirmPassword"
                  autoComplete="new-password"
                  className={inputClass}
                  value={form.confirmPassword}
                  onChange={handleChange}
                  required
                />
              </div>

              {error && <p className="text-red-500 text-sm font-medium">{error}</p>}

              <button
                type="submit"
                disabled={submitting}
                className="w-full flex justify-center py-3.5 px-4 rounded-xl text-sm font-semibold text-white bg-indigo-500 hover:bg-indigo-600 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-indigo-500 transition-all disabled:opacity-60"
              >
                {submitting ? 'Saving…' : 'Reset password'}
              </button>
            </form>
          </>
        )}
      </div>
    </div>
  );
};

export default ResetPassword;
