import { useState } from 'react';
import { useNavigate, Link } from 'react-router-dom';
import Button from '../../components/common/Button';
import { signup as signupRequest } from '../../core/services/auth.service';
import RoutePath from '../../core/constants/routes.constant';
import { USER_ROLES } from '../../core/constants/app.constant';
import { NOTIFICATION_MESSAGES } from '../../core/constants/notification.constant';
import { validateSignupForm, getPasswordStrength } from '../../utils/validation';
import { Eye, EyeOff, Bot, ShieldCheck, BarChart3, Sparkles, Check, Landmark } from 'lucide-react';

const STRENGTH_COLORS = ['bg-gray-200', 'bg-red-400', 'bg-orange-400', 'bg-yellow-400', 'bg-green-500'];
const STRENGTH_TEXT_COLORS = ['text-gray-400', 'text-red-500', 'text-orange-500', 'text-yellow-600', 'text-green-600'];

const Signup = () => {
  const [form, setForm] = useState({
    firstName: '',
    lastName: '',
    email: '',
    organizationName: '',
    bankAccountNumber: '',
    password: '',
    confirmPassword: '',
    termsAccepted: false,
  });
  const [error, setError] = useState('');
  const [errors, setErrors] = useState({});
  const [showPassword, setShowPassword] = useState(false);
  const [showConfirmPassword, setShowConfirmPassword] = useState(false);
  const navigate = useNavigate();

  const strength = getPasswordStrength(form.password);

  const handleChange = (e) => {
    const { name } = e.target;
    const value = e.target.type === 'checkbox' ? e.target.checked : e.target.value;
    setForm((prev) => ({ ...prev, [name]: value }));
    setErrors((prev) => (prev[name] ? { ...prev, [name]: '' } : prev));
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    setError('');

    const formErrors = validateSignupForm(form);
    if (Object.keys(formErrors).length > 0) {
      setErrors(formErrors);
      return;
    }
    setErrors({});

    try {
      await signupRequest({
        name: `${form.firstName} ${form.lastName}`.trim(),
        email: form.email,
        password: form.password,
        organizationName: form.organizationName,
        bankAccountNumber: form.bankAccountNumber,
        role: USER_ROLES.ADMIN,
      });
      navigate(RoutePath.LOGIN);
    } catch (err) {
      // Surface the backend's reason (e.g. "A user with this email already
      // exists" on 409) instead of a generic message that hides why signup failed.
      setError(err.response?.data?.message || NOTIFICATION_MESSAGES.GENERIC_ERROR);
    }
  };

  return (
    <div className="flex h-screen bg-white relative overflow-hidden">
      {/* Left side - Marketing/Branding (Hidden on mobile) */}
      <div className="hidden lg:flex lg:w-1/2 bg-gradient-to-br from-indigo-500 to-indigo-600 text-white p-12 flex-col relative overflow-hidden">
        <div className="z-10 flex flex-col h-full max-w-xl mx-auto w-full">
          <div className="flex items-center gap-3 mb-16">
            <div className="w-10 h-10 bg-white/20 rounded-xl flex items-center justify-center backdrop-blur-sm">
              <Sparkles className="w-5 h-5 text-white" />
            </div>
            <div>
              <h1 className="font-bold text-lg leading-tight">SmartCRM AI</h1>
              <p className="text-xs text-indigo-200">Enterprise Suite</p>
            </div>
          </div>

          <div>
            <h2 className="text-4xl font-bold mb-6 leading-tight pr-8">
              The revenue workspace your whole company can trust.
            </h2>
            <p className="text-base text-indigo-100 mb-12 pr-12">
              One secure workspace for sales, marketing, service and finance — with permissions that match how your teams actually operate.
            </p>

            <div className="space-y-4 mb-8">
              <div className="flex items-start gap-4 p-4 rounded-2xl bg-white/10 backdrop-blur-sm border border-white/10 hover:bg-white/15 transition-colors">
                <div className="p-2 bg-white/10 rounded-lg">
                  <Bot className="w-5 h-5 text-white" />
                </div>
                <div>
                  <h3 className="font-semibold text-white text-sm">AI copilot built in</h3>
                  <p className="text-sm text-indigo-100/80">Lead scoring, next-best action and auto summaries.</p>
                </div>
              </div>

              <div className="flex items-start gap-4 p-4 rounded-2xl bg-white/10 backdrop-blur-sm border border-white/10 hover:bg-white/15 transition-colors">
                <div className="p-2 bg-white/10 rounded-lg">
                  <ShieldCheck className="w-5 h-5 text-white" />
                </div>
                <div>
                  <h3 className="font-semibold text-white text-sm">Role-based access</h3>
                  <p className="text-sm text-indigo-100/80">Seven granular roles with SOC 2 aligned audit trails.</p>
                </div>
              </div>

              <div className="flex items-start gap-4 p-4 rounded-2xl bg-white/10 backdrop-blur-sm border border-white/10 hover:bg-white/15 transition-colors">
                <div className="p-2 bg-white/10 rounded-lg">
                  <BarChart3 className="w-5 h-5 text-white" />
                </div>
                <div>
                  <h3 className="font-semibold text-white text-sm">Live revenue clarity</h3>
                  <p className="text-sm text-indigo-100/80">Pipeline, forecast and campaign ROI in one workspace.</p>
                </div>
              </div>
            </div>
          </div>

          <div className="flex justify-between items-center text-xs text-indigo-200/80 font-medium">
            <p>Trusted by 4,200+ revenue teams · 99.99% uptime</p>
          </div>
        </div>
      </div>

      {/* Right side - Signup Form */}
      <div className="w-full lg:w-1/2 flex justify-center p-8 sm:p-12 xl:p-16 relative bg-[#FAFAFA] overflow-y-auto">
        <div className="w-full max-w-[420px] my-auto ">
          <div className="mb-6">
            <p className="text-indigo-600 font-bold text-xs tracking-[0.15em] uppercase mb-3">14-day free trial</p>
            <h2 className="text-3xl font-bold text-gray-900 mb-2">Create your workspace account</h2>
            <p className="text-gray-500 text-sm">No credit card required. You'll be set up as the workspace admin (tenant owner).</p>
          </div>

          <form onSubmit={handleSubmit} className="space-y-4">
            <div className="grid grid-cols-2 gap-4">
              <div>
                <label className="block text-sm font-semibold text-gray-700 mb-1.5" htmlFor="firstName">First name</label>
                <div className="relative">
                  <div className="absolute inset-y-0 left-0 pl-3 flex items-center pointer-events-none">
                    <svg className="h-4 w-4 text-gray-400" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><path d="M19 21v-2a4 4 0 0 0-4-4H9a4 4 0 0 0-4 4v2"></path><circle cx="12" cy="7" r="4"></circle></svg>
                  </div>
                  <input
                    id="firstName"
                    type="text"
                    name="firstName"
                    className={`block w-full pl-9 pr-3 py-2.5 border rounded-xl focus:ring-2 text-sm bg-white shadow-sm transition-shadow hover:border-gray-300 ${
                      errors.firstName ? 'border-red-300 focus:ring-red-500 focus:border-red-500' : 'border-gray-200 focus:ring-indigo-500 focus:border-indigo-500'
                    }`}
                    placeholder="John"
                    value={form.firstName}
                    onChange={handleChange}
                    required
                  />
                </div>
                {errors.firstName && <p className="mt-1 text-xs text-red-500">{errors.firstName}</p>}
              </div>
              <div>
                <label className="block text-sm font-semibold text-gray-700 mb-1.5" htmlFor="lastName">Last name</label>
                <div className="relative">
                  <div className="absolute inset-y-0 left-0 pl-3 flex items-center pointer-events-none">
                    <svg className="h-4 w-4 text-gray-400" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><path d="M19 21v-2a4 4 0 0 0-4-4H9a4 4 0 0 0-4 4v2"></path><circle cx="12" cy="7" r="4"></circle></svg>
                  </div>
                  <input
                    id="lastName"
                    type="text"
                    name="lastName"
                    className={`block w-full pl-9 pr-3 py-2.5 border rounded-xl focus:ring-2 text-sm bg-white shadow-sm transition-shadow hover:border-gray-300 ${
                      errors.lastName ? 'border-red-300 focus:ring-red-500 focus:border-red-500' : 'border-gray-200 focus:ring-indigo-500 focus:border-indigo-500'
                    }`}
                    placeholder="Doe"
                    value={form.lastName}
                    onChange={handleChange}
                    required
                  />
                </div>
                {errors.lastName && <p className="mt-1 text-xs text-red-500">{errors.lastName}</p>}
              </div>
            </div>

            <div>
              <label className="block text-sm font-semibold text-gray-700 mb-1.5" htmlFor="email">Work email</label>
              <div className="relative">
                <div className="absolute inset-y-0 left-0 pl-3 flex items-center pointer-events-none">
                  <svg className="h-4 w-4 text-gray-400" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><path d="M4 4h16c1.1 0 2 .9 2 2v12c0 1.1-.9 2-2 2H4c-1.1 0-2-.9-2-2V6c0-1.1.9-2 2-2z"></path><polyline points="22,6 12,13 2,6"></polyline></svg>
                </div>
                <input
                  id="email"
                  type="email"
                  name="email"
                  className={`block w-full pl-9 pr-3 py-2.5 border rounded-xl focus:ring-2 text-sm bg-white shadow-sm transition-shadow hover:border-gray-300 ${
                    errors.email ? 'border-red-300 focus:ring-red-500 focus:border-red-500' : 'border-gray-200 focus:ring-indigo-500 focus:border-indigo-500'
                  }`}
                  placeholder="john.doe@company.com"
                  value={form.email}
                  onChange={handleChange}
                  required
                />
              </div>
              {errors.email && <p className="mt-1 text-xs text-red-500">{errors.email}</p>}
            </div>

            <div>
              <label className="block text-sm font-semibold text-gray-700 mb-1.5" htmlFor="organizationName">Company</label>
              <div className="relative">
                <div className="absolute inset-y-0 left-0 pl-3 flex items-center pointer-events-none">
                  <svg className="h-4 w-4 text-gray-400" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><path d="M3 9l9-7 9 7v11a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z"></path><polyline points="9 22 9 12 15 12 15 22"></polyline></svg>
                </div>
                <input
                  id="organizationName"
                  type="text"
                  name="organizationName"
                  className={`block w-full pl-9 pr-3 py-2.5 border rounded-xl focus:ring-2 text-sm bg-white shadow-sm transition-shadow hover:border-gray-300 ${
                    errors.organizationName ? 'border-red-300 focus:ring-red-500 focus:border-red-500' : 'border-gray-200 focus:ring-indigo-500 focus:border-indigo-500'
                  }`}
                  placeholder="Acme Corp"
                  value={form.organizationName}
                  onChange={handleChange}
                  required
                />
              </div>
              {errors.organizationName && <p className="mt-1 text-xs text-red-500">{errors.organizationName}</p>}
            </div>

            <div>
              <label className="block text-sm font-semibold text-gray-700 mb-1.5" htmlFor="bankAccountNumber">Bank account number</label>
              <div className="relative">
                <div className="absolute inset-y-0 left-0 pl-3 flex items-center pointer-events-none">
                  <Landmark className="h-4 w-4 text-gray-400" />
                </div>
                <input
                  id="bankAccountNumber"
                  type="text"
                  inputMode="numeric"
                  name="bankAccountNumber"
                  className={`block w-full pl-9 pr-3 py-2.5 border rounded-xl focus:ring-2 text-sm bg-white shadow-sm transition-shadow hover:border-gray-300 ${
                    errors.bankAccountNumber ? 'border-red-300 focus:ring-red-500 focus:border-red-500' : 'border-gray-200 focus:ring-indigo-500 focus:border-indigo-500'
                  }`}
                  placeholder="e.g. 123456789012"
                  value={form.bankAccountNumber}
                  onChange={handleChange}
                  required
                />
              </div>
              {errors.bankAccountNumber ? (
                <p className="mt-1.5 text-xs text-red-500">{errors.bankAccountNumber}</p>
              ) : (
                <p className="mt-1.5 text-xs text-gray-500">Used for billing and payout settlement on your workspace.</p>
              )}
            </div>

            <div>
              <label className="block text-sm font-semibold text-gray-700 mb-1.5" htmlFor="password">Password</label>
              <div className="relative">
                <div className="absolute inset-y-0 left-0 pl-3 flex items-center pointer-events-none">
                  <svg className="h-4 w-4 text-gray-400" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><rect x="3" y="11" width="18" height="11" rx="2" ry="2"></rect><path d="M7 11V7a5 5 0 0 1 10 0v4"></path></svg>
                </div>
                <input
                  id="password"
                  type={showPassword ? 'text' : 'password'}
                  name="password"
                  className={`block w-full pl-9 pr-10 py-2.5 border rounded-xl focus:ring-2 text-sm bg-white shadow-sm transition-shadow hover:border-gray-300 ${
                    errors.password ? 'border-red-300 focus:ring-red-500 focus:border-red-500' : 'border-gray-200 focus:ring-indigo-500 focus:border-indigo-500'
                  }`}
                  placeholder="Create a strong password"
                  value={form.password}
                  onChange={handleChange}
                  required
                />
                <button
                  type="button"
                  className="absolute inset-y-0 right-0 pr-3 flex items-center text-gray-400 hover:text-gray-600 transition-colors"
                  onClick={() => setShowPassword(!showPassword)}
                >
                  {showPassword ? <EyeOff className="h-4 w-4" /> : <Eye className="h-4 w-4" />}
                </button>
              </div>

              {/* Password strength indicator - animated, driven by actual password */}
              <div className="mt-2 flex gap-1 h-1">
                {[0, 1, 2, 3].map((i) => (
                  <div
                    key={i}
                    className={`flex-1 rounded-full transition-colors duration-300 ${
                      i < strength.score ? STRENGTH_COLORS[strength.score] : 'bg-gray-200'
                    }`}
                  ></div>
                ))}
              </div>
              <p className="mt-1 text-xs text-gray-500">
                Password strength:{' '}
                <span className={`font-medium transition-colors duration-300 ${STRENGTH_TEXT_COLORS[strength.score]}`}>
                  {strength.label}
                </span>
              </p>
              {errors.password && <p className="mt-1 text-xs text-red-500">{errors.password}</p>}
            </div>

            <div>
              <label className="block text-sm font-semibold text-gray-700 mb-1.5" htmlFor="confirmPassword">Confirm password</label>
              <div className="relative">
                <div className="absolute inset-y-0 left-0 pl-3 flex items-center pointer-events-none">
                  <svg className="h-4 w-4 text-gray-400" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><rect x="3" y="11" width="18" height="11" rx="2" ry="2"></rect><path d="M7 11V7a5 5 0 0 1 10 0v4"></path></svg>
                </div>
                <input
                  id="confirmPassword"
                  type={showConfirmPassword ? 'text' : 'password'}
                  name="confirmPassword"
                  className={`block w-full pl-9 pr-10 py-2.5 border rounded-xl focus:ring-2 text-sm bg-white shadow-sm transition-shadow hover:border-gray-300 ${
                    (form.confirmPassword && form.confirmPassword !== form.password) || errors.confirmPassword
                      ? 'border-red-300 focus:ring-red-500 focus:border-red-500'
                      : 'border-gray-200 focus:ring-indigo-500 focus:border-indigo-500'
                  }`}
                  placeholder="Re-enter your password"
                  value={form.confirmPassword}
                  onChange={handleChange}
                  required
                />
                <button
                  type="button"
                  className="absolute inset-y-0 right-0 pr-3 flex items-center text-gray-400 hover:text-gray-600 transition-colors"
                  onClick={() => setShowConfirmPassword(!showConfirmPassword)}
                >
                  {showConfirmPassword ? <EyeOff className="h-4 w-4" /> : <Eye className="h-4 w-4" />}
                </button>
              </div>
              {form.confirmPassword ? (
                <p className={`mt-1 text-xs ${form.confirmPassword === form.password ? 'text-green-600' : 'text-red-500'}`}>
                  {form.confirmPassword === form.password ? 'Passwords match' : 'Passwords do not match'}
                </p>
              ) : (
                errors.confirmPassword && <p className="mt-1 text-xs text-red-500">{errors.confirmPassword}</p>
              )}
            </div>

            <div className="flex items-center pt-2">
              <input
                id="termsAccepted"
                name="termsAccepted"
                type="checkbox"
                className="h-4 w-4 text-indigo-600 focus:ring-indigo-500 border-gray-300 rounded cursor-pointer"
                checked={form.termsAccepted}
                onChange={handleChange}
              />
              <label htmlFor="termsAccepted" className="ml-2 block text-xs text-gray-600 cursor-pointer select-none">
                I agree to the <a href="#" className="text-indigo-600 hover:underline">Terms of Service</a> and <a href="#" className="text-indigo-600 hover:underline">Privacy Policy</a>.
              </label>
            </div>
            {errors.termsAccepted && <p className="text-xs text-red-500">{errors.termsAccepted}</p>}

            {error && <p className="text-red-500 text-sm font-medium pt-1">{error}</p>}

            <button
              type="submit"
              className="w-full flex justify-center py-3 px-4 mt-2 border border-transparent rounded-xl shadow-[0_4px_14px_0_rgba(99,102,241,0.39)] text-sm font-semibold text-white bg-indigo-500 hover:bg-indigo-600 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-indigo-500 transition-all hover:shadow-[0_6px_20px_rgba(99,102,241,0.23)] active:scale-[0.98]"
            >
              Create account
            </button>
          </form>

          <div className="mt-6 bg-[#F0FDF4]/50 border border-green-100 rounded-xl p-4">
            <ul className="space-y-2 text-xs text-gray-600">
              <li className="flex items-center gap-2">
                <Check className="w-3.5 h-3.5 text-green-500" />
                <span>Full access for 14 days</span>
              </li>
              <li className="flex items-center gap-2">
                <Check className="w-3.5 h-3.5 text-green-500" />
                <span>Unlimited team invites</span>
              </li>
              <li className="flex items-center gap-2">
                <Check className="w-3.5 h-3.5 text-green-500" />
                <span>Cancel anytime</span>
              </li>
            </ul>
          </div>

          <div className="mt-8 text-center text-sm text-gray-600">
            Already have an account? <Link to={RoutePath.LOGIN} className="font-semibold text-indigo-600 hover:text-indigo-500 transition-colors">Sign in</Link>
          </div>
        </div>
      </div>
    </div>
  );
};

export default Signup;