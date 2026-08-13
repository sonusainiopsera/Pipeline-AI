import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import '@testing-library/jest-dom';
import LoginPage from '../pages/Login';
import { loginResponse, mfaRequiredResponse } from '../__fixtures__/authResponses';

// vi.mock is hoisted above imports by Vitest — these named imports are the mocked versions
vi.mock('../api', () => ({
  login: vi.fn(),
  ApiError: class ApiError extends Error {
    public status: number;
    constructor(status: number, message: string) {
      super(message);
      this.name = 'ApiError';
      this.status = status;
    }
  },
}));

import { login, ApiError } from '../api';

const loginMock = vi.mocked(login);

beforeEach(() => {
  loginMock.mockReset();
  Object.defineProperty(window, 'location', {
    value: { href: '' },
    writable: true,
    configurable: true,
  });
});

describe('LoginPage rendering', () => {
  it('renders email and password fields', () => {
    render(<LoginPage />);
    expect(screen.getByLabelText(/email/i)).toBeInTheDocument();
    expect(screen.getByLabelText(/password/i)).toBeInTheDocument();
  });

  it('renders a sign in submit button', () => {
    render(<LoginPage />);
    expect(screen.getByRole('button', { name: /sign in/i })).toBeInTheDocument();
  });

  it('renders a link to the register page', () => {
    render(<LoginPage />);
    expect(screen.getByRole('link', { name: /register/i })).toHaveAttribute('href', '/register');
  });

  it('password field uses type=password and never exposes the value', () => {
    render(<LoginPage />);
    expect(screen.getByLabelText(/password/i)).toHaveAttribute('type', 'password');
  });
});

describe('LoginPage inline validation', () => {
  it('shows email required error when email is empty', async () => {
    render(<LoginPage />);
    fireEvent.click(screen.getByRole('button', { name: /sign in/i }));
    await waitFor(() => {
      expect(screen.getByText(/email is required/i)).toBeInTheDocument();
    });
  });

  it('shows password required error when password is empty', async () => {
    render(<LoginPage />);
    fireEvent.change(screen.getByLabelText(/email/i), { target: { value: 'user@example.com' } });
    fireEvent.click(screen.getByRole('button', { name: /sign in/i }));
    await waitFor(() => {
      expect(screen.getByText(/password is required/i)).toBeInTheDocument();
    });
  });

  it('does not call api.login when validation fails', async () => {
    render(<LoginPage />);
    fireEvent.click(screen.getByRole('button', { name: /sign in/i }));
    await waitFor(() => expect(screen.getByText(/email is required/i)).toBeInTheDocument());
    expect(loginMock).not.toHaveBeenCalled();
  });
});

describe('LoginPage form submission', () => {
  const fillAndSubmit = (emailVal = 'user@example.com', passwordVal = 'password123') => {
    fireEvent.change(screen.getByLabelText(/email/i), { target: { value: emailVal } });
    fireEvent.change(screen.getByLabelText(/password/i), { target: { value: passwordVal } });
    fireEvent.click(screen.getByRole('button', { name: /sign in/i }));
  };

  it('calls api.login with trimmed email and password', async () => {
    loginMock.mockResolvedValue({ ...loginResponse, mfaEnabled: true });
    render(<LoginPage />);
    fillAndSubmit('  user@example.com  ', 'password123');
    await waitFor(() => {
      expect(loginMock).toHaveBeenCalledWith({ email: 'user@example.com', password: 'password123' });
    });
  });

  it('redirects to / on successful login for MFA-enabled user', async () => {
    loginMock.mockResolvedValue({ ...loginResponse, mfaEnabled: true, mfaRequired: false });
    render(<LoginPage />);
    fillAndSubmit();
    await waitFor(() => expect(window.location.href).toBe('/'));
  });

  it('redirects to /mfa/verify when mfaRequired is true', async () => {
    loginMock.mockResolvedValue(mfaRequiredResponse);
    render(<LoginPage />);
    fillAndSubmit();
    await waitFor(() => expect(window.location.href).toBe('/mfa/verify'));
  });

  it('redirects to /mfa/enroll when mfaEnabled is false', async () => {
    loginMock.mockResolvedValue({ ...loginResponse, mfaEnabled: false, mfaRequired: false });
    render(<LoginPage />);
    fillAndSubmit();
    await waitFor(() => expect(window.location.href).toBe('/mfa/enroll'));
  });

  it('displays server error message on failed login', async () => {
    loginMock.mockRejectedValue(new ApiError(401, 'Invalid credentials'));
    render(<LoginPage />);
    fillAndSubmit();
    await waitFor(() => {
      expect(screen.getByRole('alert')).toHaveTextContent(/invalid credentials/i);
    });
  });

  it('disables submit button while loading', async () => {
    loginMock.mockReturnValue(new Promise(() => {}));
    render(<LoginPage />);
    fillAndSubmit();
    await waitFor(() => {
      expect(screen.getByRole('button', { name: /signing in/i })).toBeDisabled();
    });
  });
});
