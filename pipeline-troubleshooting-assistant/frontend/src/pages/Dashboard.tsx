import React, { useEffect, useState } from 'react';
import { Database, FileSearch, Target, TrendingUp } from 'lucide-react';
import { getDashboard, ApiError } from '../api';
import type { DashboardData, CategoryStat } from '../api';

// ── Sub-components ─────────────────────────────────────────────────────────────

interface StatCardProps {
  icon: React.ReactNode;
  value: string;
  label: string;
  subtitle?: string;
}

function StatCard({ icon, value, label, subtitle }: StatCardProps) {
  return (
    <div
      style={{
        backgroundColor: '#fff',
        border: '1px solid #e2e8f0',
        borderRadius: '8px',
        padding: '20px 24px',
        display: 'flex',
        flexDirection: 'column',
        gap: '8px',
      }}
    >
      <div style={{ display: 'flex', alignItems: 'center', gap: '8px', color: '#64748b' }}>
        {icon}
        <span style={{ fontSize: '0.8rem', fontWeight: 500, textTransform: 'uppercase', letterSpacing: '0.05em' }}>
          {label}
        </span>
      </div>
      <p style={{ margin: 0, fontSize: '2.2rem', fontWeight: 700, color: '#1e293b', lineHeight: 1 }}>
        {value}
      </p>
      {subtitle && (
        <p style={{ margin: 0, fontSize: '0.8rem', color: '#64748b' }}>{subtitle}</p>
      )}
    </div>
  );
}

function StatCardSkeleton() {
  return (
    <div
      style={{
        backgroundColor: '#fff',
        border: '1px solid #e2e8f0',
        borderRadius: '8px',
        padding: '20px 24px',
        display: 'flex',
        flexDirection: 'column',
        gap: '8px',
      }}
    >
      <div className="skeleton-item" style={{ height: '14px', width: '120px' }} />
      <div className="skeleton-item" style={{ height: '36px', width: '80px' }} />
      <div className="skeleton-item" style={{ height: '12px', width: '100px' }} />
    </div>
  );
}

interface CategoryBarProps {
  stat: CategoryStat;
}

function CategoryBar({ stat }: CategoryBarProps) {
  return (
    <div style={{ display: 'flex', alignItems: 'center', gap: '12px' }}>
      <span
        style={{
          flex: '0 0 160px',
          fontSize: '0.875rem',
          color: '#374151',
          whiteSpace: 'nowrap',
          overflow: 'hidden',
          textOverflow: 'ellipsis',
        }}
        title={stat.category}
      >
        {stat.category}
      </span>
      <div
        style={{
          flex: 1,
          height: '8px',
          backgroundColor: '#e2e8f0',
          borderRadius: '4px',
          overflow: 'hidden',
        }}
      >
        <div
          style={{
            height: '100%',
            width: `${Math.min(stat.percentage, 100)}%`,
            backgroundColor: '#3b82f6',
            borderRadius: '4px',
            transition: 'width 0.3s ease',
          }}
        />
      </div>
      <span style={{ flex: '0 0 80px', fontSize: '0.8rem', color: '#64748b', textAlign: 'right' }}>
        {stat.count} ({stat.percentage.toFixed(1)}%)
      </span>
    </div>
  );
}

function CategoryBarSkeleton() {
  return (
    <div style={{ display: 'flex', alignItems: 'center', gap: '12px' }}>
      <div className="skeleton-item" style={{ flex: '0 0 160px', height: '14px' }} />
      <div className="skeleton-item" style={{ flex: 1, height: '8px', borderRadius: '4px' }} />
      <div className="skeleton-item" style={{ flex: '0 0 80px', height: '14px' }} />
    </div>
  );
}

// ── Page ───────────────────────────────────────────────────────────────────────

