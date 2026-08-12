import React from 'react';

interface PageHeaderProps {
  title: string;
  description?: string;
}

export function PageHeader({ title, description }: PageHeaderProps) {
  return (
    <div style={{ marginBottom: '24px' }}>
      <h1 style={{ margin: '0 0 4px', fontSize: '1.5rem' }}>{title}</h1>
      {description && (
        // #475569 on #f8fafc: ~6.8:1 — passes 4.5:1 ✓
        <p style={{ margin: 0, color: '#475569', fontSize: '0.9em' }}>{description}</p>
      )}
    </div>
  );
}

export function Loading({ message = 'Loading...' }: { message?: string }) {
  return (
    // #475569 on #f8fafc: ~6.8:1 — passes 4.5:1 ✓
    <p role="status" aria-live="polite" style={{ color: '#475569' }}>
      {message}
    </p>
  );
}

export function ErrorDisplay({ message }: { message: string }) {
  return (
    // #b91c1c on #f8fafc: ~6.2:1 — passes 4.5:1 ✓
    <p role="alert" style={{ color: '#b91c1c' }}>
      Error: {message}
    </p>
  );
}
