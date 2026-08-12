import { describe, it, expect } from 'vitest';
import React from 'react';
import {
  detectPotentialSecrets,
  highlightRedactions,
  REDACTION_PLACEHOLDER_REGEX,
} from './sanitization';

// ── detectPotentialSecrets ────────────────────────────────────────────────────

describe('detectPotentialSecrets', () => {
  it('returns true for text containing an AWS access key', () => {
    expect(detectPotentialSecrets('AKIAIOSFODNN7EXAMPLE accessed denied')).toBe(true);
  });

  it('returns true for text containing a Bearer token', () => {
    expect(detectPotentialSecrets('Authorization: Bearer eyJhbGciOiJIUzI1NiJ9.test')).toBe(true);
  });

  it('returns true for text containing a password assignment', () => {
    expect(detectPotentialSecrets('connection failed: password=SuperSecret123')).toBe(true);
  });

  it('returns true for text containing a passwd assignment', () => {
    expect(detectPotentialSecrets('db connect passwd=MyPass1!')).toBe(true);
  });

  it('returns true for text containing a secret assignment', () => {
    expect(detectPotentialSecrets('secret=abc123xyz')).toBe(true);
  });

  it('returns true for password= with uppercase letters (case-insensitive)', () => {
    expect(detectPotentialSecrets('PASSWORD=Hunter2')).toBe(true);
  });

  it('returns false for clean pipeline log text', () => {
    expect(detectPotentialSecrets('Pipeline build failed: docker daemon not started')).toBe(false);
  });

  it('returns false for empty string', () => {
    expect(detectPotentialSecrets('')).toBe(false);
  });

  it('returns false for text containing only redaction placeholders', () => {
    expect(detectPotentialSecrets('[AWS_KEY_REDACTED] and [PASSWORD_REDACTED]')).toBe(false);
  });

  it('returns false for generic square-bracket log annotations like [INFO]', () => {
    expect(detectPotentialSecrets('[INFO] build started [2025-01-01]')).toBe(false);
  });

  it('returns false for text containing the word "password" without an assignment', () => {
    expect(detectPotentialSecrets('please check your password policy')).toBe(false);
  });
});

// ── REDACTION_PLACEHOLDER_REGEX ───────────────────────────────────────────────

describe('REDACTION_PLACEHOLDER_REGEX', () => {
  it('matches [AWS_KEY_REDACTED]', () => {
    expect(REDACTION_PLACEHOLDER_REGEX.test('[AWS_KEY_REDACTED]')).toBe(true);
  });

  it('matches [BEARER_TOKEN_REDACTED]', () => {
    expect(REDACTION_PLACEHOLDER_REGEX.test('[BEARER_TOKEN_REDACTED]')).toBe(true);
  });

  it('matches [PASSWORD_REDACTED]', () => {
    expect(REDACTION_PLACEHOLDER_REGEX.test('[PASSWORD_REDACTED]')).toBe(true);
  });

  it('does not match [INFO] — too short, no _REDACTED suffix', () => {
    expect(REDACTION_PLACEHOLDER_REGEX.test('[INFO]')).toBe(false);
  });

  it('does not match log timestamps like [2025-01-01]', () => {
    expect(REDACTION_PLACEHOLDER_REGEX.test('[2025-01-01]')).toBe(false);
  });

  it('does not match lowercase content like [password]', () => {
    expect(REDACTION_PLACEHOLDER_REGEX.test('[password]')).toBe(false);
  });

  it('matches a placeholder embedded within surrounding text', () => {
    expect(
      REDACTION_PLACEHOLDER_REGEX.test('Pipeline log: AKIA [AWS_KEY_REDACTED] failed')
    ).toBe(true);
  });
});

// ── highlightRedactions ───────────────────────────────────────────────────────

describe('highlightRedactions', () => {
  it('returns array with single plain string when no placeholders present', () => {
    const nodes = highlightRedactions('plain text no placeholders');
    expect(nodes).toHaveLength(1);
    expect(nodes[0]).toBe('plain text no placeholders');
  });

  it('returns a styled span element for a single redaction placeholder', () => {
    const nodes = highlightRedactions('[AWS_KEY_REDACTED]');
    expect(nodes).toHaveLength(1);
    const node = nodes[0] as React.ReactElement;
    expect(node.type).toBe('span');
    expect(node.props.children).toBe('[AWS_KEY_REDACTED]');
  });

  it('splits text correctly around a single placeholder', () => {
    const nodes = highlightRedactions('before [AWS_KEY_REDACTED] after');
    expect(nodes).toHaveLength(3);
    expect(nodes[0]).toBe('before ');
    const middle = nodes[1] as React.ReactElement;
    expect(middle.type).toBe('span');
    expect(middle.props.children).toBe('[AWS_KEY_REDACTED]');
    expect(nodes[2]).toBe(' after');
  });

  it('handles multiple redaction placeholders in one string', () => {
    const nodes = highlightRedactions('[AWS_KEY_REDACTED] and [PASSWORD_REDACTED]');
    const spans = nodes.filter((n): n is React.ReactElement => typeof n !== 'string');
    expect(spans).toHaveLength(2);
    expect(spans[0].props.children).toBe('[AWS_KEY_REDACTED]');
    expect(spans[1].props.children).toBe('[PASSWORD_REDACTED]');
  });

  it('wraps placeholder spans with a defined style containing a background color', () => {
    const nodes = highlightRedactions('[BEARER_TOKEN_REDACTED]');
    const node = nodes[0] as React.ReactElement;
    expect(node.props.style).toBeDefined();
    expect(node.props.style.backgroundColor).toBeDefined();
    expect(node.props.style.fontWeight).toBe(700);
  });

  it('preserves surrounding text when placeholder appears at the start', () => {
    const nodes = highlightRedactions('[PASSWORD_REDACTED] connection refused');
    expect(nodes[nodes.length - 1]).toBe(' connection refused');
  });

  it('preserves surrounding text when placeholder appears at the end', () => {
    const nodes = highlightRedactions('connecting with key=[AWS_KEY_REDACTED]');
    expect(nodes[0]).toBe('connecting with key=');
  });

  it('handles empty string input', () => {
    const nodes = highlightRedactions('');
    expect(nodes).toHaveLength(1);
    expect(nodes[0]).toBe('');
  });

  it('handles text that is entirely a single redaction placeholder with no surrounding text', () => {
    const nodes = highlightRedactions('[BEARER_TOKEN_REDACTED]');
    expect(nodes).toHaveLength(1);
    const node = nodes[0] as React.ReactElement;
    expect(node.type).toBe('span');
  });
});
