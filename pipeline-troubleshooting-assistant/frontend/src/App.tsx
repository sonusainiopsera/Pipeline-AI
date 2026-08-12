import React, { useState } from 'react';
import Layout, { Page } from './components/Layout';
import RouteAnnouncer from './components/RouteAnnouncer';
import DashboardPage from './pages/Dashboard';
import AnalyzePage from './pages/Analyze';
import KnowledgeBasePage from './pages/KnowledgeBase';
import HistoryPage from './pages/History';
import { AuthProvider } from './contexts/AuthContext';

export default function App() {
  const [page, setPage] = useState<Page>('analyze');

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
