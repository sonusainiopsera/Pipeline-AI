import React, { useEffect, useState } from 'react';
import toast from 'react-hot-toast';
import {
  getProfile,
  updateProfile,
  changePassword,
  getSessions,
  revokeSession,
  ApiError,
} from '../api';
import type { UserProfile, SessionInfo } from '../api';

export default function SettingsPage() {
  const [profile, setProfile] = useState<UserProfile | null>(null);
  const [sessions, setSessions] = useState<SessionInfo[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  // Profile form state
  const [displayName, setDisplayName] = useState('');
  const [profileSaving, setProfileSaving] = useState(false);

  // Password form state
  const [currentPassword, setCurrentPassword] = useState('');
  const [newPassword, setNewPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [passwordSaving, setPasswordSaving] = useState(false);

  useEffect(() => {
    loadData();
  }, []);

  async function loadData() {
    setLoading(true);
    setError(null);
    try {
      const [profileData, sessionsData] = await Promise.all([getProfile(), getSessions()]);
      setProfile(profileData);
      setDisplayName(profileData.displayName);
      setSessions(sessionsData);
    } catch {
      setError('Unable to load settings. Please try again.');
    } finally {
      setLoading(false);
    }
  }

  async function handleProfileSave(e: React.FormEvent) {
    e.preventDefault();
    if (!displayName.trim()) {
      toast.error('Display name is required');
      return;
    }
    setProfileSaving(true);
    try {
      const updated = await updateProfile({ displayName: displayName.trim() });
      setProfile(updated);
      toast.success('Profile updated successfully');
    } catch (err) {
      const msg = err instanceof ApiError ? err.message : 'Failed to update profile';
      toast.error(msg);
    } finally {
      setProfileSaving(false);
    }
  }

  async function handlePasswordChange(e: React.FormEvent) {
    e.preventDefault();
    if (newPassword !== confirmPassword) {
      toast.error('New passwords do not match');
      return;
    }
    if (newPassword.length < 12) {
      toast.error('Password must be at least 12 characters');
      return;
    }
    setPasswordSaving(true);
    try {
      await changePassword({ currentPassword, newPassword });
      toast.success('Password changed successfully. Please log in again.');
      setCurrentPassword('');
      setNewPassword('');
      setConfirmPassword('');
    } catch (err) {
      const msg = err instanceof ApiError ? err.message : 'Failed to change password';
      toast.error(msg);
    } finally {
      setPasswordSaving(false);
    }
  }

  async function handleRevokeSession(sessionId: string) {
    if (!window.confirm('Revoke this session? You will be logged out if this is your current session.')) {
      return;
    }
    try {
      await revokeSession(sessionId);
      setSessions(prev => prev.filter(s => s.sessionId !== sessionId));
      toast.success('Session revoked');
    } catch (err) {
      const msg = err instanceof ApiError ? err.message : 'Failed to revoke session';
      toast.error(msg);
    }
  }

  if (loading) {
    return (
      <div role="status" aria-label="Loading settings" style={{ padding: '40px', textAlign: 'center', color: '#64748b' }}>
        Loading settings...
      </div>
    );
  }

  if (error) {
    return (
      <div role="alert" style={{ padding: '40px', textAlign: 'center' }}>
        <p style={{ color: '#dc2626', marginBottom: '16px' }}>{error}</p>
        <button
          type="button"
          onClick={loadData}
          style={{
            padding: '8px 16px',
            backgroundColor: '#3b82f6',
            color: '#fff',
            border: 'none',
            borderRadius: '6px',
            cursor: 'pointer',
          }}
        >
          Retry
        </button>
      </div>
    );
  }

  return (
    <div style={{ maxWidth: '680px' }}>
      <h1 style={{ fontSize: '1.5rem', fontWeight: 700, color: '#1e293b', marginBottom: '32px' }}>
        Settings
      </h1>

      {/* ── Profile Section ──────────────────────────────────────────────────── */}
      <section aria-labelledby="profile-heading" style={sectionStyle}>
        <h2 id="profile-heading" style={sectionHeadingStyle}>Profile</h2>
        <form onSubmit={handleProfileSave}>
          <div style={fieldGroupStyle}>
            <label htmlFor="displayName" style={labelStyle}>Display Name</label>
            <input
              id="displayName"
              type="text"
              value={displayName}
              onChange={e => setDisplayName(e.target.value)}
              maxLength={100}
              required
              style={inputStyle}
            />
          </div>
          <div style={fieldGroupStyle}>
            <label style={labelStyle}>Email</label>
            <p style={readOnlyStyle}>{profile?.email}</p>
          </div>
          <div style={fieldGroupStyle}>
            <label style={labelStyle}>Role</label>
            <span style={badgeStyle}>{profile?.role}</span>
          </div>
          <button
            type="submit"
            disabled={profileSaving}
            style={primaryButtonStyle}
          >
            {profileSaving ? 'Saving...' : 'Save Profile'}
          </button>
        </form>
      </section>

      {/* ── Security Section ─────────────────────────────────────────────────── */}
      <section aria-labelledby="security-heading" style={sectionStyle}>
        <h2 id="security-heading" style={sectionHeadingStyle}>Security</h2>

        <div style={{ marginBottom: '24px' }}>
          <p style={labelStyle}>MFA Status</p>
          {profile?.mfaEnabled ? (
            <span style={{ ...badgeStyle, backgroundColor: '#dcfce7', color: '#16a34a' }}>
              Enabled
            </span>
          ) : (
            <span style={{ ...badgeStyle, backgroundColor: '#fef9c3', color: '#92400e' }}>
              Not configured
            </span>
          )}
        </div>

        <form onSubmit={handlePasswordChange}>
          <h3 style={{ fontSize: '0.95rem', fontWeight: 600, color: '#374151', marginBottom: '16px' }}>
            Change Password
          </h3>
          <div style={fieldGroupStyle}>
            <label htmlFor="currentPassword" style={labelStyle}>Current Password</label>
            <input
              id="currentPassword"
              type="password"
              value={currentPassword}
              onChange={e => setCurrentPassword(e.target.value)}
              required
              autoComplete="current-password"
              style={inputStyle}
            />
          </div>
          <div style={fieldGroupStyle}>
            <label htmlFor="newPassword" style={labelStyle}>New Password</label>
            <input
              id="newPassword"
              type="password"
              value={newPassword}
              onChange={e => setNewPassword(e.target.value)}
              minLength={12}
              required
              autoComplete="new-password"
              style={inputStyle}
            />
            <p style={{ fontSize: '0.75rem', color: '#64748b', marginTop: '4px' }}>
              12+ characters with uppercase, lowercase, digit, and special character
            </p>
          </div>
          <div style={fieldGroupStyle}>
            <label htmlFor="confirmPassword" style={labelStyle}>Confirm New Password</label>
            <input
              id="confirmPassword"
              type="password"
              value={confirmPassword}
              onChange={e => setConfirmPassword(e.target.value)}
              minLength={12}
              required
              autoComplete="new-password"
              style={inputStyle}
            />
          </div>
          <button
            type="submit"
            disabled={passwordSaving}
            style={primaryButtonStyle}
          >
            {passwordSaving ? 'Changing...' : 'Change Password'}
          </button>
        </form>
      </section>

      {/* ── Active Sessions Section ──────────────────────────────────────────── */}
      <section aria-labelledby="sessions-heading" style={sectionStyle}>
        <h2 id="sessions-heading" style={sectionHeadingStyle}>Active Sessions</h2>
        {sessions.length === 0 ? (
          <p style={{ color: '#64748b', fontSize: '0.875rem' }}>No active sessions.</p>
        ) : (
          <ul role="list" style={{ listStyle: 'none', margin: 0, padding: 0 }}>
            {sessions.map(session => (
              <li
                key={session.sessionId}
                style={{
                  display: 'flex',
                  justifyContent: 'space-between',
                  alignItems: 'center',
                  padding: '12px 0',
                  borderBottom: '1px solid #e2e8f0',
                }}
              >
                <div>
                  <p style={{ fontSize: '0.875rem', color: '#374151', margin: 0 }}>
                    Session {session.sessionId.slice(0, 8)}...
                    {session.isCurrent && (
                      <span style={{ marginLeft: '8px', ...badgeStyle, backgroundColor: '#dbeafe', color: '#1d4ed8' }}>
                        Current
                      </span>
                    )}
                  </p>
                  <p style={{ fontSize: '0.75rem', color: '#64748b', margin: '2px 0 0 0' }}>
                    Created: {new Date(session.createdAt).toLocaleString()}
                    {session.ipAddress && ` · ${session.ipAddress}`}
                  </p>
                </div>
                <button
                  type="button"
                  onClick={() => handleRevokeSession(session.sessionId)}
                  style={dangerButtonStyle}
                >
                  Revoke
                </button>
              </li>
            ))}
          </ul>
        )}
      </section>

      {/* ── Appearance Section ───────────────────────────────────────────────── */}
      <section aria-labelledby="appearance-heading" style={sectionStyle}>
        <h2 id="appearance-heading" style={sectionHeadingStyle}>Appearance</h2>
        <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
          <div>
            <p style={{ fontSize: '0.875rem', fontWeight: 500, color: '#374151', margin: 0 }}>Dark Mode</p>
            <p style={{ fontSize: '0.75rem', color: '#64748b', margin: '2px 0 0 0' }}>Coming soon</p>
          </div>
          <button
            type="button"
            disabled
            aria-label="Toggle dark mode (coming soon)"
            style={{
              width: '44px',
              height: '24px',
              borderRadius: '12px',
              border: 'none',
              backgroundColor: '#e2e8f0',
              cursor: 'not-allowed',
              opacity: 0.6,
            }}
          />
        </div>
      </section>
    </div>
  );
}

const sectionStyle: React.CSSProperties = {
  backgroundColor: '#fff',
  border: '1px solid #e2e8f0',
  borderRadius: '8px',
  padding: '24px',
  marginBottom: '24px',
};

const sectionHeadingStyle: React.CSSProperties = {
  fontSize: '1.05rem',
  fontWeight: 600,
  color: '#1e293b',
  marginBottom: '20px',
  marginTop: 0,
};

const fieldGroupStyle: React.CSSProperties = {
  marginBottom: '16px',
};

const labelStyle: React.CSSProperties = {
  display: 'block',
  fontSize: '0.875rem',
  fontWeight: 500,
  color: '#374151',
  marginBottom: '6px',
};

const inputStyle: React.CSSProperties = {
  width: '100%',
  padding: '8px 12px',
  border: '1px solid #d1d5db',
  borderRadius: '6px',
  fontSize: '0.875rem',
  color: '#1e293b',
  boxSizing: 'border-box',
};

const readOnlyStyle: React.CSSProperties = {
  fontSize: '0.875rem',
  color: '#64748b',
  margin: 0,
};

const badgeStyle: React.CSSProperties = {
  display: 'inline-block',
  padding: '2px 8px',
  borderRadius: '4px',
  fontSize: '0.75rem',
  fontWeight: 500,
  backgroundColor: '#f1f5f9',
  color: '#475569',
};

const primaryButtonStyle: React.CSSProperties = {
  padding: '8px 20px',
  backgroundColor: '#3b82f6',
  color: '#fff',
  border: 'none',
  borderRadius: '6px',
  fontSize: '0.875rem',
  fontWeight: 500,
  cursor: 'pointer',
};

const dangerButtonStyle: React.CSSProperties = {
  padding: '6px 14px',
  backgroundColor: '#fff',
  color: '#dc2626',
  border: '1px solid #fca5a5',
  borderRadius: '6px',
  fontSize: '0.8rem',
  cursor: 'pointer',
};
