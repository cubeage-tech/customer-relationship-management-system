import { useState } from 'react';
import { useNavigate, Link } from 'react-router-dom';
import Button from '../../components/common/Button';
import { login as loginRequest } from '../../core/services/auth.service';
import { useAuth } from '../../core/hooks/useAuth';
import { getRoleHomeRoute } from '../../core/constants/routes.constant';
import { NOTIFICATION_MESSAGES } from '../../core/constants/notification.constant';
import { ROLE_LABELS } from '../../core/constants/app.constant';
import { DEV_USERS } from '../../core/mocks/devUsers';
import { Eye, EyeOff, Bot, ShieldCheck, BarChart3, Sparkles, CheckCircle2 } from 'lucide-react';

// Same double gate as auth.service.js: import.meta.env.DEV is compiled to
// `false` in production builds, so this panel is stripped out of `vite build`
// output regardless of the env flag's value.
const SHOW_DEV_CREDENTIALS =
  import.meta.env.DEV && import.meta.env.VITE_ENABLE_MOCK_AUTH === 'true';
const DEV_LOGIN_PASSWORD = import.meta.env.VITE_DEV_LOGIN_PASSWORD || 'Dev@12345';

const Login = () => {
  const [form, setForm] = useState({ email: '', password: '' });
  const [error, setError] = useState('');
  const [showPassword, setShowPassword] = useState(false);
  const { loginUser } = useAuth();
  const navigate = useNavigate();

  const handleChange = (e) => setForm((prev) => ({ ...prev, [e.target.name]: e.target.value }));

  const submitCredentials = async (credentials) => {
    setError('');
    try {
      const { user, token } = await loginRequest(credentials);
      loginUser({ user, token });
      // Every CRM role lands on its own dashboard.
      navigate(getRoleHomeRoute(user.role));
    } catch {
      setError(NOTIFICATION_MESSAGES.LOGIN_FAILED);
    }
  };

  const handleSubmit = (e) => {
    e.preventDefault();
    submitCredentials(form);
  };

  const handleDevLogin = (email) => submitCredentials({ email, password: DEV_LOGIN_PASSWORD });

  return (
    <div className="flex min-h-screen bg-white relative">
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

      {/* Right side - Login Form */}
      <div className="w-full lg:w-1/2 flex items-center justify-center p-8 sm:p-12 xl:p-20 relative bg-[#FAFAFA]">
        <div className="w-full max-w-[420px]">
          <div className="mb-8">
            <p className="text-indigo-600 font-bold text-xs tracking-[0.15em] uppercase mb-3">Welcome back</p>
            <h2 className="text-3xl font-bold text-gray-900 mb-2">Sign in to SmartCRM AI</h2>
            <p className="text-gray-500 text-sm">Use your work account. Your role decides what you see next.</p>
          </div>

          <form onSubmit={handleSubmit} className="space-y-5">
            <div>
              <label className="block text-sm font-semibold text-gray-700 mb-1.5" htmlFor="email">Work email</label>
              <div className="relative">
                <div className="absolute inset-y-0 left-0 pl-3.5 flex items-center pointer-events-none">
                  <svg className="h-5 w-5 text-gray-400" viewBox="0 0 20 20" fill="currentColor">
                    <path d="M2.003 5.884L10 9.882l7.997-3.998A2 2 0 0016 4H4a2 2 0 00-1.997 1.884z" />
                    <path d="M18 8.118l-8 4-8-4V14a2 2 0 002 2h12a2 2 0 002-2V8.118z" />
                  </svg>
                </div>
                <input
                  id="email"
                  type="email"
                  name="email"
                  className="block w-full pl-10 pr-3 py-3 border border-gray-200 rounded-xl focus:ring-2 focus:ring-indigo-500 focus:border-indigo-500 text-sm bg-white shadow-sm transition-shadow hover:border-gray-300"
                  placeholder="john.doe@company.com"
                  value={form.email}
                  onChange={handleChange}
                  required
                />
              </div>
            </div>

            <div>
              <label className="block text-sm font-semibold text-gray-700 mb-1.5" htmlFor="password">Password</label>
              <div className="relative">
                <div className="absolute inset-y-0 left-0 pl-3.5 flex items-center pointer-events-none">
                  <svg className="h-5 w-5 text-gray-400" viewBox="0 0 20 20" fill="currentColor">
                    <path fillRule="evenodd" d="M5 9V7a5 5 0 0110 0v2a2 2 0 012 2v5a2 2 0 01-2 2H5a2 2 0 01-2-2v-5a2 2 0 012-2zm8-2v2H7V7a3 3 0 016 0z" clipRule="evenodd" />
                  </svg>
                </div>
                <input
                  id="password"
                  type={showPassword ? 'text' : 'password'}
                  name="password"
                  className="block w-full pl-10 pr-10 py-3 border border-gray-200 rounded-xl focus:ring-2 focus:ring-indigo-500 focus:border-indigo-500 text-sm bg-white shadow-sm transition-shadow hover:border-gray-300"
                  placeholder="••••••••"
                  value={form.password}
                  onChange={handleChange}
                  required
                />
                <button
                  type="button"
                  className="absolute inset-y-0 right-0 pr-3.5 flex items-center text-gray-400 hover:text-gray-600 transition-colors"
                  onClick={() => setShowPassword(!showPassword)}
                >
                  {showPassword ? <EyeOff className="h-5 w-5" /> : <Eye className="h-5 w-5" />}
                </button>
              </div>
            </div>

            <div className="flex items-center justify-between py-1">
              <div className="flex items-center">
                <input
                  id="remember-me"
                  name="remember-me"
                  type="checkbox"
                  className="h-4 w-4 text-indigo-600 focus:ring-indigo-500 border-gray-300 rounded cursor-pointer"
                />
                <label htmlFor="remember-me" className="ml-2 block text-sm text-gray-600 cursor-pointer select-none">
                  Keep me signed in
                </label>
              </div>

              <div className="text-sm">
                <a href="#" className="font-semibold text-indigo-600 hover:text-indigo-500 transition-colors">
                  Forgot password?
                </a>
              </div>
            </div>

            {error && <p className="text-red-500 text-sm font-medium">{error}</p>}

            <button
              type="submit"
              className="w-full flex justify-center py-3.5 px-4 border border-transparent rounded-xl shadow-[0_4px_14px_0_rgba(99,102,241,0.39)] text-sm font-semibold text-white bg-indigo-500 hover:bg-indigo-600 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-indigo-500 transition-all hover:shadow-[0_6px_20px_rgba(99,102,241,0.23)] active:scale-[0.98]"
            >
              Sign in
            </button>
          </form>

          <div className="mt-8">
            <div className="relative">
              <div className="absolute inset-0 flex items-center">
                <div className="w-full border-t border-gray-200" />
              </div>
              <div className="relative flex justify-center text-[10px] uppercase font-bold tracking-widest text-gray-400">
                <span className="bg-[#FAFAFA] px-4">Or continue with</span>
              </div>
            </div>

            <div className="mt-6 grid grid-cols-2 gap-4">
              <button className="flex justify-center items-center py-3 px-4 border border-gray-200 rounded-xl shadow-sm bg-white text-sm font-semibold text-gray-700 hover:bg-gray-50 transition-colors hover:border-gray-300">
                <span className="mr-2">Google Workspace</span>
              </button>
              <button className="flex justify-center items-center py-3 px-4 border border-gray-200 rounded-xl shadow-sm bg-white text-sm font-semibold text-gray-700 hover:bg-gray-50 transition-colors hover:border-gray-300">
                <span className="mr-2">Microsoft SSO</span>
              </button>
            </div>
          </div>
          
          <div className="mt-8 bg-[#F0FDF4] rounded-xl p-3 flex items-center justify-center gap-2 border border-green-100">
            <CheckCircle2 className="w-4 h-4 text-green-600" />
            <p className="text-[13px] text-gray-600 font-medium">Protected by SSO, MFA and role-based access control.</p>
          </div>
          
          <div className="mt-8 text-center text-sm text-gray-600">
            New to SmartCRM AI? <a href="signup" className="font-semibold text-indigo-600 hover:text-indigo-500 transition-colors">Create an account</a>
          </div>

          {SHOW_DEV_CREDENTIALS && (
            <div className="mt-12 pt-8 border-t border-gray-200">
              <p className="font-semibold text-sm text-gray-900 mb-2">Development sign-in (not shown in production)</p>
              <p className="text-xs text-gray-500 mb-4">
                Shared password for every seed account:{' '}
                <code className="bg-gray-100 px-1 py-0.5 rounded">{DEV_LOGIN_PASSWORD}</code>
              </p>
              <ul className="space-y-2">
                {DEV_USERS.map((user) => (
                  <li
                    key={user.id}
                    className="flex flex-col sm:flex-row sm:items-center justify-between gap-2 p-3 bg-white rounded-lg text-sm border border-gray-200 shadow-sm"
                  >
                    <span className="text-gray-700">
                      <span className="font-medium">{ROLE_LABELS[user.role]}</span> — <code className="text-gray-500">{user.email}</code>
                    </span>
                    <Button type="button" variant="secondary" onClick={() => handleDevLogin(user.email)} className="text-xs py-1.5 px-3">
                      Log in
                    </Button>
                  </li> 
                ))}
              </ul>
            </div>
          )}
        </div>
      </div>
    </div>
  );
};

export default Login;
