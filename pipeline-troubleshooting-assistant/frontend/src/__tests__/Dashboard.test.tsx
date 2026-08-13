import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import '@testing-library/jest-dom';
import DashboardPage from '../pages/Dashboard';
import mockDashboard from '../../tests/fixtures/mock-dashboard-response.json';

vi.mock('../api', () => ({
  getDashboard: vi.fn(),
  ApiError: class ApiError extends Error {
    public status: number;
    constructor(status: number, message: string) {
      super(message);
      this.name = 'ApiError';
      this.status = status;
    }
  },
}));

import { getDashboard } from '../api';
const getDashboardMock = vi.mocked(getDashboard);

beforeEach(() => {
  getDashboardMock.mockReset();
});

describe('DashboardPage — loading state', () => {
  it('shows skeleton placeholders while loading', () => {
    getDashboardMock.mockReturnValue(new Promise(() => {})); // never resolves
    render(<DashboardPage />);
    expect(screen.getByRole('status', { hidden: true })).toBeInTheDocument();
  });
});

describe('DashboardPage — error state', () => {
  it('renders the error message and retry button on fetch failure', async () => {
    getDashboardMock.mockRejectedValue(new Error('Network error'));
    render(<DashboardPage />);
    await screen.findByRole('alert');
    expect(screen.getByText(/Unable to load dashboard data/i)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /retry/i })).toBeInTheDocument();
  });

  it('retry button re-fetches data', async () => {
    getDashboardMock
      .mockRejectedValueOnce(new Error('fail'))
      .mockResolvedValueOnce(mockDashboard);

    render(<DashboardPage />);
    await screen.findByRole('alert');

    fireEvent.click(screen.getByRole('button', { name: /retry/i }));

    await waitFor(() => {
      expect(getDashboardMock).toHaveBeenCalledTimes(2);
    });
  });
});

describe('DashboardPage — empty state', () => {
  it('shows guidance text when analyzedLogs is 0', async () => {
    getDashboardMock.mockResolvedValue({
      ...mockDashboard,
      analyzedLogs: 0,
      analysesLast7Days: 0,
      analysesLast30Days: 0,
      topCategories: [],
    });
    render(<DashboardPage />);
    await screen.findByText(/No analysis data yet/i);
    expect(screen.getByText(/Analyze your first pipeline log/i)).toBeInTheDocument();
  });
});

describe('DashboardPage — data state', () => {
  beforeEach(() => {
    getDashboardMock.mockResolvedValue(mockDashboard);
  });

  it('renders 4 KPI card labels', async () => {
    render(<DashboardPage />);
    await screen.findByText('TOTAL KNOWN ERRORS');
    expect(screen.getByText('TOTAL ANALYZED LOGS')).toBeInTheDocument();
    expect(screen.getByText('AVERAGE CONFIDENCE')).toBeInTheDocument();
    expect(screen.getByText('ANALYSES LAST 7 DAYS')).toBeInTheDocument();
  });

  it('renders correct numeric values from mock data', async () => {
    render(<DashboardPage />);
    await screen.findByText('42'); // totalErrors
    expect(screen.getByText('128')).toBeInTheDocument(); // analyzedLogs
    expect(screen.getByText('87%')).toBeInTheDocument(); // averageConfidence
    expect(screen.getByText('15')).toBeInTheDocument(); // analysesLast7Days
  });

  it('renders top categories section with category names', async () => {
    render(<DashboardPage />);
    await screen.findByText('Top Categories');
    expect(screen.getByText('Docker Issues')).toBeInTheDocument();
    expect(screen.getByText('Network Timeout')).toBeInTheDocument();
    expect(screen.getByText('Memory Leak')).toBeInTheDocument();
  });

  it('renders percentage values for categories', async () => {
    render(<DashboardPage />);
    await screen.findByText('Top Categories');
    expect(screen.getByText(/35\.2%/)).toBeInTheDocument();
  });

  it('renders at most 5 categories', async () => {
    const extraCategories = {
      ...mockDashboard,
      topCategories: [
        ...mockDashboard.topCategories,
        { category: 'Sixth Category', count: 5, percentage: 3.9 },
      ],
    };
    getDashboardMock.mockResolvedValue(extraCategories);
    render(<DashboardPage />);
    await screen.findByText('Top Categories');
    expect(screen.queryByText('Sixth Category')).not.toBeInTheDocument();
  });

  it('shows no category data message when topCategories is empty', async () => {
    getDashboardMock.mockResolvedValue({ ...mockDashboard, topCategories: [] });
    render(<DashboardPage />);
    await screen.findByText(/No category data available/i);
  });
});
