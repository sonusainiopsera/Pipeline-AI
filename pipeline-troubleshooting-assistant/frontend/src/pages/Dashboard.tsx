import React, { useEffect, useState } from 'react';
import { getHistory } from '../api';
import { PageHeader, Loading, ErrorDisplay } from '../components/Common';

export default function DashboardPage() {
  const [totalAnalyses, setTotalAnalyses] = useState<number | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    getHistory()
      .then((logs) => setTotalAnalyses(logs.length))
      .catch((err: Error) => setError(err.message))
      .finally(() => setLoading(false));
  }, []);

  return (
    <div>
      <PageHeader
        title="Dashboard"
        description="Overview of your pipeline analysis activity"
      />

      {loading && <Loading message="Loading dashboard..." />}
      {error && <ErrorDisplay message={error} />}

      {!loading && !error && (
        <div
          style={{
            display: 'grid',
            gridTemplateColumns: 'repeat(auto-fit, minmax(180px, 1fr))',
            gap: '16px',
          }}
        >
          <div
            style={{
              padding: '20px',
              borderRadius: '8px',
              border: '1px solid #e2e8f0',
              backgroundColor: '#fff',
            }}
          >
            <h2 style={{ margin: '0 0 8px', fontSize: '0.95rem', color: '#64748b', fontWeight: 500 }}>
              Total Analyses
            </h2>
            <p style={{ margin: 0, fontSize: '2rem', fontWeight: 700 }}>
              {totalAnalyses ?? '—'}
            </p>
          </div>

          <div
            style={{
              padding: '20px',
              borderRadius: '8px',
              border: '1px solid #e2e8f0',
              backgroundColor: '#fff',
            }}
          >
            <h2 style={{ margin: '0 0 8px', fontSize: '0.95rem', color: '#64748b', fontWeight: 500 }}>
              Getting Started
            </h2>
            <p style={{ margin: 0, fontSize: '0.9em', color: '#475569' }}>
              Paste a pipeline failure log in the Analyze page to get started.
            </p>
          </div>
        </div>
      )}
    </div>
  );
}
