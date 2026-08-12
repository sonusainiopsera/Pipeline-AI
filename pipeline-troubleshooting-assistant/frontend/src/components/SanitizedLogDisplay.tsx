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
            // #15803d on #f1f5f9 (History expanded row bg): ~5.3:1 — passes 4.5:1 ✓
            color: '#15803d',
            fontSize: '0.8em',
            marginBottom: '6px',
          }}
          title="This log has been sanitized — sensitive data has been redacted before storage"
        >
          <Shield size={14} aria-hidden="true" />
          <span>Log sanitized</span>
        </div>
      )}
      {hasPotentialSecrets && (
        <div
          role="alert"
          style={{
            backgroundColor: 'rgba(185, 28, 28, 0.08)',
            // #b91c1c border: ~6.5:1 against white — passes 3:1 UI boundary ✓
            border: '1px solid rgba(185, 28, 28, 0.5)',
            borderRadius: '6px',
            padding: '8px 12px',
            display: 'flex',
            alignItems: 'flex-start',
            gap: '8px',
            // #b91c1c on near-white bg (~L 0.9): ~6.2:1 — passes 4.5:1 ✓
            color: '#b91c1c',
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
