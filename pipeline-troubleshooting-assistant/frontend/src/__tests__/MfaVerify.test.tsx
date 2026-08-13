import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import '@testing-library/jest-dom';
import MfaVerifyPage from '../pages/MfaVerify';
import { loginResponse } from '../__fixtures__/authResponses';

vi.mock('../api', () => ({
  mfaChallenge: vi.fn(),
  ApiError: class ApiError extends Error {
    public status: number;
    constructor(status: number, message: string) {
      super(message);
      this.name = 'ApiError';
      this.status = status;
    }
  },
}));

import { mfaChallenge, ApiError } from '../api';

const mfaChallengeMock = vi.mocked(mfaChallenge);

beforeEach(() => {
  mfaChallengeMock.mockReset();
  Object.defineProperty(window, 'location', {
    value: { href: '' },
    writable: true,
    configurable: true,
  });
});

describe('MfaVerifyPage rendering', () => {
  it('renders a 6-digit code input with numeric inputMode', () => {
    render(<MfaVerifyPage />);
    const input = screen.getByLabelText(/authentication code/i);
    expect(input).toBeInTheDocument();
    expect(input).toHaveAttribute('inputmode', 'numeric');
    expect(input).toHaveAttribute('maxlength', '6');
  });

  it('renders a verify submit button', () => {
    render(<MfaVerifyPage />);
    expect(screen.getByRole('button', { name: /^verify$/i })).toBeInTheDocument();
  });

  it('renders a recovery code link', () => {
    render(<MfaVerifyPage />);
    expect(screen.getByRole('link', { name: /recovery code/i })).toBeInTheDocument();
  });

  it('renders a link back to login', () => {
    render(<MfaVerifyPage />);
    expect(screen.getByRole('link', { name: /back to login/i })).toHaveAttribute('href', '/login');
  });
});

describe('MfaVerifyPage submission', () => {
  it('shows error when code is empty on submit', async () => {
    render(<MfaVerifyPage />);
    fireEvent.click(screen.getByRole('button', { name: /^verify$/i }));
    await waitFor(() => {
      expect(screen.getByRole('alert')).toHaveTextContent(/please enter your 6-digit code/i);
    });
    expect(mfaChallengeMock).not.toHaveBeenCalled();
  });

  it('trims whitespace from code before submitting', async () => {
    mfaChallengeMock.mockResolvedValue(loginResponse);
    render(<MfaVerifyPage />);
    fireEvent.change(screen.getByLabelText(/authentication code/i), { target: { value: ' 123456 ' } });
    fireEvent.click(screen.getByRole('button', { name: /^verify$/i }));
    await waitFor(() => {
      expect(mfaChallengeMock).toHaveBeenCalledWith({ code: '123456' });
    });
  });

  it('redirects to / on successful verification', async () => {
    mfaChallengeMock.mockResolvedValue(loginResponse);
    render(<MfaVerifyPage />);
    fireEvent.change(screen.getByLabelText(/authentication code/i), { target: { value: '123456' } });
    fireEvent.click(screen.getByRole('button', { name: /^verify$/i }));
    await waitFor(() => expect(window.location.href).toBe('/'));
  });

  it('shows error message on invalid code', async () => {
    mfaChallengeMock.mockRejectedValue(new ApiError(401, 'Invalid TOTP code'));
    render(<MfaVerifyPage />);
    fireEvent.change(screen.getByLabelText(/authentication code/i), { target: { value: '000000' } });
    fireEvent.click(screen.getByRole('button', { name: /^verify$/i }));
    await waitFor(() => {
      expect(screen.getByRole('alert')).toHaveTextContent(/invalid totp code/i);
    });
  });

  it('disables submit button while loading', async () => {
    mfaChallengeMock.mockReturnValue(new Promise(() => {}));
    render(<MfaVerifyPage />);
    fireEvent.change(screen.getByLabelText(/authentication code/i), { target: { value: '123456' } });
    fireEvent.click(screen.getByRole('button', { name: /^verify$/i }));
    await waitFor(() => {
      expect(screen.getByRole('button', { name: /verifying/i })).toBeDisabled();
    });
  });
});