export default function DashboardPage() {
  const [data, setData] = useState<DashboardData | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  function fetchData() {
    setLoading(true);
    setError(null);
    getDashboard()
      .then(setData)
      .catch((err: unknown) => {
        const msg = err instanceof ApiError ? err.message : 'Unknown error';
        setError(msg);
      })
      .finally(() => setLoading(false));
  }

  useEffect(() => {
    fetchData();
  }, []);

  // ── Loading state ──────────────────────────────────────────────────────────

  if (loading) {
    return (
      <div>
        <h1 style={headingStyle}>Dashboard</h1>
        <div role="status" aria-busy="true" aria-label="Loading dashboard data">
          <div style={gridStyle}>
            <StatCardSkeleton />
            <StatCardSkeleton />
            <StatCardSkeleton />
            <StatCardSkeleton />
          </div>
          <div style={sectionStyle}>
            <div className="skeleton-item" style={{ height: '20px', width: '140px', marginBottom: '16px' }} />
            <div style={{ display: 'flex', flexDirection: 'column', gap: '12px' }}>
              <CategoryBarSkeleton />
              <CategoryBarSkeleton />
              <CategoryBarSkeleton />
              <CategoryBarSkeleton />
              <CategoryBarSkeleton />
            </div>
          </div>
        </div>
      </div>
    );
  }

  // ── Error state ────────────────────────────────────────────────────────────

  if (error) {
    return (
      <div>
        <h1 style={headingStyle}>Dashboard</h1>
        <div
          role="alert"
          style={{
            backgroundColor: '#fff',
            border: '1px solid #fca5a5',
            borderRadius: '8px',
            padding: '24px',
            textAlign: 'center',
          }}
        >
          <p style={{ color: '#dc2626', marginBottom: '16px', fontSize: '0.95rem' }}>
            Unable to load dashboard data. Please try again.
          </p>
          <button
            type="button"
            onClick={fetchData}
            style={{
              padding: '8px 20px',
              backgroundColor: '#3b82f6',
              color: '#fff',
              border: 'none',
              borderRadius: '6px',
              fontSize: '0.875rem',
              cursor: 'pointer',
            }}
          >
            Retry
          </button>
        </div>
      </div>
    );
  }

  // ── Empty state ────────────────────────────────────────────────────────────

  if (data && data.analyzedLogs === 0) {
    return (
      <div>
        <h1 style={headingStyle}>Dashboard</h1>
        <div
          style={{
            backgroundColor: '#fff',
            border: '1px solid #e2e8f0',
            borderRadius: '8px',
            padding: '48px 24px',
            textAlign: 'center',
          }}
        >
          <p style={{ color: '#475569', fontSize: '1rem', marginBottom: '16px' }}>
            No analysis data yet. Analyze your first pipeline log to get started.
          </p>
          <button
            type="button"
            onClick={() => window.history.pushState({}, '', '/analyze')}
            style={{
              padding: '8px 20px',
              backgroundColor: '#3b82f6',
              color: '#fff',
              border: 'none',
              borderRadius: '6px',
              fontSize: '0.875rem',
              cursor: 'pointer',
            }}
          >
            Go to Analyze
          </button>
        </div>
      </div>
    );
  }

  // ── Data state ─────────────────────────────────────────────────────────────

  const safeData: DashboardData = data ?? {
    totalErrors: 0,
    analyzedLogs: 0,
    mostCommonIssue: null,
    categoryBreakdown: {},
    averageConfidence: 0,
    analysesLast7Days: 0,
    analysesLast30Days: 0,
    topCategories: [],
  };

  return (
    <div>
      <h1 style={headingStyle}>Dashboard</h1>
      <p style={{ margin: '-16px 0 24px', color: '#64748b', fontSize: '0.875rem' }}>
        Overview of your pipeline analysis activity
      </p>

      {/* ── KPI cards ──────────────────────────────────────────────────────── */}
      <div style={gridStyle}>
        <StatCard
          icon={<Database size={16} aria-hidden="true" />}
          value={safeData.totalErrors.toLocaleString()}
          label="Total Known Errors"
          subtitle="Knowledge base entries"
        />
        <StatCard
          icon={<FileSearch size={16} aria-hidden="true" />}
          value={safeData.analyzedLogs.toLocaleString()}
          label="Total Analyzed Logs"
          subtitle={`${safeData.analysesLast30Days} in last 30 days`}
        />
        <StatCard
          icon={<Target size={16} aria-hidden="true" />}
          value={`${safeData.averageConfidence}%`}
          label="Average Confidence"
          subtitle="Across all analyses"
        />
        <StatCard
          icon={<TrendingUp size={16} aria-hidden="true" />}
          value={safeData.analysesLast7Days.toLocaleString()}
          label="Analyses Last 7 Days"
          subtitle={safeData.mostCommonIssue ? `Top: ${safeData.mostCommonIssue}` : undefined}
        />
      </div>

      {/* ── Top categories ─────────────────────────────────────────────────── */}
      {safeData.topCategories.length > 0 ? (
        <div style={sectionStyle}>
          <h2 style={{ margin: '0 0 16px', fontSize: '1rem', fontWeight: 600, color: '#1e293b' }}>
            Top Categories
          </h2>
          <div style={{ display: 'flex', flexDirection: 'column', gap: '12px' }}>
            {safeData.topCategories.slice(0, 5).map(stat => (
              <CategoryBar key={stat.category} stat={stat} />
            ))}
          </div>
        </div>
      ) : (
        <div style={sectionStyle}>
          <h2 style={{ margin: '0 0 8px', fontSize: '1rem', fontWeight: 600, color: '#1e293b' }}>
            Top Categories
          </h2>
          <p style={{ color: '#64748b', fontSize: '0.875rem', margin: 0 }}>No category data available.</p>
        </div>
      )}
    </div>
  );
}

// ── Shared styles ──────────────────────────────────────────────────────────────

const headingStyle: React.CSSProperties = {
  fontSize: '1.5rem',
  fontWeight: 700,
  color: '#1e293b',
  margin: '0 0 20px',
};

const gridStyle: React.CSSProperties = {
  display: 'grid',
  gridTemplateColumns: 'repeat(auto-fit, minmax(220px, 1fr))',
  gap: '16px',
  marginBottom: '24px',
};

const sectionStyle: React.CSSProperties = {
  backgroundColor: '#fff',
  border: '1px solid #e2e8f0',
  borderRadius: '8px',
  padding: '24px',
  marginBottom: '24px',
};
