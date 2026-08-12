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
        <p style={{ margin: 0, color: '#64748b', fontSize: '0.9em' }}>{description}</p>
      )}
    </div>
  );
}

export function Loading({ message = 'Loading...' }: { message?: string }) {
  return (
    <p role="status" aria-live="polite" style={{ color: '#64748b' }}>
      {message}
    </p>
  );
}

export function ErrorDisplay({ message }: { message: string }) {
  return (
    <p role="alert" style={{ color: '#ef4444' }}>
      Error: {message}
    </p>
  );
}
