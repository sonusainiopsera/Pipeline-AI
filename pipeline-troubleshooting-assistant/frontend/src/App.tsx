import React, { useState } from 'react';
import Layout, { Page } from './components/Layout';
import RouteAnnouncer from './components/RouteAnnouncer';
import DashboardPage from './pages/Dashboard';
import AnalyzePage from './pages/Analyze';
import KnowledgeBasePage from './pages/KnowledgeBase';
import HistoryPage from './pages/History';
import LoginPage from './pages/Login';
import RegisterPage from './pages/Register';
import MfaVerifyPage from './pages/MfaVerify';
import MfaEnrollPage from './pages/MfaEnroll';
import { AuthProvider } from './contexts/AuthContext';

function getAuthRoute(): string | null {
  if (typeof window === 'undefined') return null;
  const path = window.location.pathname;
  if (path === '/login' || path.startsWith('/login/')) return 'login';
  if (path === '/register' || path.startsWith('/register/')) return 'register';
  if (path === '/mfa/verify' || path.startsWith('/mfa/verify/')) return 'mfa-verify';
  if (path === '/mfa/enroll' || path.startsWith('/mfa/enroll/')) return 'mfa-enroll';
  return null;
}

export default function App() {
  const [page, setPage] = useState<Page>('analyze');
  const authRoute = getAuthRoute();

  if (authRoute === 'login') {
    return <AuthProvider><LoginPage /></AuthProvider>;
  }
  if (authRoute === 'register') {
    return <AuthProvider><RegisterPage /></AuthProvider>;
  }
  if (authRoute === 'mfa-verify') {
    return <AuthProvider><MfaVerifyPage /></AuthProvider>;
  }
  if (authRoute === 'mfa-enroll') {
    return <AuthProvider><MfaEnrollPage /></AuthProvider>;
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
    }
  };

  return (
    <AuthProvider>
      {/* Announces page transitions to screen readers and updates document.title */}
      <RouteAnnouncer page={page} />
      <Layout page={page} onNavigate={setPage}>
        {renderPage()}
      </Layout>
    </AuthProvider>
  );
}
