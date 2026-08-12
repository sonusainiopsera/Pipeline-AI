import React, { useEffect, useState } from 'react';
import { ChevronDown, ChevronRight, RefreshCw, Search } from 'lucide-react';
import type { AuditLogEntry, AuditLogFilters } from '../api';
import { getAuditLogs } from '../api';
import { PageHeader, Loading, ErrorDisplay } from '../components/Common';

const ACTIONS = ['CREATE', 'UPDATE', 'DELETE', 'LOGIN', 'LOGOUT', 'ANALYZE', 'LOGIN_FAILED', 'PURGE'];
const RESOURCE_TYPES = ['KNOWLEDGE_BASE', 'AUTH', 'ANALYZED_LOG', 'USER', 'SESSION', 'AUDIT_LOG'];

const INPUT_STYLE: React.CSSProperties = {
  padding: '7px 10px',
  border: '1px solid #64748b',
  borderRadius: '4px',
  fontSize: '0.9em',
  backgroundColor: '#fff',
  color: '#1e293b',
};

const SELECT_STYLE: React.CSSProperties = {
  ...INPUT_STYLE,
  minWidth: '140px',
};

function formatDate(iso: string): string {
  if (!iso) return '—';
  const d = new Date(iso);
  return d.toLocaleString();
}

function truncateDetails(details: Record<string, unknown> | null): string {
  if (details == null) return 'N/A';
  try {
    const str = JSON.stringify(details);
    return str.length > 80 ? str.slice(0, 80) + '…' : str;
  } catch {
    return 'N/A';
  }
}

function DetailsCell({ details }: { details: Record<string, unknown> | null }) {
  const [expanded, setExpanded] = useState(false);
  if (details == null) {
    return <span style={{ color: '#64748b' }}>N/A</span>;
  }
  const full = JSON.stringify(details, null, 2);
  const short = truncateDetails(details);
  const isTruncated = full.length > 80;
  return (
    <div>
      <button
        type="button"
        onClick={() => setExpanded(v => !v)}
        aria-expanded={expanded}
        style={{
          background: 'none',
          border: 'none',
          cursor: isTruncated ? 'pointer' : 'default',
          padding: 0,
          display: 'flex',
          alignItems: 'center',
          gap: '4px',
          color: '#1e293b',
          fontSize: '0.85em',
          fontFamily: 'monospace',
          textAlign: 'left',
        }}
        disabled={!isTruncated}
        aria-label={isTruncated ? (expanded ? 'Collapse details' : 'Expand details') : undefined}
      >
        {isTruncated && (
          <span style={{ color: '#64748b', flexShrink: 0 }}>
            {expanded ? <ChevronDown size={14} aria-hidden="true" /> : <ChevronRight size={14} aria-hidden="true" />}
          </span>
        )}
        {expanded ? null : <span>{short}</span>}
      </button>
      {expanded && (
        <pre
          style={{
            margin: '6px 0 0 0',
            padding: '8px',
            backgroundColor: '#f1f5f9',
            borderRadius: '4px',
            fontSize: '0.8em',
            overflowX: 'auto',
            maxHeight: '200px',
            color: '#1e293b',
          }}
        >
          {full}
        </pre>
      )}
    </div>
  );
}

