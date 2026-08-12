import React, { useEffect, useState } from 'react';
import { history, historyDetail, HistoryListItem, HistoryItem, PageResponse } from '../api';
import { SkeletonLoader } from '../components/Common';
import { SanitizedLogDisplay } from '../components/SanitizedLogDisplay';

const PAGE_SIZE = 20;

function severityStyle(severity: string): React.CSSProperties {
  switch (severity?.toLowerCase()) {
    case 'critical': return { backgroundColor: '#fef2f2', color: '#b91c1c', border: '1px solid #fca5a5' };
    case 'high':     return { backgroundColor: '#fff7ed', color: '#c2410c', border: '1px solid #fdba74' };
    case 'medium':   return { backgroundColor: '#fefce8', color: '#a16207', border: '1px solid #fde047' };
    case 'low':      return { backgroundColor: '#f0fdf4', color: '#15803d', border: '1px solid #86efac' };
    default:         return { backgroundColor: '#f8fafc', color: '#475569', border: '1px solid #cbd5e1' };
  }
}

export default function HistoryPage() {
  const [currentPage, setCurrentPage] = useState(0);
  const [pageData, setPageData] = useState<PageResponse<HistoryListItem> | null>(null);
  const [expandedId, setExpandedId] = useState<number | null>(null);
  const [expandedDetail, setExpandedDetail] = useState<HistoryItem | null>(null);
  const [loading, setLoading] = useState(false);
  const [detailLoading, setDetailLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [detailError, setDetailError] = useState<string | null>(null);
  const [copied, setCopied] = useState(false);

  const fetchPage = async (page: number) => {
    setLoading(true);
    setError(null);
    try {
      const data = await history(page, PAGE_SIZE);
      setPageData(data);
      setExpandedId(null);
      setExpandedDetail(null);
      setDetailError(null);
    } catch {
      setError('Unable to load history. Please try again.');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    void fetchPage(currentPage);
  }, [currentPage]); // eslint-disable-line react-hooks/exhaustive-deps

  const loadDetail = async (id: number) => {
    setDetailLoading(true);
    setDetailError(null);
    setExpandedDetail(null);
    try {
      const detail = await historyDetail(id);
      setExpandedDetail(detail);
    } catch {
      setDetailError('Unable to load details. Click to retry.');
    } finally {
      setDetailLoading(false);
    }
  };

  const handleAccordionClick = (id: number) => {
    if (expandedId === id) {
      setExpandedId(null);
      setExpandedDetail(null);
      setDetailError(null);
      return;
    }
    setExpandedId(id);
    void loadDetail(id);
  };

  const handleCopy = (text: string) => {
    navigator.clipboard.writeText(text).then(() => {
      setCopied(true);
      setTimeout(() => setCopied(false), 2000);
    });
  };

  // ── Initial load ──────────────────────────────────────────────────────────

  if (loading && pageData === null) {
    return (
      <div>
        <h2>Analysis History</h2>
        <SkeletonLoader rows={5} />
      </div>
    );
  }

  if (error && pageData === null) {
    return (
      <div>
        <h2>Analysis History</h2>
        {/* #b91c1c on #f8fafc: ~6.2:1 — passes 4.5:1 ✓ */}
        <p role="alert" style={{ color: '#b91c1c', marginBottom: '12px' }}>
          Unable to load history. Please try again.
        </p>
        <button type="button" onClick={() => fetchPage(currentPage)}>
          Retry
        </button>
      </div>
    );
  }

  if (pageData?.empty) {
    return (
      <div>
        <h2>Analysis History</h2>
        <p>No analysis history yet. Analyze your first pipeline log to get started.</p>
      </div>
    );
  }

  // ── List + pagination ─────────────────────────────────────────────────────

  return (
    <div>
      <h2>Analysis History</h2>

      {/* Page transition error */}
      {error && (
        <p role="alert" style={{ color: '#b91c1c', marginBottom: '12px' }}>
          Unable to load history. Please try again.{' '}
          <button
            type="button"
            onClick={() => fetchPage(currentPage)}
            style={{ marginLeft: '8px' }}
          >
            Retry
          </button>
        </p>
      )}

      {/* Item list — dimmed during page transitions */}
      <div
        style={{ opacity: loading ? 0.6 : 1, transition: 'opacity 0.2s' }}
        aria-busy={loading}
      >
        {pageData?.content.map((item) => (
          <div
            key={item.id}
            style={{
              marginBottom: '8px',
              border: '1px solid #64748b',
              borderRadius: '6px',
              overflow: 'hidden',
            }}
          >
            {/* Accordion header */}
            <button
              type="button"
              aria-expanded={expandedId === item.id}
              aria-controls={`history-detail-${item.id}`}
              onClick={() => handleAccordionClick(item.id)}
              style={{
                width: '100%',
                display: 'flex',
                gap: '12px',
                alignItems: 'center',
                flexWrap: 'wrap',
                padding: '12px',
                background: 'none',
                border: 'none',
                textAlign: 'left',
                cursor: 'pointer',
                fontSize: 'inherit',
              }}
            >
              {/* #475569 on #f8fafc: ~6.8:1 — passes 4.5:1 ✓ */}
              <span style={{ fontSize: '0.8em', color: '#475569', minWidth: '140px' }}>
                {new Date(item.createdAt).toLocaleString()}
              </span>
              <span
                style={{
                  fontSize: '0.78em',
                  padding: '2px 8px',
                  borderRadius: '10px',
                  fontWeight: 600,
                  ...severityStyle(item.severity),
                }}
              >
                {item.severity}
              </span>
              <span style={{ fontWeight: 600 }}>
                {item.detectedCategory}
              </span>
              <span style={{ color: '#475569', fontSize: '0.85em' }}>
                {item.confidence}% confidence
              </span>
              <span
                style={{
                  flex: 1,
                  color: '#475569',
                  fontSize: '0.9em',
                  overflow: 'hidden',
                  textOverflow: 'ellipsis',
                  whiteSpace: 'nowrap',
                  minWidth: 0,
                }}
              >
                {item.rootCause}
              </span>
              <span style={{ color: '#475569', fontSize: '0.85em' }} aria-hidden="true">
                {expandedId === item.id ? '▲' : '▼'}
              </span>
            </button>

            {/* Expanded detail panel */}
            {expandedId === item.id && (
              <div
                id={`history-detail-${item.id}`}
                role="region"
                aria-label={`Detail for ${item.detectedCategory}`}
                style={{
                  padding: '16px',
                  borderTop: '1px solid #e2e8f0',
                  // #111827 on #f8fafc: ~18:1 — passes 4.5:1 ✓
                  backgroundColor: '#f8fafc',
                }}
              >
                {detailLoading && (
                  <p role="status" style={{ color: '#475569' }}>Loading details…</p>
                )}

                {detailError && (
                  <p role="alert" style={{ color: '#b91c1c' }}>
                    {detailError}
                    <button
                      type="button"
                      onClick={() => loadDetail(item.id)}
                      style={{ marginLeft: '8px' }}
                    >
                      Retry
                    </button>
                  </p>
                )}

                {expandedDetail && (
                  <>
                    <div style={{ marginBottom: '12px' }}>
                      <strong>Root Cause:</strong>
                      <p style={{ margin: '4px 0' }}>{expandedDetail.rootCause}</p>
                    </div>

                    <div style={{ marginBottom: '12px' }}>
                      <strong>Suggested Fix:</strong>
                      <p style={{ margin: '4px 0' }}>{expandedDetail.suggestedFix}</p>
                    </div>

                    <div style={{ marginBottom: '12px' }}>
                      <label
                        htmlFor={`customer-update-${item.id}`}
                        style={{ display: 'block', fontWeight: 600, marginBottom: '6px' }}
                      >
                        Customer Update
                      </label>
                      <textarea
                        id={`customer-update-${item.id}`}
                        value={expandedDetail.customerUpdate}
                        readOnly
                        rows={3}
                        style={{
                          width: '100%',
                          boxSizing: 'border-box',
                          padding: '8px',
                          // #64748b border: ~4.0:1 — passes 3:1 UI boundary ✓
                          border: '1px solid #64748b',
                        }}
                      />
                      <button
                        type="button"
                        onClick={() => handleCopy(expandedDetail.customerUpdate)}
                        style={{ marginTop: '6px' }}
                      >
                        {copied ? 'Copied!' : 'Copy Customer Update'}
                      </button>
                    </div>

                    <div>
                      <strong>Log Text:</strong>
                      <div style={{ marginTop: '8px', overflowX: 'auto' }}>
                        {expandedDetail.logText != null ? (
                          <SanitizedLogDisplay logText={expandedDetail.logText} />
                        ) : (
                          <p style={{ color: '#475569', fontStyle: 'italic' }}>
                            No log text available.
                          </p>
                        )}
                      </div>
                    </div>
                  </>
                )}
              </div>
            )}
          </div>
        ))}
      </div>

      {/* Pagination controls */}
      {pageData && pageData.totalPages > 0 && (
        <div
          style={{
            display: 'flex',
            alignItems: 'center',
            gap: '12px',
            marginTop: '16px',
            justifyContent: 'center',
          }}
        >
          <button
            type="button"
            onClick={() => setCurrentPage((p) => p - 1)}
            disabled={pageData.first || loading}
            aria-label="Previous page"
          >
            Previous
          </button>
          {/* aria-live so screen readers announce page changes */}
          <span aria-live="polite" aria-atomic="true">
            Page {pageData.number + 1} of {pageData.totalPages}
          </span>
          <button
            type="button"
            onClick={() => setCurrentPage((p) => p + 1)}
            disabled={pageData.last || loading}
            aria-label="Next page"
          >
            Next
          </button>
        </div>
      )}
    </div>
  );
}
