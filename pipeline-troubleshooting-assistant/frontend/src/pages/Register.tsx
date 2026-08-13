import React, { useState } from 'react';
import { register as apiRegister, ApiError } from '../api';

const cardContainerStyle: React.CSSProperties = {
  minHeight: '100vh',
  display: 'flex',
  alignItems: 'center',
  justifyContent: 'center',
  backgroundColor: '#f8fafc',
  padding: '24px',
};

const cardStyle: React.CSSProperties = {
  backgroundColor: '#ffffff',
  padding: '40px',
  borderRadius: '8px',
  boxShadow: '0 1px 3px rgba(0,0,0,0.1)',
  width: '100%',
  maxWidth: '420px',
  border: '1px solid #e2e8f0',
};

const labelStyle: React.CSSProperties = {
  display: 'block',
  marginBottom: '6px',
  fontWeight: 500,
  color: '#111827',
  fontSize: '0.9em',
};

const inputStyle: React.CSSProperties = {
  width: '100%',
  padding: '10px 12px',
  border: '1px solid #64748b',
  borderRadius: '4px',
  fontSize: '1em',
  color: '#111827',
  backgroundColor: '#ffffff',
  boxSizing: 'border-box',
};

const fieldStyle: React.CSSProperties = { marginBottom: '16px' };

const errorTextStyle: React.CSSProperties = {
  color: '#b91c1c',
  margin: '4px 0 0',
  fontSize: '0.85em',
};

const primaryBtnStyle: React.CSSProperties = {
  width: '100%',
  padding: '10px',
  backgroundColor: '#101a2d',
  color: '#ffffff',
  border: 'none',
  borderRadius: '4px',
  fontSize: '1em',
  cursor: 'pointer',
  fontWeight: 500,
};

export default function RegisterPage() {
  const [email, setEmail] = useState('');
  const [displayName, setDisplayName] = useState('');
  const [password, setPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [success, setSuccess] = useState<string | null>(null);
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});

  const validate = (): boolean => {
    const errors: Record<string, string> = {};
    if (!email.trim()) errors.email = 'Email is required';
    if (!displayName.trim()) errors.displayName = 'Display name is required';
    if (!password) errors.password = 'Password is required';
    if (!confirmPassword) {
      errors.confirmPassword = 'Please confirm your password';
    } else if (password && password !== confirmPassword) {
      errors.confirmPassword = 'Passwords do not match';
    }
    setFieldErrors(errors);
    return Object.keys(errors).length === 0;
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!validate()) return;
    setLoading(true);
    setError(null);
    try {
      const response = await apiRegister({
        email: email.trim(),
        password,
        displayName: displayName.trim(),
      });
      setSuccess(response.message);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Registration failed. Please try again.');
    } finally {
      setLoading(false);
    }
  };

  if (success) {
    return (
      <div style={cardContainerStyle}>
        <div style={cardStyle}>
          <h1 style={{ margin: '0 0 16px', fontSize: '1.5rem', color: '#111827' }}>Registration Successful</h1>
          <p role="status" style={{ color: '#15803d', marginBottom: '24px' }}>{success}</p>
          <p style={{ color: '#475569', fontSize: '0.9em' }}>
            <a href="/login" style={{ color: '#1d4ed8' }}>Sign in to your account</a>
          </p>
        </div>
      </div>
    );
  }

  return (
    <div style={cardContainerStyle}>
      <div style={cardStyle}>
        <h1 style={{ margin: '0 0 4px', fontSize: '1.5rem', color: '#111827' }}>Create Account</h1>
        <p style={{ margin: '0 0 28px', color: '#475569', fontSize: '0.9em' }}>
          Pipeline Troubleshooting Assistant
        </p>

        <form onSubmit={handleSubmit} noValidate>
          <div style={fieldStyle}>
            <label htmlFor="email" style={labelStyle}>Email</label>
            <input
              id="email"
              type="email"
              name="email"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              style={inputStyle}
              placeholder="you@example.com"
              autoComplete="email"
              disabled={loading}
              aria-invalid={fieldErrors.email ? 'true' : undefined}
              aria-describedby={fieldErrors.email ? 'email-error' : undefined}
            />
            {fieldErrors.email && (
              <p id="email-error" role="alert" style={errorTextStyle}>{fieldErrors.email}</p>
            )}
          </div>

          <div style={fieldStyle}>
            <label htmlFor="displayName" style={labelStyle}>Display Name</label>
            <input
              id="displayName"
              type="text"
              name="displayName"
              value={displayName}
              onChange={(e) => setDisplayName(e.target.value)}
              style={inputStyle}
              placeholder="Your name"
              autoComplete="name"
              disabled={loading}
              aria-invalid={fieldErrors.displayName ? 'true' : undefined}
              aria-describedby={fieldErrors.displayName ? 'displayName-error' : undefined}
            />
            {fieldErrors.displayName && (
              <p id="displayName-error" role="alert" style={errorTextStyle}>{fieldErrors.displayName}</p>
            )}
          </div>

          <div style={fieldStyle}>
            <label htmlFor="password" style={labelStyle}>Password</label>
            <input
              id="password"
              type="password"
              name="password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              style={inputStyle}
              placeholder="••••••••"
              autoComplete="new-password"
              disabled={loading}
              aria-invalid={fieldErrors.password ? 'true' : undefined}
              aria-describedby={fieldErrors.password ? 'password-error' : undefined}
            />
            {fieldErrors.password && (
              <p id="password-error" role="alert" style={errorTextStyle}>{fieldErrors.password}</p>
            )}
          </div>

          <div style={fieldStyle}>
            <label htmlFor="confirmPassword" style={labelStyle}>Confirm Password</label>
            <input
              id="confirmPassword"
              type="password"
              name="confirmPassword"
              value={confirmPassword}
              onChange={(e) => setConfirmPassword(e.target.value)}
              style={inputStyle}
              placeholder="••••••••"
              autoComplete="new-password"
              disabled={loading}
              aria-invalid={fieldErrors.confirmPassword ? 'true' : undefined}
              aria-describedby={fieldErrors.confirmPassword ? 'confirmPassword-error' : undefined}
            />
            {fieldErrors.confirmPassword && (
              <p id="confirmPassword-error" role="alert" style={errorTextStyle}>{fieldErrors.confirmPassword}</p>
            )}
          </div>

          {error && (
            <p role="alert" style={{ color: '#b91c1c', margin: '0 0 16px', fontSize: '0.9em' }}>
              {error}
            </p>
          )}

          <button
            type="submit"
            disabled={loading}
            aria-busy={loading}
            style={{ ...primaryBtnStyle, opacity: loading ? 0.5 : 1, cursor: loading ? 'not-allowed' : 'pointer' }}
          >
            {loading ? 'Creating account…' : 'Create Account'}
          </button>
        </form>

        <p style={{ marginTop: '24px', textAlign: 'center', color: '#475569', fontSize: '0.9em', margin: '24px 0 0' }}>
          Already have an account?{' '}
          <a href="/login" style={{ color: '#1d4ed8' }}>Sign in</a>
        </p>
      </div>
    </div>
  );
}
