import { useState } from 'react';
import { Link } from 'react-router-dom';
import Button from '../../components/common/Button';
import { forgotPassword } from '../../core/services/auth.service';
import RoutePath from '../../core/constants/routes.constant';
import { validateEmail, sanitizeEmail } from '../../utils/validation';
import { MailCheck } from 'lucide-react';

const ForgotPassword = () => {
  const [email, setEmail] = useState('');
  const [error, setError] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [sent, setSent] = useState(false);

  const handleSubmit = async (e) => {
    e.preventDefault();

    const emailErr = validateEmail(email);
    if (emailErr) {
      setError(emailErr);
      return;
    }

    setError('');
    setSubmitting(true);
    try {
      await forgotPassword(email);
      // The backend answers the same way for unknown emails, so always show the same message.
      setSent(true);
    } catch (err) {
      setError(err.response?.data?.message || 'Something went wrong. Please try again.');
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="flex min-h-screen items-center justify-center bg-[#FAFAFA] p-8">
      <div className="w-full max-w-[420px] bg-white rounded-2xl shadow-sm border border-gray-100 p-10">
        {sent ? (
          <div className="text-center">
            <MailCheck className="w-12 h-12 text-indigo-500 mx-auto mb-4" />
            <h2 className="text-xl font-bold text-gray-900">Check your inbox</h2>
            <p className="text-gray-500 text-sm mt-2 mb-6">
              If an account exists for {email}, we've sent a link to reset your password. The link expires in 1 hour.
            </p>
            <Button to={RoutePath.LOGIN}>Back to login</Button>
          </div>
        ) : (
          <>
            <h2 className="text-2xl font-bold text-gray-900 mb-2">Forgot your password?</h2>
            <p className="text-gray-500 text-sm mb-6">Enter your work email and we'll send you a link to reset it.</p>

            <form onSubmit={handleSubmit} className="space-y-5">
              <div>
                <label className="block text-sm font-semibold text-gray-700 mb-1.5" htmlFor="email">Work email</label>
                <input
                  id="email"
                  type="email"
                  name="email"
                  className="block w-full px-3 py-3 border border-gray-200 rounded-xl focus:ring-2 focus:ring-indigo-500 focus:border-indigo-500 text-sm bg-white shadow-sm"
                  placeholder="john.doe@company.com"
                  value={email}
                  onChange={(e) => setEmail(sanitizeEmail(e.target.value))}
                  required
                />
              </div>

              {error && <p className="text-red-500 text-sm font-medium">{error}</p>}

              <button
                type="submit"
                disabled={submitting}
                className="w-full flex justify-center py-3.5 px-4 rounded-xl text-sm font-semibold text-white bg-indigo-500 hover:bg-indigo-600 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-indigo-500 transition-all disabled:opacity-60"
              >
                {submitting ? 'Sending…' : 'Send reset link'}
              </button>
            </form>

            <p className="mt-6 text-center text-sm text-gray-500">
              Remembered it?{' '}
              <Link to={RoutePath.LOGIN} className="font-semibold text-indigo-600 hover:text-indigo-500">
                Back to login
              </Link>
            </p>
          </>
        )}
      </div>
    </div>
  );
};

export default ForgotPassword;
