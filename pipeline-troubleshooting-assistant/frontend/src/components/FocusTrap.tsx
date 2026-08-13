import React, { useEffect, useRef } from 'react';

const FOCUSABLE_SELECTORS = [
  'a[href]',
  'button:not([disabled])',
  'input:not([disabled])',
  'select:not([disabled])',
  'textarea:not([disabled])',
  '[tabindex]:not([tabindex="-1"])',
].join(', ');

interface FocusTrapProps {
  active: boolean;
  onEscape: () => void;
  children: React.ReactNode;
}

/**
 * Constrains Tab cycling to focusable descendants when active.
 * Calls onEscape when the Escape key is pressed.
 * Focuses the first focusable child only when the trap first becomes active
 * (not on every parent re-render).
 */
export default function FocusTrap({ active, onEscape, children }: FocusTrapProps) {
  const containerRef = useRef<HTMLDivElement>(null);
  const onEscapeRef = useRef(onEscape);
  const wasActiveRef = useRef(false);

  onEscapeRef.current = onEscape;

  useEffect(() => {
    if (!active || !containerRef.current) {
      wasActiveRef.current = active;
      return;
    }

    const container = containerRef.current;
    const getFocusable = () =>
      Array.from(container.querySelectorAll<HTMLElement>(FOCUSABLE_SELECTORS));

    // Only auto-focus when the trap transitions from inactive → active.
    // Re-running this on every parent render (e.g. form keystrokes) steals focus.
    if (!wasActiveRef.current) {
      const focusable = getFocusable();
      if (focusable.length > 0) {
        focusable[0].focus();
      }
    }
    wasActiveRef.current = true;

    const handleKeyDown = (e: KeyboardEvent) => {
      if (e.key === 'Escape') {
        e.preventDefault();
        onEscapeRef.current();
        return;
      }
      if (e.key !== 'Tab') return;

      const current = getFocusable();
      if (current.length === 0) return;

      const first = current[0];
      const last = current[current.length - 1];

      if (e.shiftKey) {
        if (document.activeElement === first) {
          e.preventDefault();
          last.focus();
        }
      } else if (document.activeElement === last) {
        e.preventDefault();
        first.focus();
      }
    };

    document.addEventListener('keydown', handleKeyDown);
    return () => document.removeEventListener('keydown', handleKeyDown);
  }, [active]);

  useEffect(() => {
    if (!active) {
      wasActiveRef.current = false;
    }
  }, [active]);

  return <div ref={containerRef}>{children}</div>;
}
