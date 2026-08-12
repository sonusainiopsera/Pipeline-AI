import React, { useState } from 'react';
import Layout, { Page } from './components/Layout';
import DashboardPage from './pages/Dashboard';
import AnalyzePage from './pages/Analyze';
import KnowledgeBasePage from './pages/KnowledgeBase';
import HistoryPage from './pages/History';

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
    <Layout page={page} onNavigate={setPage}>
      {renderPage()}
    </Layout>
  );
}
