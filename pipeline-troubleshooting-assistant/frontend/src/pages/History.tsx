import React, { useEffect, useState } from 'react';
import { getHistory, AnalyzedLog } from '../api';
import { Loading, ErrorDisplay } from '../components/Common';
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

  if (loading) return <Loading message="Loading history..." />;
  if (error) return <ErrorDisplay message={error} />;
  if (logs.length === 0) {
    return (
      <div>
        <h2>Analysis History</h2>
        {/* Empty state announced when page loads — plain paragraph is sufficient */}
        <p>No analysis history yet. Analyze your first pipeline log to get started.</p>
      </div>
    );
  }

  return (
    <div>
      <h2>Analysis History</h2>
      <div className="table-scroll-container">
        <table
          style={{ width: '100%', borderCollapse: 'collapse', fontSize: '0.9em' }}
          aria-label="Analysis history"
        >
          <thead>
            <tr style={{
              textAlign: 'left',
              // #64748b on #f8fafc: ~4.0:1 — passes 3:1 UI boundary ✓
              borderBottom: '2px solid #64748b',
            }}>
              <th className="col-date" style={{ padding: '8px' }} scope="col">Date</th>
              <th style={{ padding: '8px' }} scope="col">Category</th>
              <th style={{ padding: '8px' }} scope="col">Severity</th>
              <th className="col-confidence" style={{ padding: '8px' }} scope="col">Confidence</th>
              <th style={{ padding: '8px' }} scope="col">Actions</th>
            </tr>
          </thead>
          <tbody>
            {logs.map((log) => (
              <React.Fragment key={log.id}>
                <tr style={{ borderBottom: '1px solid #64748b' }}>
                  <td className="col-date" style={{ padding: '8px' }}>{new Date(log.createdAt).toLocaleString()}</td>
                  <td style={{ padding: '8px' }}>{log.category}</td>
                  <td style={{ padding: '8px' }}>{log.severity}</td>
                  <td className="col-confidence" style={{ padding: '8px' }}>{log.confidence}%</td>
                  <td style={{ padding: '8px' }}>
                    <button
                      type="button"
                      aria-expanded={expandedId === log.id}
                      aria-controls={`history-detail-${log.id}`}
                      onClick={() =>
                        setExpandedId(expandedId === log.id ? null : log.id)
                      }
                    >
                      {expandedId === log.id ? 'Collapse' : 'View Details'}
                    </button>
                  </td>
                </tr>
                {expandedId === log.id && (
                  <tr id={`history-detail-${log.id}`}>
                    <td colSpan={5} style={{ padding: '16px', backgroundColor: '#f1f5f9' }}>
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
    </div>
  );
}
