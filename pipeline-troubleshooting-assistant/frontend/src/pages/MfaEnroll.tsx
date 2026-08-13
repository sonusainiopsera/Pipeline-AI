import React, { useEffect, useState } from 'react';
import { QRCodeSVG } from 'qrcode.react';
import { mfaSetup, mfaVerify, ApiError } from '../api';
import type { MfaSetupResponse } from '../types';

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
  maxWidth: '480px',
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

const primaryBtnStyle: React.CSSProperties = {
  padding: '10px 20px',
  backgroundColor: '#101a2d',
  color: '#ffffff',
  border: 'none',
  borderRadius: '4px',
  fontSize: '1em',
  cursor: 'pointer',
  fontWeight: 500,
};

const codeGridStyle: React.CSSProperties = {
  display: 'grid',
  gridTemplateColumns: '1fr 1fr',
  gap: '8px',
  margin: '12px 0',
};

const recoveryCodeStyle: React.CSSProperties = {
  fontFamily: 'monospace',
  fontSize: '0.95em',
  padding: '8px 12px',
  backgroundColor: '#f1f5f9',
  border: '1px solid #e2e8f0',
  borderRadius: '4px',
  color: '#111827',
};

export default function MfaEnrollPage() {
  const [setupData, setSetupData] = useState<MfaSetupResponse | null>(null);
  const [code, setCode] = useState('');
  const [loading, setLoading] = useState(true);
  const [verifying, setVerifying] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [verified, setVerified] = useState(false);
  const [acknowledged, setAcknowledged] = useState(false);
  const [copied, setCopied] = useState(false);
  const [uriCopied, setUriCopied] = useState(false);

  useEffect(() => {
    mfaSetup()
      .then((data) => setSetupData(data))
      .catch((err) => {
        setError(err instanceof ApiError ? err.message : 'Failed to initialize MFA setup. Please try again.');
      })
      .finally(() => setLoading(false));
  }, []);

  const handleCopyUri = async () => {
    if (!setupData?.qrCodeUri) return;
    try {
      await navigator.clipboard.writeText(setupData.qrCodeUri);
      setUriCopied(true);
      setTimeout(() => setUriCopied(false), 2000);
    } catch {
      // clipboard not available — user can manually copy
    }
  };

  const handleCopyAll = async () => {
    if (!setupData?.recoveryCodes) return;
    try {
      await navigator.clipboard.writeText(setupData.recoveryCodes.join('\n'));
      setCopied(true);
      setTimeout(() => setCopied(false), 2000);
    } catch {
      // clipboard not available
    }
  };

  const handleVerify = async (e: React.FormEvent) => {
    e.preventDefault();
    const trimmedCode = code.trim();
    if (!trimmedCode) {
      setError('Please enter your 6-digit code');
      return;
    }
    setVerifying(true);
    setError(null);
    try {
      await mfaVerify({ code: trimmedCode });
      setVerified(true);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Invalid code. Please try again.');
    } finally {
      setVerifying(false);
    }
  };

  if (loading) {
    return (
      <div style={cardContainerStyle}>
        <div style={cardStyle}>
          <p role="status" style={{ color: '#475569' }}>Setting up MFA…</p>
        </div>
      </div>
    );
  }

  if (error && !setupData) {
    return (
      <div style={cardContainerStyle}>
        <div style={cardStyle}>
          <h1 style={{ margin: '0 0 16px', fontSize: '1.5rem', color: '#111827' }}>MFA Setup Error</h1>
          <p role="alert" style={{ color: '#b91c1c', marginBottom: '16px' }}>{error}</p>
          <a href="/" style={{ color: '#1d4ed8', fontSize: '0.9em' }}>← Return to dashboard</a>
        </div>
      </div>
    );
  }

  if (verified && setupData) {
    return (
      <div style={cardContainerStyle}>
        <div style={cardStyle}>
          <h1 style={{ margin: '0 0 8px', fontSize: '1.5rem', color: '#111827' }}>Save Your Recovery Codes</h1>
          <p style={{ color: '#b91c1c', fontWeight: 500, margin: '0 0 8px', fontSize: '0.9em' }}>
            ⚠ These codes will not be shown again. Save them in a secure place.
          </p>
          <p style={{ color: '#475569', margin: '0 0 16px', fontSize: '0.875em' }}>
            Use these codes to access your account if you lose your authenticator device.
          </p>

          <div style={codeGridStyle} aria-label="Recovery codes">
            {setupData.recoveryCodes.map((c, i) => (
              <span key={i} style={recoveryCodeStyle}>{c}</span>
            ))}
          </div>

          <button
            type="button"
            onClick={handleCopyAll}
            style={{ ...primaryBtnStyle, marginBottom: '24px', cursor: 'pointer' }}
          >
            {copied ? 'Copied!' : 'Copy All Codes'}
          </button>

          <div style={{ marginBottom: '24px', display: 'flex', alignItems: 'flex-start', gap: '8px' }}>
            <input
              id="ack-codes"
              type="checkbox"
              checked={acknowledged}
              onChange={(e) => setAcknowledged(e.target.checked)}
              style={{ marginTop: '2px', flexShrink: 0 }}
            />
            <label htmlFor="ack-codes" style={{ color: '#111827', fontSize: '0.9em', cursor: 'pointer' }}>
              I have saved my recovery codes in a secure location
            </label>
          </div>

          <button
            type="button"
            onClick={() => { window.location.href = '/'; }}
            disabled={!acknowledged}
            style={{
              ...primaryBtnStyle,
              width: '100%',
              opacity: acknowledged ? 1 : 0.5,
              cursor: acknowledged ? 'pointer' : 'not-allowed',
            }}
          >
            Continue to Dashboard
          </button>
        </div>
      </div>
    );
  }

  return (
    <div style={cardContainerStyle}>
      <div style={cardStyle}>
        <h1 style={{ margin: '0 0 4px', fontSize: '1.5rem', color: '#111827' }}>
          Set Up Two-Factor Authentication
        </h1>
        <p style={{ margin: '0 0 24px', color: '#475569', fontSize: '0.9em' }}>
          Scan the QR code with your authenticator app (Google Authenticator, Authy, etc.), then enter the 6-digit code to confirm.
        </p>

        {setupData && (
          <>
            <div style={{ marginBottom: '24px', textAlign: 'center' }}>
              <div
                role="img"
                aria-label="QR code for authenticator app"
                style={{
                  display: 'inline-block',
                  padding: '16px',
                  backgroundColor: '#ffffff',
                  border: '1px solid #e2e8f0',
                  borderRadius: '8px',
                  marginBottom: '12px',
                }}
              >
                <QRCodeSVG
                  value={setupData.qrCodeUri}
                  size={200}
                  level="M"
                  includeMargin={false}
                  bgColor="#ffffff"
                  fgColor="#111827"
                />
              </div>
              <p style={{ margin: '0 0 8px', fontWeight: 500, color: '#111827', fontSize: '0.9em', textAlign: 'left' }}>
                Or enter this URI manually
              </p>
              <div style={{ position: 'relative' }}>
                <textarea
                  readOnly
                  value={setupData.qrCodeUri}
                  rows={3}
                  aria-label="QR code URI for authenticator app"
                  style={{
                    ...inputStyle,
                    fontFamily: 'monospace',
                    fontSize: '0.78em',
                    resize: 'none',
                    color: '#475569',
                    backgroundColor: '#f8fafc',
                    wordBreak: 'break-all',
                  }}
                  onClick={(e) => (e.target as HTMLTextAreaElement).select()}
                />
              </div>
              <button
                type="button"
                onClick={handleCopyUri}
                style={{
                  marginTop: '6px',
                  padding: '6px 14px',
                  fontSize: '0.85em',
                  backgroundColor: '#f1f5f9',
                  color: '#111827',
                  border: '1px solid #64748b',
                  borderRadius: '4px',
                  cursor: 'pointer',
                }}
              >
                {uriCopied ? 'Copied!' : 'Copy URI'}
              </button>
            </div>

            <form onSubmit={handleVerify} noValidate>
              <div style={{ marginBottom: '16px' }}>
                <label htmlFor="enroll-code" style={labelStyle}>Verification Code</label>
                <input
                  id="enroll-code"
                  type="text"
                  inputMode="numeric"
                  pattern="[0-9]{6}"
                  maxLength={6}
                  value={code}
                  onChange={(e) => setCode(e.target.value)}
                  style={{
                    ...inputStyle,
                    fontSize: '1.4em',
                    letterSpacing: '0.3em',
                    textAlign: 'center',
                    fontFamily: 'monospace',
                  }}
                  placeholder="000000"
                  autoComplete="one-time-code"
                  disabled={verifying}
                  aria-describedby={error ? 'enroll-error' : undefined}
                />
                {error && (
                  <p id="enroll-error" role="alert" style={{ color: '#b91c1c', margin: '4px 0 0', fontSize: '0.85em' }}>
                    {error}
                  </p>
                )}
              </div>

              <button
                type="submit"
                disabled={verifying}
                aria-busy={verifying}
                style={{
                  ...primaryBtnStyle,
                  width: '100%',
                  opacity: verifying ? 0.5 : 1,
                  cursor: verifying ? 'not-allowed' : 'pointer',
                }}
              >
                {verifying ? 'Verifying…' : 'Verify and Enable MFA'}
              </button>
            </form>
          </>
        )}

        <p style={{ marginTop: '16px', textAlign: 'center', fontSize: '0.875em', margin: '16px 0 0' }}>
          <a href="/" style={{ color: '#475569' }}>Skip for now</a>
        </p>
      </div>
    </div>
  );
}
