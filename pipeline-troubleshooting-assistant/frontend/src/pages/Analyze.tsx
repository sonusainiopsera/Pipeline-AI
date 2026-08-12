import React, { useState } from 'react';
import { Shield } from 'lucide-react';
import { analyze, AnalyzedLog } from '../api';

const MAX_LOG_LENGTH = 100_000;

export default function AnalyzePage() {
  const [logText, setLogText] = useState('');
  const [result, setResult] = useState<AnalyzedLog | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [copied, setCopied] = useState(false);

  const handleAnalyze = async () => {
    if (!logText.trim()) return;
    setLoading(true);
    setError(null);
    setResult(null);
    try {
      const res = await analyze(logText);
      setResult(res);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Analysis failed. Please try again.');
    } finally {
      setLoading(false);
    }
  };

  const handleCopy = () => {
    if (result?.customerUpdate) {
      navigator.clipboard.writeText(result.customerUpdate).then(() => {
        setCopied(true);
        setTimeout(() => setCopied(false), 2000);
      });
    }
  };

  return (
    <div className="analyze-layout">
      <h2>Analyze Pipeline Log</h2>

      <div style={{ marginBottom: '16px' }}>
        <label
          htmlFor="log-input"
          style={{ display: 'block', marginBottom: '6px', fontWeight: 500 }}
        >
          Pipeline log input
        </label>
        <textarea
          id="log-input"
          value={logText}
          onChange={(e) => setLogText(e.target.value)}
          rows={10}
          style={{
            width: '100%',
            fontFamily: 'monospace',
            boxSizing: 'border-box',
            padding: '8px',
            // #64748b border on #fff: ~4.2:1 — passes 3:1 UI boundary ✓
            border: '1px solid #64748b',
          }}
          placeholder="Paste your pipeline failure log here..."
          maxLength={MAX_LOG_LENGTH}
        />
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginTop: '8px' }}>
          {/* #475569 on #f8fafc: ~6.8:1 — passes 4.5:1 ✓ */}
          <span style={{ fontSize: '0.8em', color: '#475569' }} aria-live="polite" aria-atomic="true">
            {logText.length.toLocaleString()} / {MAX_LOG_LENGTH.toLocaleString()} characters
          </span>
          <div style={{ display: 'flex', gap: '8px' }}>
            <button
              type="button"
              onClick={handleAnalyze}
              disabled={loading || !logText.trim()}
              aria-busy={loading}
            >
              {loading ? 'Analyzing...' : 'Analyze'}
            </button>
            <button
              type="button"
              onClick={() => {
                setLogText('');
                setResult(null);
                setError(null);
              }}
            >
              Clear
            </button>
          </div>
        </div>
      </div>

      {/* aria-live region for dynamic content — always in DOM so screen readers
          register it before the content changes (ARIA constraint). */}
      <div aria-live="polite" aria-atomic="true">
        {error && (
          // #b91c1c on #f8fafc: ~6.2:1 — passes 4.5:1 ✓
          <p role="alert" style={{ color: '#b91c1c', marginBottom: '16px' }}>{error}</p>
        )}

        {loading && (
          <p role="status" style={{ color: '#475569' }}>Analyzing your pipeline log…</p>
        )}

        {result && (
          <div style={{ marginTop: '24px' }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start' }}>
              <h3 style={{ margin: 0 }}>
                {result.category} — {result.severity}
              </h3>
              {/* #475569 on #f8fafc: ~6.8:1 — passes 4.5:1 ✓ */}
              <span style={{ fontSize: '0.9em', color: '#475569' }}>
                Confidence: {result.confidence}%
              </span>
            </div>

            {/* Sanitization notice — shown whenever analysis results are displayed */}
            <div
              style={{
                display: 'inline-flex',
                alignItems: 'center',
                gap: '5px',
                // #15803d on #f8fafc: ~4.8:1 — passes 4.5:1 ✓
                color: '#15803d',
                fontSize: '0.85em',
                margin: '10px 0 16px',
              }}
            >
              <Shield size={14} aria-hidden="true" />
              <span>Log sanitized before storage</span>
            </div>

            <div style={{ marginBottom: '12px' }}>
              <strong>Root Cause:</strong>
              <p style={{ margin: '4px 0' }}>{result.rootCause}</p>
            </div>

            <div style={{ marginBottom: '12px' }}>
              <strong>Suggested Fix:</strong>
              <p style={{ margin: '4px 0' }}>{result.suggestedFix}</p>
            </div>

            <div>
              <label htmlFor="customer-update" style={{ display: 'block', fontWeight: 600, marginBottom: '6px' }}>
                Customer Update
              </label>
              <textarea
                id="customer-update"
                value={result.customerUpdate}
                readOnly
                rows={4}
                style={{
                  width: '100%',
                  boxSizing: 'border-box',
                  padding: '8px',
                  marginTop: '6px',
                  border: '1px solid #64748b',
                }}
              />
              <button type="button" onClick={handleCopy} style={{ marginTop: '6px' }}>
                {copied ? 'Copied!' : 'Copy Customer Update'}
              </button>
            </div>
          </div>
        )}
      </div>
    </div>
  );
}
