import React, { useState } from 'react';
import { mfaChallenge, ApiError } from '../api';

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

export default function MfaVerifyPage() {
  const [code, setCode] = useState('');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    const trimmedCode = code.trim();
    if (!trimmedCode) {
      setError('Please enter your 6-digit code');
      return;
    }
    setLoading(true);
    setError(null);
    try {
      await mfaChallenge({ code: trimmedCode });
      window.location.href = '/';
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Invalid code. Please try again.');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div style={cardContainerStyle}>
      <div style={cardStyle}>
        <h1 style={{ margin: '0 0 4px', fontSize: '1.5rem', color: '#111827' }}>
          Two-Factor Authentication
        </h1>
        <p style={{ margin: '0 0 28px', color: '#475569', fontSize: '0.9em' }}>
          Enter the 6-digit code from your authenticator app to continue.
        </p>

        <form onSubmit={handleSubmit} noValidate>
          <div style={{ marginBottom: '16px' }}>
            <label htmlFor="totp-code" style={labelStyle}>Authentication Code</label>
            <input
              id="totp-code"
              type="text"
              inputMode="numeric"
              pattern="[0-9]{6}"
              maxLength={6}
              value={code}
              onChange={(e) => setCode(e.target.value)}
              style={{
                width: '100%',
                padding: '10px 12px',
                border: '1px solid #64748b',
                borderRadius: '4px',
                fontSize: '1.5em',
                color: '#111827',
                backgroundColor: '#ffffff',
                boxSizing: 'border-box',
                letterSpacing: '0.3em',
                textAlign: 'center',
                fontFamily: 'monospace',
              }}
              placeholder="000000"
              autoComplete="one-time-code"
              disabled={loading}
              aria-describedby={error ? 'totp-error' : undefined}
            />
            {error && (
              <p id="totp-error" role="alert" style={{ color: '#b91c1c', margin: '4px 0 0', fontSize: '0.85em' }}>
                {error}
              </p>
            )}
          </div>

          <button
            type="submit"
            disabled={loading}
            aria-busy={loading}
            style={{ ...primaryBtnStyle, opacity: loading ? 0.5 : 1, cursor: loading ? 'not-allowed' : 'pointer' }}
          >
            {loading ? 'Verifying…' : 'Verify'}
          </button>
        </form>

        <p style={{ marginTop: '24px', textAlign: 'center', color: '#475569', fontSize: '0.875em', margin: '24px 0 0' }}>
          Lost access to your authenticator?{' '}
          <a href="/mfa/recover" style={{ color: '#1d4ed8' }}>Use a recovery code</a>
        </p>

        <p style={{ marginTop: '12px', textAlign: 'center', fontSize: '0.875em', margin: '12px 0 0' }}>
          <a href="/login" style={{ color: '#475569' }}>← Back to login</a>
        </p>
      </div>
    </div>
  );
}
