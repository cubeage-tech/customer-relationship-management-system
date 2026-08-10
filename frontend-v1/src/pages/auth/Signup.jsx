import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import Button from '../../components/common/Button';
import { signup as signupRequest } from '../../core/services/auth.service';
import RoutePath from '../../core/constants/routes.constant';
import {
  ROLE_LABELS,
  SIGNUP_ROLES,
  USER_ROLES,
} from '../../core/constants/app.constant';
import { NOTIFICATION_MESSAGES } from '../../core/constants/notification.constant';

const INITIAL_FORM = {
  role: USER_ROLES.SALES_EXECUTIVE,
  name: '',
  email: '',
  password: '',
  organizationName: '',
  website: '',
  address: '',
  department: '',
  phone: '',
};

const Signup = () => {
  const [form, setForm] = useState(INITIAL_FORM);
  const [error, setError] = useState('');
  const navigate = useNavigate();

  // A tenant administrator registers the organisation itself, everyone else
  // joins an existing tenant as a team member.
  const isTenantAdmin = form.role === USER_ROLES.SUPER_ADMIN;

  const handleChange = (e) => setForm((prev) => ({ ...prev, [e.target.name]: e.target.value }));

  const handleSubmit = async (e) => {
    e.preventDefault();
    setError('');
    try {
      const payload = isTenantAdmin
        ? { role: form.role, name: form.name, email: form.email, password: form.password, organizationName: form.organizationName, website: form.website, address: form.address }
        : { role: form.role, name: form.name, email: form.email, password: form.password, organizationName: form.organizationName, department: form.department, phone: form.phone };

      await signupRequest(payload);
      navigate(RoutePath.LOGIN);
    } catch {
      setError(NOTIFICATION_MESSAGES.GENERIC_ERROR);
    }
  };

  return (
    <section className="auth-form">
      <h1>Create your CRM account</h1>
      <form onSubmit={handleSubmit}>
        <label>
          I am registering as
          {/* option value is the internal role name, the label is what the user reads */}
          <select name="role" value={form.role} onChange={handleChange}>
            {SIGNUP_ROLES.map((role) => (
              <option key={role} value={role}>
                {ROLE_LABELS[role]}
              </option>
            ))}
          </select>
        </label>

        <input
          type="text"
          name="name"
          placeholder="Full name"
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

        {isTenantAdmin ? (
          <>
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
          </>
        ) : (
          <>
            <input
              type="text"
              name="organizationName"
              placeholder="Organization name (optional)"
              value={form.organizationName}
              onChange={handleChange}
            />
            <input
              type="text"
              name="department"
              placeholder="Department / team (optional)"
              value={form.department}
              onChange={handleChange}
            />
            <input
              type="text"
              name="phone"
              placeholder="Phone (optional)"
              value={form.phone}
              onChange={handleChange}
            />
          </>
        )}

        {error && <p className="form-error">{error}</p>}
        <Button type="submit">Sign up</Button>
      </form>
    </section>
  );
};

export default Signup;
