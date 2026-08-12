import React, { useState } from 'react';
import AnalyzePage from './pages/Analyze';
import HistoryPage from './pages/History';

type Page = 'analyze' | 'history';

export default function App() {
  const [page, setPage] = useState<Page>('analyze');

  return (
    <div style={{ fontFamily: 'sans-serif', maxWidth: '1100px', margin: '0 auto', padding: '20px' }}>
      <header style={{ marginBottom: '24px' }}>
        <h1 style={{ margin: '0 0 12px' }}>Pipeline Troubleshooting Assistant</h1>
        <nav style={{ display: 'flex', gap: '8px' }}>
          <button
            onClick={() => setPage('analyze')}
            style={{ fontWeight: page === 'analyze' ? 700 : 400 }}
          >
            Analyze
          </button>
          <button
            onClick={() => setPage('history')}
            style={{ fontWeight: page === 'history' ? 700 : 400 }}
          >
            History
          </button>
        </nav>
      </header>
      <main>
        {page === 'analyze' ? <AnalyzePage /> : <HistoryPage />}
      </main>
    </div>
  );
}
