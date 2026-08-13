import React, { useState } from 'react';
import { login as apiLogin, resendVerification, ApiError } from '../api';

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

export default function LoginPage() {
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [loading, setLoading] = useState(false);
  const [resending, setResending] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [emailError, setEmailError] = useState<string | null>(null);
  const [passwordError, setPasswordError] = useState<string | null>(null);
  const [needsVerification, setNeedsVerification] = useState(false);
  const [verificationUrl, setVerificationUrl] = useState<string | null>(null);
  const [resendMessage, setResendMessage] = useState<string | null>(null);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    let valid = true;

    if (!email.trim()) {
      setEmailError('Email is required');
      valid = false;
    } else {
      setEmailError(null);
    }

    if (!password) {
      setPasswordError('Password is required');
      valid = false;
    } else {
      setPasswordError(null);
    }

    if (!valid) return;

    setLoading(true);
    setError(null);
    setNeedsVerification(false);
    setVerificationUrl(null);
    setResendMessage(null);

    try {
      const response = await apiLogin({ email: email.trim(), password });
      if (response.mfaRequired) {
        window.location.href = '/mfa/verify';
      } else if (!response.mfaEnabled) {
        window.location.href = '/mfa/enroll';
      } else {
        window.location.href = '/';
      }
    } catch (err) {
      const message = err instanceof ApiError ? err.message : 'Login failed. Please try again.';
      setError(message);
      if (/verify your email/i.test(message)) {
        setNeedsVerification(true);
      }
    } finally {
      setLoading(false);
    }
  };

  const handleResend = async () => {
    if (!email.trim()) {
      setEmailError('Email is required');
      return;
    }
    setResending(true);
    setResendMessage(null);
    try {
      const response = await resendVerification(email.trim());
      setResendMessage(response.message);
      setVerificationUrl(response.verificationUrl ?? null);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not resend verification.');
    } finally {
      setResending(false);
    }
  };

  return (
    <div style={cardContainerStyle}>
      <div style={cardStyle}>
        <h1 style={{ margin: '0 0 4px', fontSize: '1.5rem', color: '#111827' }}>Sign In</h1>
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
              aria-invalid={emailError ? 'true' : undefined}
              aria-describedby={emailError ? 'email-error' : undefined}
            />
            {emailError && (
              <p id="email-error" role="alert" style={{ color: '#b91c1c', margin: '4px 0 0', fontSize: '0.85em' }}>
                {emailError}
              </p>
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
              autoComplete="current-password"
              disabled={loading}
              aria-invalid={passwordError ? 'true' : undefined}
              aria-describedby={passwordError ? 'password-error' : undefined}
            />
            {passwordError && (
              <p id="password-error" role="alert" style={{ color: '#b91c1c', margin: '4px 0 0', fontSize: '0.85em' }}>
                {passwordError}
              </p>
            )}
          </div>

          {error && (
            <p role="alert" style={{ color: '#b91c1c', margin: '0 0 16px', fontSize: '0.9em' }}>
              {error}
            </p>
          )}

          {needsVerification && (
            <div style={{ marginBottom: '16px' }}>
              <button
                type="button"
                onClick={handleResend}
                disabled={resending}
                style={{
                  ...primaryBtnStyle,
                  backgroundColor: '#ffffff',
                  color: '#101a2d',
                  border: '1px solid #101a2d',
                  marginBottom: '8px',
                  opacity: resending ? 0.5 : 1,
                }}
              >
                {resending ? 'Sending…' : 'Resend verification link'}
              </button>
              {resendMessage && (
                <p role="status" style={{ color: '#15803d', fontSize: '0.85em', margin: '0 0 8px' }}>
                  {resendMessage}
                </p>
              )}
              {verificationUrl && (
                <>
                  <p style={{ color: '#475569', fontSize: '0.85em', margin: '0 0 8px' }}>
                    Local development does not send real email. Open this link:
                  </p>
                  <a href={verificationUrl} style={{ color: '#1d4ed8', fontSize: '0.85em', wordBreak: 'break-all' }}>
                    {verificationUrl}
                  </a>
                </>
              )}
            </div>
          )}

          <button
            type="submit"
            disabled={loading}
            aria-busy={loading}
            style={{ ...primaryBtnStyle, opacity: loading ? 0.5 : 1, cursor: loading ? 'not-allowed' : 'pointer' }}
          >
            {loading ? 'Signing in…' : 'Sign In'}
          </button>
        </form>

        <p style={{ marginTop: '24px', textAlign: 'center', color: '#475569', fontSize: '0.9em', margin: '24px 0 0' }}>
          Don't have an account?{' '}
          <a href="/register" style={{ color: '#1d4ed8' }}>Register</a>
        </p>
      </div>
    </div>
  );
}
