import { useState } from 'react';
import { useNavigate } from 'react-router-dom';

// All colors come from the CSS custom properties defined in src/index.css
// (--primary, --secondary, --destructive, --success, --warning, --border, ...),
// exposed as Tailwind utilities via the @theme inline block. Never hardcode a
// hex/oklch value here — add a new token in index.css instead.
const VARIANT_CLASSES = {
  primary: 'bg-primary text-primary-foreground hover:bg-primary/90',
  secondary: 'bg-secondary text-secondary-foreground hover:bg-secondary/80',
  destructive: 'bg-destructive text-destructive-foreground hover:bg-destructive/90',
  success: 'bg-success text-success-foreground hover:bg-success/90',
  warning: 'bg-warning text-warning-foreground hover:bg-warning/90',
  outline: 'border border-border bg-transparent text-foreground hover:bg-accent hover:text-accent-foreground',
  ghost: 'bg-transparent text-foreground hover:bg-accent hover:text-accent-foreground',
  link: 'bg-transparent text-primary underline-offset-4 hover:underline h-auto p-0',
};

const SIZE_CLASSES = {
  sm: 'h-8 px-3 text-sm gap-1.5',
  md: 'h-10 px-4 text-sm gap-2',
  lg: 'h-12 px-6 text-base gap-2.5',
  icon: 'h-10 w-10 p-0',
};

const cn = (...classes) => classes.filter(Boolean).join(' ');

/**
 * Reusable button. Resize with `className` (e.g. "w-48 h-12") or `style`,
 * swap `variant`/`size` for the built-in looks, and drive behavior with
 * either `to`/`href` (redirect), `api` (async call), `onClick`, or all three —
 * `onClick` runs first, then `api`, then the redirect once `api` resolves.
 */
const Button = ({
  children,
  label,
  variant = 'primary',
  size = 'md',
  fullWidth = false,
  className = '',
  style,
  type = 'button',
  icon: Icon,
  iconPosition = 'left',
  to,
  href,
  target,
  api,
  onSuccess,
  onError,
  onClick,
  loading: loadingProp = false,
  disabled = false,
  ...rest
}) => {
  const [loading, setLoading] = useState(false);
  const navigate = useNavigate();
  const isLoading = loadingProp || loading;

  const redirect = () => {
    if (to) navigate(to);
    else if (href) window.open(href, target || '_self');
  };

  const handleClick = async (event) => {
    onClick?.(event);
    if (event.defaultPrevented) return;

    if (api) {
      setLoading(true);
      try {
        const result = await api(event);
        onSuccess?.(result);
        redirect();
      } catch (error) {
        onError?.(error);
      } finally {
        setLoading(false);
      }
      return;
    }

    redirect();
  };

  return (
    <button
      type={type}
      disabled={disabled || isLoading}
      onClick={handleClick}
      style={style}
      className={cn(
        'inline-flex items-center justify-center rounded-md font-medium transition-colors',
        'focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2',
        'disabled:pointer-events-none disabled:opacity-50',
        VARIANT_CLASSES[variant] || VARIANT_CLASSES.primary,
        SIZE_CLASSES[size] || SIZE_CLASSES.md,
        fullWidth && 'w-full',
        className
      )}
      {...rest}
    >
      {isLoading ? (
        <span
          className="h-4 w-4 animate-spin rounded-full border-2 border-current border-t-transparent"
          aria-hidden="true"
        />
      ) : (
        Icon && iconPosition === 'left' && <Icon size={16} aria-hidden="true" />
      )}
      {!isLoading && (children ?? label)}
      {!isLoading && Icon && iconPosition === 'right' && <Icon size={16} aria-hidden="true" />}
    </button>
  );
};

export default Button;
