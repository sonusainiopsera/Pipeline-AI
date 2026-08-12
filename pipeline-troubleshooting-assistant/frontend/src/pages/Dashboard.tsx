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
        <div className="dashboard-grid">
          <div
            style={{
              padding: '20px',
              borderRadius: '8px',
              // #64748b border on #fff: ~4.2:1 — passes 3:1 UI boundary ✓
              border: '1px solid #64748b',
              backgroundColor: '#fff',
            }}
          >
            <h2 style={{
              margin: '0 0 8px',
              fontSize: '0.95rem',
              // #475569 on #fff: ~7.6:1 — passes 4.5:1 ✓
              color: '#475569',
              fontWeight: 500,
            }}>
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
              border: '1px solid #64748b',
              backgroundColor: '#fff',
            }}
          >
            <h2 style={{ margin: '0 0 8px', fontSize: '0.95rem', color: '#475569', fontWeight: 500 }}>
              Getting Started
            </h2>
            {/* #475569 on #fff: ~7.6:1 — passes 4.5:1 ✓ */}
            <p style={{ margin: 0, fontSize: '0.9em', color: '#475569' }}>
              Paste a pipeline failure log in the Analyze page to get started.
            </p>
          </div>
        </div>
      )}
    </div>
  );
}
