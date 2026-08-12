import React, { useEffect, useState } from 'react';
import { getHistory, AnalyzedLog } from '../api';
import { SanitizedLogDisplay } from '../components/SanitizedLogDisplay';

export default function HistoryPage() {
  const [logs, setLogs] = useState<AnalyzedLog[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [expandedId, setExpandedId] = useState<number | null>(null);

  useEffect(() => {
    getHistory()
      .then(setLogs)
      .catch((err: Error) => setError(err.message))
      .finally(() => setLoading(false));
  }, []);

  if (loading) return <p>Loading history...</p>;
  if (error) return <p style={{ color: 'red' }}>Error: {error}</p>;
  if (logs.length === 0) return <p>No analysis history yet. Analyze your first pipeline log to get started.</p>;

  return (
    <div>
      <h2>Analysis History</h2>
      <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: '0.9em' }}>
        <thead>
          <tr style={{ textAlign: 'left', borderBottom: '2px solid #ddd' }}>
            <th style={{ padding: '8px' }}>Date</th>
            <th style={{ padding: '8px' }}>Category</th>
            <th style={{ padding: '8px' }}>Severity</th>
            <th style={{ padding: '8px' }}>Confidence</th>
            <th style={{ padding: '8px' }}>Actions</th>
          </tr>
        </thead>
        <tbody>
          {logs.map((log) => (
            <React.Fragment key={log.id}>
              <tr style={{ borderBottom: '1px solid #eee' }}>
                <td style={{ padding: '8px' }}>{new Date(log.createdAt).toLocaleString()}</td>
                <td style={{ padding: '8px' }}>{log.category}</td>
                <td style={{ padding: '8px' }}>{log.severity}</td>
                <td style={{ padding: '8px' }}>{log.confidence}%</td>
                <td style={{ padding: '8px' }}>
                  <button
                    onClick={() =>
                      setExpandedId(expandedId === log.id ? null : log.id)
                    }
                  >
                    {expandedId === log.id ? 'Collapse' : 'View Details'}
                  </button>
                </td>
              </tr>
              {expandedId === log.id && (
                <tr>
                  <td colSpan={5} style={{ padding: '16px', backgroundColor: '#f9f9f9' }}>
                    <div style={{ marginBottom: '12px' }}>
                      <strong>Root Cause:</strong>
                      <p style={{ margin: '4px 0' }}>{log.rootCause}</p>
                    </div>
                    <div style={{ marginBottom: '12px' }}>
                      <strong>Suggested Fix:</strong>
                      <p style={{ margin: '4px 0' }}>{log.suggestedFix}</p>
                    </div>
                    <div>
                      <strong>Log Text:</strong>
                      <div style={{ marginTop: '8px' }}>
                        <SanitizedLogDisplay logText={log.logText} />
                      </div>
                    </div>
                  </td>
                </tr>
              )}
            </React.Fragment>
          ))}
        </tbody>
      </table>
    </div>
  );
}
