import React, { useState } from 'react';
import { Toaster } from 'react-hot-toast';
import Layout, { Page } from './components/Layout';
import RouteAnnouncer from './components/RouteAnnouncer';
import ProtectedRoute from './components/ProtectedRoute';
import DashboardPage from './pages/Dashboard';
import AnalyzePage from './pages/Analyze';
import KnowledgeBasePage from './pages/KnowledgeBase';
import HistoryPage from './pages/History';
import AuditLogPage from './pages/AuditLog';
import SettingsPage from './pages/Settings';
import LoginPage from './pages/Login';
import RegisterPage from './pages/Register';
import MfaVerifyPage from './pages/MfaVerify';
import MfaEnrollPage from './pages/MfaEnroll';
import { AuthProvider } from './contexts/AuthContext';
import { ThemeProvider } from './contexts/ThemeContext';

function getAuthRoute(): string | null {
  if (typeof window === 'undefined') return null;
  const path = window.location.pathname;
  if (path === '/login' || path.startsWith('/login/')) return 'login';
  if (path === '/register' || path.startsWith('/register/')) return 'register';
  if (path === '/mfa/verify' || path.startsWith('/mfa/verify/')) return 'mfa-verify';
  if (path === '/mfa/enroll' || path.startsWith('/mfa/enroll/')) return 'mfa-enroll';
  return null;
}

function withProviders(children: React.ReactNode) {
  return (
    <ThemeProvider>
      <AuthProvider>
        <Toaster position="top-right" />
        {children}
      </AuthProvider>
    </ThemeProvider>
  );
}

export default function App() {
  const [page, setPage] = useState<Page>('dashboard');
  const authRoute = getAuthRoute();

  if (authRoute === 'login') {
    return withProviders(<LoginPage />);
  }
  if (authRoute === 'register') {
    return withProviders(<RegisterPage />);
  }
  if (authRoute === 'mfa-verify') {
    return withProviders(<MfaVerifyPage />);
  }
  if (authRoute === 'mfa-enroll') {
    return withProviders(
      <ProtectedRoute>
        <MfaEnrollPage />
      </ProtectedRoute>,
    );
  }

  const renderPage = () => {
    switch (page) {
      case 'dashboard':
        return <DashboardPage />;
      case 'analyze':
        return <AnalyzePage />;
      case 'knowledge-base':
        return <KnowledgeBasePage />;
      case 'history':
        return <HistoryPage />;
      case 'audit-log':
        return <AuditLogPage />;
      case 'settings':
        return <SettingsPage />;
    }
  };

  return withProviders(
    <ProtectedRoute>
      <RouteAnnouncer page={page} />
      <Layout page={page} onNavigate={setPage}>
        {renderPage()}
      </Layout>
    </ProtectedRoute>,
  );
}
