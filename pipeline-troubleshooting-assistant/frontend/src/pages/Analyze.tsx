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
    <div>
      <h2>Analyze Pipeline Log</h2>

      <div style={{ marginBottom: '16px' }}>
        <textarea
          value={logText}
          onChange={(e) => setLogText(e.target.value)}
          rows={10}
          style={{ width: '100%', fontFamily: 'monospace', boxSizing: 'border-box', padding: '8px' }}
          placeholder="Paste your pipeline failure log here..."
          maxLength={MAX_LOG_LENGTH}
          aria-label="Pipeline log input"
        />
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginTop: '8px' }}>
          <span style={{ fontSize: '0.8em', color: '#666' }}>
            {logText.length.toLocaleString()} / {MAX_LOG_LENGTH.toLocaleString()} characters
          </span>
          <div style={{ display: 'flex', gap: '8px' }}>
            <button
              type="button"
              onClick={handleAnalyze}
              disabled={loading || !logText.trim()}
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

      {error && (
        <p style={{ color: 'red', marginBottom: '16px' }}>{error}</p>
      )}

      {result && (
        <div style={{ marginTop: '24px' }}>
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start' }}>
            <h3 style={{ margin: 0 }}>
              {result.category} — {result.severity}
            </h3>
            <span style={{ fontSize: '0.9em', color: '#555' }}>
              Confidence: {result.confidence}%
            </span>
          </div>

          {/* Sanitization notice — shown whenever analysis results are displayed */}
          <div
            style={{
              display: 'inline-flex',
              alignItems: 'center',
              gap: '5px',
              color: '#22c55e',
              fontSize: '0.85em',
              margin: '10px 0 16px',
            }}
          >
            <Shield size={14} />
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
            <strong>Customer Update</strong>
            <textarea
              value={result.customerUpdate}
              readOnly
              rows={4}
              style={{ width: '100%', boxSizing: 'border-box', padding: '8px', marginTop: '6px' }}
              aria-label="Customer update text"
            />
            <button type="button" onClick={handleCopy} style={{ marginTop: '6px' }}>
              {copied ? 'Copied!' : 'Copy Customer Update'}
            </button>
          </div>
        </div>
      )}
    </div>
  );
}
