import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import Button from '../../components/common/Button';
import { login as loginRequest } from '../../core/services/auth.service';
import { useAuth } from '../../core/hooks/useAuth';
import { getRoleHomeRoute } from '../../core/constants/routes.constant';
import { NOTIFICATION_MESSAGES } from '../../core/constants/notification.constant';
import { ROLE_LABELS } from '../../core/constants/app.constant';
import { DEV_USERS } from '../../core/mocks/devUsers';

// Same double gate as auth.service.js: import.meta.env.DEV is compiled to
// `false` in production builds, so this panel is stripped out of `vite build`
// output regardless of the env flag's value.
const SHOW_DEV_CREDENTIALS =
  import.meta.env.DEV && import.meta.env.VITE_ENABLE_MOCK_AUTH === 'true';
const DEV_LOGIN_PASSWORD = import.meta.env.VITE_DEV_LOGIN_PASSWORD || 'Dev@12345';

const Login = () => {
  const [form, setForm] = useState({ email: '', password: '' });
  const [error, setError] = useState('');
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
    <section className="auth-form">
      <h1>Log in to your CRM</h1>
      <form onSubmit={handleSubmit}>
        <input
          type="email"
          name="email"
          placeholder="Email"
          value={form.email}
          onChange={handleChange}
          required
        />
        <input
          type="password"
          name="password"
          placeholder="Password"
          value={form.password}
          onChange={handleChange}
          required
        />
        {error && <p className="form-error">{error}</p>}
        <Button type="submit">Log in</Button>
      </form>

      {SHOW_DEV_CREDENTIALS && (
        <div className="dev-credentials" style={{ marginTop: '2rem' }}>
          <p style={{ fontWeight: 600 }}>Development sign-in (not shown in production)</p>
          <p style={{ fontSize: '0.85rem', color: '#6B7280' }}>
            Shared password for every seed account:{' '}
            <code>{DEV_LOGIN_PASSWORD}</code>
          </p>
          <ul style={{ listStyle: 'none', padding: 0, display: 'grid', gap: '0.5rem' }}>
            {DEV_USERS.map((user) => (
              <li
                key={user.id}
                style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: '1rem' }}
              >
                <span>
                  {ROLE_LABELS[user.role]} — <code>{user.email}</code>
                </span>
                <Button type="button" variant="secondary" onClick={() => handleDevLogin(user.email)}>
                  Log in as {ROLE_LABELS[user.role]}
                </Button>
              </li>
            ))}
          </ul>
        </div>
      )}
    </section>
  );
};

export default Login;
