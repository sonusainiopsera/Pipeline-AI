import React from 'react';
import { Shield, AlertTriangle } from 'lucide-react';
import {
  detectPotentialSecrets,
  highlightRedactions,
  REDACTION_PLACEHOLDER_REGEX,
} from '../utils/sanitization';

interface SanitizedLogDisplayProps {
  logText: string;
}

/**
 * Renders sanitized log text with:
 * - Redaction placeholders highlighted in distinct styling
 * - A shield indicator when placeholders are present
 * - A warning banner if potential unsanitized secrets are detected
 * Never uses dangerouslySetInnerHTML — all text goes through React's default escaping.
 */
export function SanitizedLogDisplay({ logText }: SanitizedLogDisplayProps) {
  const hasRedactions = REDACTION_PLACEHOLDER_REGEX.test(logText);
  const hasPotentialSecrets = detectPotentialSecrets(logText);

  let nodes: React.ReactNode[];
  try {
    nodes = highlightRedactions(logText);
  } catch (err) {
    console.warn('SanitizedLogDisplay: failed to highlight redactions', err);
    nodes = [logText];
  }

  return (
    <div>
      {hasRedactions && (
        <div
          style={{
            display: 'inline-flex',
            alignItems: 'center',
            gap: '5px',
            color: '#22c55e',
            fontSize: '0.8em',
            marginBottom: '6px',
          }}
          title="This log has been sanitized — sensitive data has been redacted before storage"
        >
          <Shield size={14} />
          <span>Log sanitized</span>
        </div>
      )}
      {hasPotentialSecrets && (
        <div
          role="alert"
          style={{
            backgroundColor: 'rgba(239, 68, 68, 0.1)',
            border: '1px solid rgba(239, 68, 68, 0.4)',
            borderRadius: '6px',
            padding: '8px 12px',
            display: 'flex',
            alignItems: 'flex-start',
            gap: '8px',
            color: '#ef4444',
            fontSize: '0.85em',
            marginBottom: '8px',
          }}
        >
          <AlertTriangle size={14} style={{ flexShrink: 0, marginTop: '2px' }} />
          <span>
            Warning: This log may contain unsanitized sensitive data. Please report this to your
            administrator.
          </span>
        </div>
      )}
      <pre
        style={{
          whiteSpace: 'pre-wrap',
          wordBreak: 'break-all',
          fontFamily: 'monospace',
          fontSize: '0.85em',
          margin: 0,
          padding: '12px',
          backgroundColor: 'rgba(0,0,0,0.08)',
          borderRadius: '6px',
          maxHeight: '400px',
          overflowY: 'auto',
        }}
      >
        {nodes}
      </pre>
    </div>
  );
}
