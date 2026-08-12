import React from 'react';

/**
 * Matches backend redaction placeholders like [AWS_KEY_REDACTED], [BEARER_TOKEN_REDACTED].
 * The pattern requires an uppercase letter start, uppercase letters/digits/underscores,
 * and must end with _REDACTED — avoids matching generic log tokens like [INFO] or [2025-01-01].
 */
export const REDACTION_PLACEHOLDER_REGEX = /\[[A-Z][A-Z0-9_]*_REDACTED\]/;

const AWS_KEY_RE = /AKIA[0-9A-Z]{16}/;
const BEARER_TOKEN_RE = /Bearer\s+[A-Za-z0-9\-_.]+/;
const PASSWORD_RE = /(password|passwd|secret)=\S+/i;

/**
 * Returns true if the text contains patterns that look like unredacted secrets.
 * Used as a defense-in-depth check on displayed log text — the backend LogSanitizer
 * is the authoritative sanitizer.
 */
export function detectPotentialSecrets(text: string): boolean {
  if (!text) return false;
  return AWS_KEY_RE.test(text) || BEARER_TOKEN_RE.test(text) || PASSWORD_RE.test(text);
}

/**
 * Splits text on redaction placeholders and returns an array of React nodes:
 * plain strings for normal segments and styled <span> elements for placeholders.
 * Safe: uses React.createElement — never dangerouslySetInnerHTML.
 */
export function highlightRedactions(text: string): React.ReactNode[] {
  const globalRe = new RegExp(REDACTION_PLACEHOLDER_REGEX.source, 'g');
  const parts: React.ReactNode[] = [];
  let lastIndex = 0;
  let match: RegExpExecArray | null;

  while ((match = globalRe.exec(text)) !== null) {
    if (match.index > lastIndex) {
      parts.push(text.slice(lastIndex, match.index));
    }
    parts.push(
      React.createElement(
        'span',
        {
          key: `redact-${match.index}`,
          style: {
            // #6b21a8 (violet-800) on light-lavender bg: ~5.9:1 — passes 4.5:1 ✓
            // rgba(107,33,168,0.1) over near-white (~L 0.78) bg gives L≈0.66 effective
            backgroundColor: 'rgba(107, 33, 168, 0.1)',
            color: '#6b21a8',
            borderRadius: '3px',
            padding: '1px 5px',
            fontWeight: 700,
            fontFamily: 'monospace',
            fontSize: '0.85em',
          },
          'aria-label': `Redacted value: ${match[0]}`,
        },
        match[0],
      ),
    );
    lastIndex = globalRe.lastIndex;
  }

  if (lastIndex < text.length) {
    parts.push(text.slice(lastIndex));
  }

  return parts.length > 0 ? parts : [text];
}
