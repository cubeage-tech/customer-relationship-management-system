import { useEffect } from 'react';

const ERROR_ATTR = 'data-inline-error';
const FIELD_SELECTOR = 'input, select, textarea';

const isField = (el) => el instanceof HTMLElement && el.matches?.(FIELD_SELECTOR) && el.type !== 'hidden';

/** The message element right after a field, if we added one. */
const messageFor = (field) => {
  const next = field.nextElementSibling;
  return next && next.hasAttribute(ERROR_ATTR) ? next : null;
};

const showError = (field, message) => {
  field.setAttribute('aria-invalid', 'true');
  field.classList.add('border-destructive');
  let el = messageFor(field);
  if (!el) {
    el = document.createElement('p');
    el.setAttribute(ERROR_ATTR, '');
    el.setAttribute('role', 'alert');
    el.className = 'mt-1 text-xs text-destructive';
    field.insertAdjacentElement('afterend', el);
  }
  el.textContent = message;
  if (!field.id) field.id = `field-${Math.random().toString(36).slice(2, 9)}`;
  el.id = `${field.id}-error`;
  field.setAttribute('aria-describedby', el.id);
};

const clearError = (field) => {
  if (field.getAttribute('aria-invalid') !== 'true') return;
  field.removeAttribute('aria-invalid');
  field.classList.remove('border-destructive');
  field.removeAttribute('aria-describedby');
  messageFor(field)?.remove();
};

/** The field's own message (data-error), else the browser's explanation of the broken rule. */
const messageOf = (field) => field.dataset.error || field.validationMessage;

/**
 * UI-6: inline validation feedback on every form, mounted once for the whole app. Fields keep
 * their rules in the markup (required, type, min/max, maxLength, pattern):
 * <ul>
 *   <li>on submit, each broken rule is shown under its field (instead of the browser's bubble) and
 *       the first such field is focused;</li>
 *   <li>after leaving a filled-in field, a broken rule is shown straight away;</li>
 *   <li>a message clears as soon as the field is valid;</li>
 *   <li>field errors returned by the server (VALIDATION_FAILED) appear under the matching
 *       {@code name}d field of the form that was just submitted.</li>
 * </ul>
 */
const InlineValidation = () => {
  useEffect(() => {
    let focusedThisRound = false;
    let lastForm = null;

    const onInvalid = (e) => {
      const field = e.target;
      if (!isField(field)) return;
      e.preventDefault(); // no browser bubble — the message is shown inline instead
      showError(field, messageOf(field));
      if (!focusedThisRound) {
        focusedThisRound = true;
        field.focus();
        queueMicrotask(() => { focusedThisRound = false; });
      }
    };

    const onInput = (e) => {
      const field = e.target;
      if (!isField(field) || field.getAttribute('aria-invalid') !== 'true') return;
      if (field.dataset.serverError) {
        delete field.dataset.serverError;
        clearError(field);
        return;
      }
      if (field.checkValidity()) clearError(field);
      else showError(field, messageOf(field));
    };

    const onLeave = (e) => {
      const field = e.target;
      if (!isField(field) || field.value === '' || field.dataset.serverError) return;
      if (!field.checkValidity()) showError(field, messageOf(field));
    };

    const onSubmit = (e) => {
      if (e.target instanceof HTMLFormElement) lastForm = e.target;
    };

    const onServerErrors = (e) => {
      const form = lastForm && document.contains(lastForm) ? lastForm : null;
      if (!form) return;
      let first = null;
      Object.entries(e.detail || {}).forEach(([name, message]) => {
        const field = form.querySelector(`[name="${CSS.escape(name)}"]`);
        if (isField(field)) {
          field.dataset.serverError = 'true';
          showError(field, message);
          first = first || field;
        }
      });
      first?.focus();
    };

    document.addEventListener('invalid', onInvalid, true);
    document.addEventListener('input', onInput, true);
    document.addEventListener('change', onInput, true);
    document.addEventListener('focusout', onLeave, true);
    document.addEventListener('submit', onSubmit, true);
    window.addEventListener('api:field-errors', onServerErrors);
    return () => {
      document.removeEventListener('invalid', onInvalid, true);
      document.removeEventListener('input', onInput, true);
      document.removeEventListener('change', onInput, true);
      document.removeEventListener('focusout', onLeave, true);
      document.removeEventListener('submit', onSubmit, true);
      window.removeEventListener('api:field-errors', onServerErrors);
    };
  }, []);

  return null;
};

export default InlineValidation;
