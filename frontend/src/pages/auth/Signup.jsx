import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import Button from '../../components/common/Button';
import { signup as signupRequest } from '../../core/services/auth.service';
import RoutePath from '../../core/constants/routes.constant';
import { USER_ROLES } from '../../core/constants/app.constant';

const INITIAL_FORM = {
  role: USER_ROLES.INDUSTRY,
  name: '',
  email: '',
  password: '',
  companyName: '',
  gstin: '',
  address: '',
  phone: '',
};

const Signup = () => {
  const [form, setForm] = useState(INITIAL_FORM);
  const [error, setError] = useState('');
  const navigate = useNavigate();

  const isIndustry = form.role === USER_ROLES.INDUSTRY;

  const handleChange = (e) => setForm((prev) => ({ ...prev, [e.target.name]: e.target.value }));

  const handleSubmit = async (e) => {
    e.preventDefault();
    setError('');
    try {
      const payload = isIndustry
        ? { role: form.role, name: form.name, email: form.email, password: form.password, companyName: form.companyName, gstin: form.gstin, address: form.address }
        : { role: form.role, name: form.name, email: form.email, password: form.password, companyName: form.companyName, phone: form.phone };

      await signupRequest(payload);
      navigate(RoutePath.LOGIN);
    } catch {
      setError('Could not create account, please try again');
    }
  };

  return (
    <section className="auth-form">
      <h1>Sign up</h1>
      <form onSubmit={handleSubmit}>
        <label>
          I am registering as
          <select name="role" value={form.role} onChange={handleChange}>
            <option value={USER_ROLES.INDUSTRY}>Industry (sell scrap)</option>
            <option value={USER_ROLES.BUYER}>Buyer (buy scrap)</option>
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

        {isIndustry ? (
          <>
            <input
              type="text"
              name="companyName"
              placeholder="Company name"
              value={form.companyName}
              onChange={handleChange}
              required
            />
            <input
              type="text"
              name="gstin"
              placeholder="GSTIN (optional)"
              value={form.gstin}
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
              name="companyName"
              placeholder="Company name (optional)"
              value={form.companyName}
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
