import React, { useEffect, useState } from 'react';
import {
  PanelLeftClose,
  PanelLeftOpen,
  BarChart2,
  Search,
  BookOpen,
  Clock,
  ShieldCheck,
  Settings,
  LogOut,
  Moon,
  Sun,
} from 'lucide-react';
import SkipLink from './SkipLink';
import { getUserRole } from '../api';
import type { UserRole } from '../api';
import { useAuth } from '../contexts/AuthContext';
import { useTheme } from '../contexts/ThemeContext';

export type Page = 'dashboard' | 'analyze' | 'knowledge-base' | 'history' | 'audit-log' | 'settings';

interface NavItem {
  id: Page;
  label: string;
  icon: React.ReactNode;
  managerOnly?: boolean;
}

const NAV_ITEMS: NavItem[] = [
  { id: 'dashboard', label: 'Dashboard', icon: <BarChart2 size={18} aria-hidden="true" /> },
  { id: 'analyze', label: 'Analyze', icon: <Search size={18} aria-hidden="true" /> },
  { id: 'knowledge-base', label: 'Knowledge Base', icon: <BookOpen size={18} aria-hidden="true" /> },
  { id: 'history', label: 'History', icon: <Clock size={18} aria-hidden="true" /> },
  { id: 'audit-log', label: 'Audit Log', icon: <ShieldCheck size={18} aria-hidden="true" />, managerOnly: true },
  { id: 'settings', label: 'Settings', icon: <Settings size={18} aria-hidden="true" /> },
];

interface LayoutProps {
  page: Page;
  onNavigate: (page: Page) => void;
  children: React.ReactNode;
}

