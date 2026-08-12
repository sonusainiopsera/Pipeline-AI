import React, { useEffect, useState } from 'react';
import { PanelLeftClose, PanelLeftOpen, BarChart2, Search, BookOpen, Clock } from 'lucide-react';
import SkipLink from './SkipLink';

export type Page = 'dashboard' | 'analyze' | 'knowledge-base' | 'history';

interface NavItem {
  id: Page;
  label: string;
  icon: React.ReactNode;
}

const NAV_ITEMS: NavItem[] = [
  { id: 'dashboard', label: 'Dashboard', icon: <BarChart2 size={18} aria-hidden="true" /> },
  { id: 'analyze', label: 'Analyze', icon: <Search size={18} aria-hidden="true" /> },
  { id: 'knowledge-base', label: 'Knowledge Base', icon: <BookOpen size={18} aria-hidden="true" /> },
  { id: 'history', label: 'History', icon: <Clock size={18} aria-hidden="true" /> },
];

interface LayoutProps {
  page: Page;
  onNavigate: (page: Page) => void;
  children: React.ReactNode;
}

export default function Layout({ page, onNavigate, children }: LayoutProps) {
  // On mobile (< 768px) sidebar starts closed; on desktop it starts open.
  // Lazy initialiser runs once on mount so there is no flash-of-wrong-state.
  const [sidebarOpen, setSidebarOpen] = useState<boolean>(() => {
    if (typeof window !== 'undefined') {
      return window.innerWidth >= 768;
    }
    return true;
  });

  // When viewport crosses the 768px boundary, auto-close on shrink and
  // auto-open on expand so the layout is always in a sensible default state.
  useEffect(() => {
    const mq = window.matchMedia('(max-width: 767px)');
    const handleChange = (e: MediaQueryListEvent) => {
      if (e.matches) {
        // Became mobile — close sidebar so it doesn't overlay content
        setSidebarOpen(false);
      } else {
        // Became desktop — open sidebar by default
        setSidebarOpen(true);
      }
    };
    mq.addEventListener('change', handleChange);
    return () => mq.removeEventListener('change', handleChange);
  }, []);

  const isMobile = typeof window !== 'undefined' && window.innerWidth < 768;

  // On mobile: toggle label describes opening/closing the overlay menu.
  // On desktop: toggle describes collapsing/expanding the sidebar panel.
  const toggleLabel = isMobile
    ? sidebarOpen ? 'Close navigation menu' : 'Open navigation menu'
    : sidebarOpen ? 'Collapse sidebar' : 'Expand sidebar';

  const closeSidebar = () => setSidebarOpen(false);

  return (
    <>
      <SkipLink />

      {/* Semi-transparent backdrop rendered when mobile sidebar is open.
          CSS hides it on desktop so there is no visual impact there. */}
      {sidebarOpen && (
        <div
          className="sidebar-backdrop"
          onClick={closeSidebar}
          aria-hidden="true"
          data-testid="sidebar-backdrop"
        />
      )}

      <div style={{ display: 'flex', minHeight: '100vh', fontFamily: 'sans-serif' }}>
        {/* Sidebar navigation.
            .sidebar-nav — targeted by responsive CSS in styles.css
            .mobile-open — CSS class that slides the overlay into view on mobile */}
        <nav
          id="sidebar-nav"
          className={`sidebar-nav${sidebarOpen ? ' mobile-open' : ''}`}
          aria-label="Primary navigation"
          style={{
            width: sidebarOpen ? '220px' : '60px',
            overflow: 'hidden',
            transition: 'width 0.2s ease',
            backgroundColor: '#101a2d',
            color: '#e2e8f0',
            display: 'flex',
            flexDirection: 'column',
            flexShrink: 0,
          }}
        >
          {/* Sidebar header with toggle button */}
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

          {/* Nav items */}
          <ul
            id="sidebar-nav-list"
            role="list"
            style={{ listStyle: 'none', margin: 0, padding: '8px 0', flex: 1 }}
          >
            {NAV_ITEMS.map((item) => (
              <li key={item.id}>
                <button
                  type="button"
                  onClick={() => {
                    onNavigate(item.id);
                    // Close mobile sidebar after navigation
                    if (typeof window !== 'undefined' && window.innerWidth < 768) {
                      setSidebarOpen(false);
                    }
                  }}
                  aria-current={page === item.id ? 'page' : undefined}
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
        </nav>

        {/* Main content area */}
        <main
          id="main-content"
          tabIndex={-1}
          style={{
            flex: 1,
            padding: '28px',
            minWidth: 0,
            backgroundColor: '#f8fafc',
          }}
        >
          {children}
        </main>
      </div>
    </>
  );
}
