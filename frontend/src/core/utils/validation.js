import { VALIDATION_PATTERNS, VALIDATION_MESSAGES } from '../constants/validation.constant';

export const isValidEmail = (value) => VALIDATION_PATTERNS.EMAIL.test(value ?? '');

export const isValidPhone = (value) => VALIDATION_PATTERNS.PHONE.test(value ?? '');

export const isValidPassword = (value) =>
  typeof value === 'string' && value.length >= VALIDATION_PATTERNS.PASSWORD_MIN_LENGTH;

export const getRequiredError = (value) => (value ? '' : VALIDATION_MESSAGES.REQUIRED);
