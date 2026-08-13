import React from 'react';

interface PageHeaderProps {
  title: string;
  description?: string;
}

export function PageHeader({ title, description }: PageHeaderProps) {
  return (
    <div style={{ marginBottom: '24px' }}>
      <h1 style={{ margin: '0 0 4px', fontSize: '1.5rem', color: 'var(--color-text-primary)' }}>{title}</h1>
      {description && (
        <p style={{ margin: 0, color: 'var(--color-text-secondary)', fontSize: '0.9em' }}>{description}</p>
      )}
    </div>
  );
}

export function Loading({ message = 'Loading...' }: { message?: string }) {
  return (
    <p role="status" aria-live="polite" style={{ color: 'var(--color-text-secondary)' }}>
      {message}
    </p>
  );
}

export function ErrorDisplay({ message }: { message: string }) {
  return (
    <p role="alert" style={{ color: 'var(--color-text-error)' }}>
      Error: {message}
    </p>
  );
}

export function SkeletonLoader({ rows = 5 }: { rows?: number }) {
  return (
    <div role="status" aria-busy="true" aria-label="Loading history">
      {Array.from({ length: rows }, (_, i) => (
        <div
          key={i}
          style={{
            padding: '12px 0',
            borderBottom: '1px solid #e2e8f0',
            display: 'flex',
            gap: '12px',
            alignItems: 'center',
            flexWrap: 'wrap',
          }}
        >
          <div className="skeleton-item" style={{ height: '14px', width: '130px' }} />
          <div className="skeleton-item" style={{ height: '20px', width: '70px', borderRadius: '10px' }} />
          <div className="skeleton-item" style={{ height: '14px', width: '110px' }} />
          <div className="skeleton-item" style={{ height: '14px', flex: 1, minWidth: '100px' }} />
        </div>
      ))}
    </div>
  );
}
