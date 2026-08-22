/**
 * validation.js
 * Centralized validation utilities for SmartCRM AI frontend forms.
 * Each validator returns an error message string on failure, or '' (empty string) on success.
 * Compose these in form-level validate() functions rather than duplicating regex/logic per page.
 */

// ---------------------------------------------------------------------------
// Regex patterns
// ---------------------------------------------------------------------------

const PATTERNS = {
  EMAIL: /^[^\s@]+@[^\s@]+\.[^\s@]+$/,
  NAME: /^[A-Za-z][A-Za-z\s'-]{1,49}$/,
  // India-style bank account number: 9 to 18 digits (covers most Indian banks)
  BANK_ACCOUNT_NUMBER: /^\d{9,18}$/,
  IFSC_CODE: /^[A-Z]{4}0[A-Z0-9]{6}$/,
  PHONE_INDIA: /^[6-9]\d{9}$/,
  PAN: /^[A-Z]{5}\d{4}[A-Z]$/,
  GSTIN: /^\d{2}[A-Z]{5}\d{4}[A-Z]\d[A-Z]\d[A-Z]$/,
  ORG_NAME: /^[A-Za-z0-9][A-Za-z0-9\s.,'&-]{1,99}$/,
  URL: /^(https?:\/\/)?([\w-]+\.)+[\w-]{2,}(\/\S*)?$/,
  ALPHANUMERIC: /^[A-Za-z0-9]+$/,
};

// Known disposable/temp email domains worth blocking at signup.
// Extend this list server-side too — client-side is a UX nicety, not a security boundary.
const DISPOSABLE_EMAIL_DOMAINS = [
  'mailinator.com',
  'guerrillamail.com',
  'tempmail.com',
  'temp-mail.org',
  '10minutemail.com',
  'throwawaymail.com',
  'yopmail.com',
  'fakeinbox.com',
  'trashmail.com',
];

// ---------------------------------------------------------------------------
// Generic helpers
// ---------------------------------------------------------------------------

export const isEmpty = (value) => value === undefined || value === null || String(value).trim() === '';

export const required = (value, fieldLabel = 'This field') => {
  if (isEmpty(value)) return `${fieldLabel} is required.`;
  return '';
};

export const minLength = (value, min, fieldLabel = 'This field') => {
  if (String(value ?? '').length < min) return `${fieldLabel} must be at least ${min} characters.`;
  return '';
};

export const maxLength = (value, max, fieldLabel = 'This field') => {
  if (String(value ?? '').length > max) return `${fieldLabel} must be at most ${max} characters.`;
  return '';
};

// ---------------------------------------------------------------------------
// Name fields (first name / last name)
// ---------------------------------------------------------------------------

export const validateName = (value, fieldLabel = 'Name') => {
  const err = required(value, fieldLabel);
  if (err) return err;
  if (!PATTERNS.NAME.test(value.trim())) {
    return `${fieldLabel} must be 2-50 letters and may include spaces, hyphens, or apostrophes.`;
  }
  return '';
};

// ---------------------------------------------------------------------------
// Email
// ---------------------------------------------------------------------------

export const validateEmail = (value) => {
  const err = required(value, 'Email');
  if (err) return err;

  const trimmed = value.trim();
  if (!PATTERNS.EMAIL.test(trimmed)) {
    return 'Enter a valid email address.';
  }

  const domain = trimmed.split('@')[1]?.toLowerCase();
  if (domain && DISPOSABLE_EMAIL_DOMAINS.includes(domain)) {
    return 'Disposable email addresses are not allowed. Please use a permanent email.';
  }

  return '';
};

/**
 * Normalizes an email for uniqueness comparison — strips Gmail-style +aliases
 * and dots so john+1@gmail.com and j.ohn@gmail.com resolve to the same identity.
 * Use this before sending to the backend for duplicate-account checks,
 * but always mirror this logic server-side too (client checks are bypassable).
 */
export const normalizeEmail = (value) => {
  if (isEmpty(value)) return '';
  let [local, domain] = value.trim().toLowerCase().split('@');
  if (!domain) return value.trim().toLowerCase();

  local = local.split('+')[0]; // strip +alias
  if (domain === 'gmail.com' || domain === 'googlemail.com') {
    local = local.replace(/\./g, ''); // gmail ignores dots
    domain = 'gmail.com';
  }

  return `${local}@${domain}`;
};

// ---------------------------------------------------------------------------
// Organization / company name
// ---------------------------------------------------------------------------

export const validateOrganizationName = (value) => {
  const err = required(value, 'Company name');
  if (err) return err;
  if (!PATTERNS.ORG_NAME.test(value.trim())) {
    return 'Company name contains invalid characters.';
  }
  return '';
};

// ---------------------------------------------------------------------------
// Bank account number
// ---------------------------------------------------------------------------

export const validateBankAccountNumber = (value) => {
  const err = required(value, 'Bank account number');
  if (err) return err;
  if (!PATTERNS.BANK_ACCOUNT_NUMBER.test(value.trim())) {
    return 'Enter a valid bank account number (9-18 digits, numbers only).';
  }
  return '';
};

export const validateIfscCode = (value) => {
  const err = required(value, 'IFSC code');
  if (err) return err;
  if (!PATTERNS.IFSC_CODE.test(value.trim().toUpperCase())) {
    return 'Enter a valid IFSC code (e.g. HDFC0001234).';
  }
  return '';
};

// ---------------------------------------------------------------------------
// Phone (India)
// ---------------------------------------------------------------------------

export const validatePhoneIndia = (value) => {
  const err = required(value, 'Phone number');
  if (err) return err;
  if (!PATTERNS.PHONE_INDIA.test(value.trim())) {
    return 'Enter a valid 10-digit Indian mobile number.';
  }
  return '';
};

// ---------------------------------------------------------------------------
// PAN / GSTIN (India business identifiers)
// ---------------------------------------------------------------------------

export const validatePan = (value) => {
  const err = required(value, 'PAN');
  if (err) return err;
  if (!PATTERNS.PAN.test(value.trim().toUpperCase())) {
    return 'Enter a valid PAN (e.g. ABCDE1234F).';
  }
  return '';
};

export const validateGstin = (value) => {
  const err = required(value, 'GSTIN');
  if (err) return err;
  if (!PATTERNS.GSTIN.test(value.trim().toUpperCase())) {
    return 'Enter a valid GSTIN.';
  }
  return '';
};

// ---------------------------------------------------------------------------
// Password
// ---------------------------------------------------------------------------

/**
 * Returns { score: 0-4, label: string } for use in strength meters.
 * score 0 = "Too short", 4 = "Strong"
 */
export const getPasswordStrength = (password) => {
  if (isEmpty(password)) {
    return { score: 0, label: 'Too short' };
  }

  let score = 0;
  if (password.length >= 8) score += 1;
  if (password.length >= 12) score += 1;
  if (/[A-Z]/.test(password) && /[a-z]/.test(password)) score += 1;
  if (/\d/.test(password)) score += 1;
  if (/[^A-Za-z0-9]/.test(password)) score += 1;

  score = Math.min(score, 4);

  const labels = ['Too short', 'Weak', 'Fair', 'Good', 'Strong'];
  return { score, label: password.length < 8 ? 'Too short' : labels[score] };
};

export const validatePassword = (value, { minScore = 2 } = {}) => {
  const err = required(value, 'Password');
  if (err) return err;

  const lengthErr = minLength(value, 8, 'Password');
  if (lengthErr) return lengthErr;

  const { score, label } = getPasswordStrength(value);
  if (score < minScore) {
    return `Password is too weak (${label}). Use a longer password with a mix of letters, numbers, and symbols.`;
  }

  return '';
};

export const validateConfirmPassword = (password, confirmPassword) => {
  const err = required(confirmPassword, 'Confirm password');
  if (err) return err;
  if (password !== confirmPassword) {
    return 'Passwords do not match.';
  }
  return '';
};

// ---------------------------------------------------------------------------
// Checkbox / terms acceptance
// ---------------------------------------------------------------------------

export const validateTermsAccepted = (value) => {
  if (!value) return 'You must accept the Terms of Service to continue.';
  return '';
};

// ---------------------------------------------------------------------------
// URL
// ---------------------------------------------------------------------------

export const validateUrl = (value, fieldLabel = 'URL') => {
  const err = required(value, fieldLabel);
  if (err) return err;
  if (!PATTERNS.URL.test(value.trim())) {
    return `Enter a valid ${fieldLabel.toLowerCase()}.`;
  }
  return '';
};

// ---------------------------------------------------------------------------
// Composite: validate an entire Signup form object in one call.
// Returns an object keyed by field name; empty object means no errors.
// ---------------------------------------------------------------------------

export const validateSignupForm = (form) => {
  const errors = {};

  const firstNameErr = validateName(form.firstName, 'First name');
  if (firstNameErr) errors.firstName = firstNameErr;

  const lastNameErr = validateName(form.lastName, 'Last name');
  if (lastNameErr) errors.lastName = lastNameErr;

  const emailErr = validateEmail(form.email);
  if (emailErr) errors.email = emailErr;

  const orgErr = validateOrganizationName(form.organizationName);
  if (orgErr) errors.organizationName = orgErr;

  const bankErr = validateBankAccountNumber(form.bankAccountNumber);
  if (bankErr) errors.bankAccountNumber = bankErr;

  const passwordErr = validatePassword(form.password);
  if (passwordErr) errors.password = passwordErr;

  const confirmErr = validateConfirmPassword(form.password, form.confirmPassword);
  if (confirmErr) errors.confirmPassword = confirmErr;

  const termsErr = validateTermsAccepted(form.termsAccepted);
  if (termsErr) errors.termsAccepted = termsErr;

  return errors;
};

// ---------------------------------------------------------------------------
// Exports
// ---------------------------------------------------------------------------

export default {
  PATTERNS,
  DISPOSABLE_EMAIL_DOMAINS,
  isEmpty,
  required,
  minLength,
  maxLength,
  validateName,
  validateEmail,
  normalizeEmail,
  validateOrganizationName,
  validateBankAccountNumber,
  validateIfscCode,
  validatePhoneIndia,
  validatePan,
  validateGstin,
  getPasswordStrength,
  validatePassword,
  validateConfirmPassword,
  validateTermsAccepted,
  validateUrl,
  validateSignupForm,
};