export default function AuditLogPage() {
  const [entries, setEntries] = useState<AuditLogEntry[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [totalPages, setTotalPages] = useState(0);
  const [totalElements, setTotalElements] = useState(0);
  const [page, setPage] = useState(0);

  const [filters, setFilters] = useState<AuditLogFilters>({});
  const [pendingFilters, setPendingFilters] = useState<{
    action: string;
    resourceType: string;
    actorEmail: string;
    startDate: string;
    endDate: string;
  }>({ action: '', resourceType: '', actorEmail: '', startDate: '', endDate: '' });

  const fetchLogs = (appliedFilters: AuditLogFilters, pg: number) => {
    setLoading(true);
    setError(null);
    getAuditLogs({ ...appliedFilters, page: pg, size: 20 })
      .then(data => {
        setEntries(data.content);
        setTotalPages(data.totalPages);
        setTotalElements(data.totalElements);
      })
      .catch((err: Error) => setError(err.message))
      .finally(() => setLoading(false));
  };

  useEffect(() => {
    fetchLogs(filters, page);
  }, [filters, page]);

  const handleApplyFilters = () => {
    const applied: AuditLogFilters = {};
    if (pendingFilters.action) applied.action = pendingFilters.action;
    if (pendingFilters.resourceType) applied.resourceType = pendingFilters.resourceType;
    if (pendingFilters.actorEmail.trim()) applied.actorEmail = pendingFilters.actorEmail.trim();
    if (pendingFilters.startDate) applied.startDate = pendingFilters.startDate + ':00';
    if (pendingFilters.endDate) applied.endDate = pendingFilters.endDate + ':00';
    setPage(0);
    setFilters(applied);
  };

  const handleClearFilters = () => {
    setPendingFilters({ action: '', resourceType: '', actorEmail: '', startDate: '', endDate: '' });
    setPage(0);
    setFilters({});
  };

  const hasActiveFilters = Object.keys(filters).length > 0;

  const thStyle: React.CSSProperties = {
    padding: '10px 12px',
    textAlign: 'left',
    fontWeight: 600,
    fontSize: '0.85em',
    color: '#374151',
    borderBottom: '2px solid #e2e8f0',
    whiteSpace: 'nowrap',
  };

  const tdStyle: React.CSSProperties = {
    padding: '10px 12px',
    fontSize: '0.88em',
    color: '#1e293b',
    borderBottom: '1px solid #e2e8f0',
    verticalAlign: 'top',
  };

  return (
    <div>
      <PageHeader
        title="Audit Log"
        subtitle={`Immutable record of all system mutations and authentication events${totalElements > 0 ? ` · ${totalElements.toLocaleString()} entries` : ''}`}
      />

      {/* Filter form */}
      <section
        aria-label="Audit log filters"
        style={{
          backgroundColor: '#fff',
          border: '1px solid #e2e8f0',
          borderRadius: '8px',
          padding: '16px',
          marginBottom: '20px',
        }}
      >
        <div
          style={{
            display: 'flex',
            flexWrap: 'wrap',
            gap: '12px',
            alignItems: 'flex-end',
          }}
        >
          <div>
            <label htmlFor="filter-action" style={{ display: 'block', fontSize: '0.8em', fontWeight: 600, color: '#374151', marginBottom: '4px' }}>
              Action
            </label>
            <select
              id="filter-action"
              value={pendingFilters.action}
              onChange={e => setPendingFilters(f => ({ ...f, action: e.target.value }))}
              style={SELECT_STYLE}
            >
              <option value="">All actions</option>
              {ACTIONS.map(a => <option key={a} value={a}>{a}</option>)}
            </select>
          </div>

          <div>
            <label htmlFor="filter-resource-type" style={{ display: 'block', fontSize: '0.8em', fontWeight: 600, color: '#374151', marginBottom: '4px' }}>
              Resource Type
            </label>
            <select
              id="filter-resource-type"
              value={pendingFilters.resourceType}
              onChange={e => setPendingFilters(f => ({ ...f, resourceType: e.target.value }))}
              style={SELECT_STYLE}
            >
              <option value="">All types</option>
              {RESOURCE_TYPES.map(r => <option key={r} value={r}>{r}</option>)}
            </select>
          </div>

          <div>
            <label htmlFor="filter-actor" style={{ display: 'block', fontSize: '0.8em', fontWeight: 600, color: '#374151', marginBottom: '4px' }}>
              Actor Email
            </label>
            <input
              id="filter-actor"
              type="text"
              placeholder="Partial email match"
              value={pendingFilters.actorEmail}
              onChange={e => setPendingFilters(f => ({ ...f, actorEmail: e.target.value }))}
              style={{ ...INPUT_STYLE, minWidth: '180px' }}
            />
          </div>

          <div>
            <label htmlFor="filter-start-date" style={{ display: 'block', fontSize: '0.8em', fontWeight: 600, color: '#374151', marginBottom: '4px' }}>
              From
            </label>
            <input
              id="filter-start-date"
              type="datetime-local"
              value={pendingFilters.startDate}
              onChange={e => setPendingFilters(f => ({ ...f, startDate: e.target.value }))}
              style={INPUT_STYLE}
            />
          </div>

          <div>
            <label htmlFor="filter-end-date" style={{ display: 'block', fontSize: '0.8em', fontWeight: 600, color: '#374151', marginBottom: '4px' }}>
              To
            </label>
            <input
              id="filter-end-date"
              type="datetime-local"
              value={pendingFilters.endDate}
              onChange={e => setPendingFilters(f => ({ ...f, endDate: e.target.value }))}
              style={INPUT_STYLE}
            />
          </div>

          <div style={{ display: 'flex', gap: '8px' }}>
            <button
              type="button"
              onClick={handleApplyFilters}
              style={{
                display: 'flex',
                alignItems: 'center',
                gap: '6px',
                padding: '8px 16px',
                backgroundColor: '#1d4ed8',
                color: '#fff',
                border: 'none',
                borderRadius: '4px',
                cursor: 'pointer',
                fontWeight: 600,
                fontSize: '0.88em',
              }}
            >
              <Search size={14} aria-hidden="true" />
              Apply
            </button>
            {hasActiveFilters && (
              <button
                type="button"
                onClick={handleClearFilters}
                style={{
                  padding: '8px 14px',
                  backgroundColor: '#f8fafc',
                  color: '#374151',
                  border: '1px solid #64748b',
                  borderRadius: '4px',
                  cursor: 'pointer',
                  fontSize: '0.88em',
                }}
              >
                Clear filters
              </button>
            )}
          </div>
        </div>
      </section>

      {/* Table */}
      {loading ? (
        <div
          role="status"
          aria-label="Loading audit logs"
          style={{
            backgroundColor: '#fff',
            border: '1px solid #e2e8f0',
            borderRadius: '8px',
            padding: '40px',
          }}
        >
          {/* Skeleton rows */}
          {[1, 2, 3, 4, 5].map(i => (
            <div
              key={i}
              style={{
                height: '20px',
                backgroundColor: '#e2e8f0',
                borderRadius: '4px',
                marginBottom: '12px',
                opacity: 1 - i * 0.1,
                animation: 'pulse 1.5s infinite',
              }}
            />
          ))}
          <span style={{ position: 'absolute', width: '1px', height: '1px', overflow: 'hidden', clip: 'rect(0,0,0,0)' }}>
            Loading audit logs…
          </span>
        </div>
      ) : error ? (
        <div
          role="alert"
          style={{
            backgroundColor: '#fff',
            border: '1px solid #e2e8f0',
            borderRadius: '8px',
            padding: '40px',
            textAlign: 'center',
          }}
        >
          <p style={{ color: '#b91c1c', fontWeight: 600, marginBottom: '12px' }}>
            Unable to load audit logs
          </p>
          <p style={{ color: '#64748b', fontSize: '0.9em', marginBottom: '16px' }}>{error}</p>
          <button
            type="button"
            onClick={() => fetchLogs(filters, page)}
            style={{
              display: 'inline-flex',
              alignItems: 'center',
              gap: '6px',
              padding: '8px 16px',
              backgroundColor: '#1d4ed8',
              color: '#fff',
              border: 'none',
              borderRadius: '4px',
              cursor: 'pointer',
              fontWeight: 600,
            }}
          >
            <RefreshCw size={14} aria-hidden="true" />
            Retry
          </button>
        </div>
      ) : entries.length === 0 ? (
        <div
          style={{
            backgroundColor: '#fff',
            border: '1px solid #e2e8f0',
            borderRadius: '8px',
            padding: '60px 40px',
            textAlign: 'center',
          }}
        >
          <p style={{ color: '#64748b', fontWeight: 600, fontSize: '1em', marginBottom: '8px' }}>
            {hasActiveFilters ? 'No entries match your filters' : 'No audit log entries found'}
          </p>
          {hasActiveFilters && (
            <button
              type="button"
              onClick={handleClearFilters}
              style={{
                padding: '8px 16px',
                backgroundColor: '#f8fafc',
                color: '#374151',
                border: '1px solid #64748b',
                borderRadius: '4px',
                cursor: 'pointer',
                marginTop: '8px',
              }}
            >
              Clear filters
            </button>
          )}
        </div>
      ) : (
        <div style={{ backgroundColor: '#fff', border: '1px solid #e2e8f0', borderRadius: '8px', overflow: 'hidden' }}>
          <div className="table-scroll-container" style={{ overflowX: 'auto' }}>
            <table
              aria-label="Audit log entries"
              style={{ width: '100%', borderCollapse: 'collapse', minWidth: '800px' }}
            >
              <thead>
                <tr style={{ backgroundColor: '#f8fafc' }}>
                  <th scope="col" style={thStyle}>Timestamp</th>
                  <th scope="col" style={thStyle}>Actor</th>
                  <th scope="col" style={thStyle}>Action</th>
                  <th scope="col" style={thStyle}>Resource Type</th>
                  <th scope="col" style={thStyle}>Resource ID</th>
                  <th scope="col" style={{ ...thStyle, minWidth: '200px' }}>Details</th>
                  <th scope="col" style={thStyle}>IP Address</th>
                </tr>
              </thead>
              <tbody>
                {entries.map(entry => (
                  <tr key={entry.id} style={{ transition: 'background 0.1s' }}>
                    <td style={tdStyle}>
                      <span style={{ fontFamily: 'monospace', fontSize: '0.85em' }}>
                        {formatDate(entry.createdAt)}
                      </span>
                    </td>
                    <td style={tdStyle}>{entry.actorEmail}</td>
                    <td style={tdStyle}>
                      <span
                        style={{
                          display: 'inline-block',
                          padding: '2px 8px',
                          borderRadius: '12px',
                          fontSize: '0.8em',
                          fontWeight: 600,
                          backgroundColor:
                            entry.action === 'DELETE' ? '#fee2e2' :
                            entry.action === 'CREATE' ? '#dcfce7' :
                            entry.action === 'UPDATE' ? '#dbeafe' : '#f1f5f9',
                          color:
                            entry.action === 'DELETE' ? '#b91c1c' :
                            entry.action === 'CREATE' ? '#15803d' :
                            entry.action === 'UPDATE' ? '#1d4ed8' : '#374151',
                        }}
                      >
                        {entry.action}
                      </span>
                    </td>
                    <td style={tdStyle}>{entry.resourceType}</td>
                    <td style={{ ...tdStyle, fontFamily: 'monospace', fontSize: '0.85em' }}>
                      {entry.resourceId ?? '—'}
                    </td>
                    <td style={tdStyle}>
                      <DetailsCell details={entry.details} />
                    </td>
                    <td style={{ ...tdStyle, fontFamily: 'monospace', fontSize: '0.85em', color: '#64748b' }}>
                      {entry.ipAddress ?? '—'}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>

          {/* Pagination */}
          {totalPages > 1 && (
            <div
              aria-label="Pagination controls"
              style={{
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'space-between',
                padding: '12px 16px',
                borderTop: '1px solid #e2e8f0',
                backgroundColor: '#f8fafc',
              }}
            >
              <button
                type="button"
                onClick={() => setPage(p => Math.max(0, p - 1))}
                disabled={page === 0}
                aria-label="Previous page"
                style={{
                  padding: '6px 14px',
                  border: '1px solid #64748b',
                  borderRadius: '4px',
                  backgroundColor: page === 0 ? '#f8fafc' : '#fff',
                  color: '#374151',
                  cursor: page === 0 ? 'not-allowed' : 'pointer',
                  fontWeight: 500,
                  opacity: page === 0 ? 0.5 : 1,
                }}
              >
                ← Previous
              </button>
              <span style={{ fontSize: '0.88em', color: '#374151' }}>
                Page {page + 1} of {totalPages}
              </span>
              <button
                type="button"
                onClick={() => setPage(p => Math.min(totalPages - 1, p + 1))}
                disabled={page >= totalPages - 1}
                aria-label="Next page"
                style={{
                  padding: '6px 14px',
                  border: '1px solid #64748b',
                  borderRadius: '4px',
                  backgroundColor: page >= totalPages - 1 ? '#f8fafc' : '#fff',
                  color: '#374151',
                  cursor: page >= totalPages - 1 ? 'not-allowed' : 'pointer',
                  fontWeight: 500,
                  opacity: page >= totalPages - 1 ? 0.5 : 1,
                }}
              >
                Next →
              </button>
            </div>
          )}
        </div>
      )}
    </div>
  );
}
