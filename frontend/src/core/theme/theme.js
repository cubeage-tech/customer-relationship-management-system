export const theme = {
  colors: {
    primary: '#1F7A4D',
    primaryDark: '#155A38',
    secondary: '#2D6CDF',
    background: '#F7F8FA',
    surface: '#FFFFFF',
    text: '#1A1A1A',
    textMuted: '#6B7280',
    danger: '#DC2626',
    warning: '#D97706',
    success: '#16A34A',
  },
  spacing: (multiplier = 1) => `${multiplier * 8}px`,
  radius: {
    sm: '4px',
    md: '8px',
    lg: '16px',
  },
};
