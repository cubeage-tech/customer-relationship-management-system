import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import Button from '../../components/common/Button';
import { login as loginRequest } from '../../core/services/auth.service';
import { useAuth } from '../../core/hooks/useAuth';
import { ROLE_HOME_ROUTE } from '../../core/constants/routes.constant';

const Login = () => {
  const [form, setForm] = useState({ email: '', password: '' });
  const [error, setError] = useState('');
  const { loginUser } = useAuth();
  const navigate = useNavigate();

  const handleChange = (e) => setForm((prev) => ({ ...prev, [e.target.name]: e.target.value }));

  const handleSubmit = async (e) => {
    e.preventDefault();
    setError('');
    try {
      const { user, token } = await loginRequest(form);
      loginUser({ user, token });
      navigate(ROLE_HOME_ROUTE[user.role]);
    } catch {
      setError('Invalid email or password');
    }
  };

  return (
    <section className="auth-form">
      <h1>Log in</h1>
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
    </section>
  );
};

export default Login;
