import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import Button from '../../components/common/Button';
import { signup as signupRequest } from '../../core/services/auth.service';
import RoutePath from '../../core/constants/routes.constant';
import { USER_ROLES } from '../../core/constants/app.constant';
import { NOTIFICATION_MESSAGES } from '../../core/constants/notification.constant';

const INITIAL_FORM = {
  name: '',
  email: '',
  password: '',
  organizationName: '',
  website: '',
  address: '',
};

// Public signup only ever creates a new tenant + its admin (owner) account. Every other
// CRM role (sales_manager, sales_executive, ...) is added by that admin from the Users
// page instead of self-registering — see app.constant.js's SIGNUP_ROLES/TEAM_ROLES split.
const Signup = () => {
  const [form, setForm] = useState(INITIAL_FORM);
  const [error, setError] = useState('');
  const navigate = useNavigate();

  const handleChange = (e) => setForm((prev) => ({ ...prev, [e.target.name]: e.target.value }));

  const handleSubmit = async (e) => {
    e.preventDefault();
    setError('');
    try {
      await signupRequest({ role: USER_ROLES.ADMIN, ...form });
      navigate(RoutePath.LOGIN);
    } catch {
      setError(NOTIFICATION_MESSAGES.GENERIC_ERROR);
    }
  };

  return (
    <section className="auth-form">
      <h1>Register your company</h1>
      <form onSubmit={handleSubmit}>
        <input
          type="text"
          name="name"
          placeholder="Your full name"
          value={form.name}
          onChange={handleChange}
          required
        />
        <input
          type="email"
          name="email"
          placeholder="Work email"
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
        <input
          type="text"
          name="organizationName"
          placeholder="Organization name"
          value={form.organizationName}
          onChange={handleChange}
          required
        />
        <input
          type="text"
          name="website"
          placeholder="Website (optional)"
          value={form.website}
          onChange={handleChange}
        />
        <input
          type="text"
          name="address"
          placeholder="Address (optional)"
          value={form.address}
          onChange={handleChange}
        />

        {error && <p className="form-error">{error}</p>}
        <Button type="submit">Sign up</Button>
      </form>
    </section>
  );
};

export default Signup;