export default function Layout({ page, onNavigate, children }: LayoutProps) {
  const { user, logout } = useAuth();
  const { theme, toggleTheme } = useTheme();
  const [loggingOut, setLoggingOut] = useState(false);

  const [sidebarOpen, setSidebarOpen] = useState<boolean>(() => {
    if (typeof window !== 'undefined') {
      return window.innerWidth >= 768;
    }
    return true;
  });

  const [userRole, setUserRole] = useState<UserRole | null>(null);

  useEffect(() => {
    getUserRole().then(setUserRole);
  }, []);

  useEffect(() => {
    const mq = window.matchMedia('(max-width: 767px)');
    const handleChange = (e: MediaQueryListEvent) => {
      setSidebarOpen(!e.matches);
    };
    mq.addEventListener('change', handleChange);
    return () => mq.removeEventListener('change', handleChange);
  }, []);

  const isMobile = typeof window !== 'undefined' && window.innerWidth < 768;

  const toggleLabel = isMobile
    ? sidebarOpen ? 'Close navigation menu' : 'Open navigation menu'
    : sidebarOpen ? 'Collapse sidebar' : 'Expand sidebar';

  const closeSidebar = () => setSidebarOpen(false);

  const handleLogout = async () => {
    setLoggingOut(true);
    try {
      await logout();
    } finally {
      window.location.href = '/login';
    }
  };

  return (
    <>
      <SkipLink />

      {sidebarOpen && (
        <div
          className="sidebar-backdrop"
          onClick={closeSidebar}
          aria-hidden="true"
          data-testid="sidebar-backdrop"
        />
      )}

      <div style={{ display: 'flex', minHeight: '100vh', fontFamily: 'sans-serif' }}>
        <nav
          id="sidebar-nav"
          className={`sidebar-nav${sidebarOpen ? ' mobile-open' : ''}`}
          aria-label="Primary navigation"
          style={{
            width: sidebarOpen ? '220px' : '60px',
            overflow: 'hidden',
            transition: 'width 0.2s ease',
            backgroundColor: 'var(--app-sidebar)',
            color: 'var(--app-sidebar-text)',
            display: 'flex',
            flexDirection: 'column',
            flexShrink: 0,
          }}
        >
          <div
            style={{
              padding: '12px',
              display: 'flex',
              alignItems: 'center',
              justifyContent: sidebarOpen ? 'space-between' : 'center',
              borderBottom: '1px solid rgba(255,255,255,0.1)',
              minHeight: '52px',
            }}
          >
            {sidebarOpen && (
              <span
                style={{
                  fontWeight: 700,
                  fontSize: '0.85em',
                  whiteSpace: 'nowrap',
                  overflow: 'hidden',
                  color: '#f1f5f9',
                }}
              >
                Pipeline AI
              </span>
            )}
            <button
              type="button"
              onClick={() => setSidebarOpen((open) => !open)}
              aria-label={toggleLabel}
              aria-expanded={sidebarOpen}
              aria-controls="sidebar-nav-list"
              style={{
                background: 'none',
                border: 'none',
                color: '#e2e8f0',
                cursor: 'pointer',
                padding: '4px',
                display: 'flex',
                alignItems: 'center',
                borderRadius: '4px',
              }}
            >
              {sidebarOpen ? (
                <PanelLeftClose size={20} aria-hidden="true" />
              ) : (
                <PanelLeftOpen size={20} aria-hidden="true" />
              )}
            </button>
          </div>

          <ul
            id="sidebar-nav-list"
            role="list"
            style={{ listStyle: 'none', margin: 0, padding: '8px 0', flex: 1 }}
          >
            {NAV_ITEMS.filter(item => !item.managerOnly || userRole === 'MANAGER').map((item) => (
              <li key={item.id}>
                <button
                  type="button"
                  onClick={() => {
                    onNavigate(item.id);
                    if (typeof window !== 'undefined' && window.innerWidth < 768) {
                      setSidebarOpen(false);
                    }
                  }}
                  aria-current={page === item.id ? 'page' : undefined}
                  aria-label={item.label}
                  style={{
                    width: '100%',
                    background: page === item.id ? 'rgba(96,165,250,0.15)' : 'none',
                    border: 'none',
                    borderLeft: page === item.id ? '3px solid #60a5fa' : '3px solid transparent',
                    color: page === item.id ? '#60a5fa' : '#cbd5e1',
                    cursor: 'pointer',
                    padding: '10px 14px',
                    display: 'flex',
                    alignItems: 'center',
                    gap: '10px',
                    textAlign: 'left',
                    fontSize: '0.9em',
                    transition: 'background 0.15s, color 0.15s',
                  }}
                >
                  <span style={{ flexShrink: 0 }}>{item.icon}</span>
                  {sidebarOpen && (
                    <span style={{ whiteSpace: 'nowrap' }}>{item.label}</span>
                  )}
                </button>
              </li>
            ))}
          </ul>

          <div className="sidebar-footer">
            {sidebarOpen && user && (
              <div className="sidebar-user">
                <strong>{user.displayName || 'Signed in'}</strong>
                <span title={user.email}>{user.email}</span>
              </div>
            )}

            <button
              type="button"
              className="sidebar-action-btn"
              onClick={toggleTheme}
              aria-label={theme === 'dark' ? 'Switch to light mode' : 'Switch to dark mode'}
              title={theme === 'dark' ? 'Light mode' : 'Dark mode'}
            >
              {theme === 'dark' ? <Sun size={16} aria-hidden="true" /> : <Moon size={16} aria-hidden="true" />}
              {sidebarOpen && <span>{theme === 'dark' ? 'Light mode' : 'Dark mode'}</span>}
            </button>

            <button
              type="button"
              className="sidebar-action-btn danger"
              onClick={handleLogout}
              disabled={loggingOut}
              aria-label="Log out"
              title="Log out"
            >
              <LogOut size={16} aria-hidden="true" />
              {sidebarOpen && <span>{loggingOut ? 'Signing out…' : 'Log out'}</span>}
            </button>
          </div>
        </nav>

        <main
          id="main-content"
          tabIndex={-1}
          style={{
            flex: 1,
            padding: '28px',
            minWidth: 0,
            backgroundColor: 'var(--app-bg)',
            color: 'var(--color-text-primary)',
          }}
        >
          {children}
        </main>
      </div>
    </>
  );
}
