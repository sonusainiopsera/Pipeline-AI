import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import '@testing-library/jest-dom';
import RegisterPage from '../pages/Register';
import { registerResponse } from '../__fixtures__/authResponses';

vi.mock('../api', () => ({
  register: vi.fn(),
  ApiError: class ApiError extends Error {
    public status: number;
    constructor(status: number, message: string) {
      super(message);
      this.name = 'ApiError';
      this.status = status;
    }
  },
}));

import { register, ApiError } from '../api';

const registerMock = vi.mocked(register);

beforeEach(() => {
  registerMock.mockReset();
});

describe('RegisterPage rendering', () => {
  it('renders email, displayName, password, and confirmPassword fields', () => {
    render(<RegisterPage />);
    expect(screen.getByLabelText(/email/i)).toBeInTheDocument();
    expect(screen.getByLabelText(/display name/i)).toBeInTheDocument();
    expect(screen.getByLabelText(/^password$/i)).toBeInTheDocument();
    expect(screen.getByLabelText(/confirm password/i)).toBeInTheDocument();
  });

  it('renders a create account submit button', () => {
    render(<RegisterPage />);
    expect(screen.getByRole('button', { name: /create account/i })).toBeInTheDocument();
  });

  it('renders a link back to the login page', () => {
    render(<RegisterPage />);
    expect(screen.getByRole('link', { name: /sign in/i })).toHaveAttribute('href', '/login');
  });

  it('both password fields use type=password', () => {
    render(<RegisterPage />);
    const passwordInputs = document.querySelectorAll('input[type="password"]');
    expect(passwordInputs).toHaveLength(2);
  });
});

describe('RegisterPage validation', () => {
  it('shows required errors for all empty fields on submit', async () => {
    render(<RegisterPage />);
    fireEvent.click(screen.getByRole('button', { name: /create account/i }));
    await waitFor(() => {
      expect(screen.getByText(/email is required/i)).toBeInTheDocument();
      expect(screen.getByText(/display name is required/i)).toBeInTheDocument();
      expect(screen.getByText(/^password is required/i)).toBeInTheDocument();
    });
  });

  it('shows error when passwords do not match', async () => {
    render(<RegisterPage />);
    fireEvent.change(screen.getByLabelText(/email/i), { target: { value: 'user@example.com' } });
    fireEvent.change(screen.getByLabelText(/display name/i), { target: { value: 'Test User' } });
    fireEvent.change(screen.getByLabelText(/^password$/i), { target: { value: 'Password1!' } });
    fireEvent.change(screen.getByLabelText(/confirm password/i), { target: { value: 'Different1!' } });
    fireEvent.click(screen.getByRole('button', { name: /create account/i }));
    await waitFor(() => {
      expect(screen.getByText(/passwords do not match/i)).toBeInTheDocument();
    });
  });

  it('does not call api.register when validation fails', async () => {
    render(<RegisterPage />);
    fireEvent.click(screen.getByRole('button', { name: /create account/i }));
    await waitFor(() => expect(screen.getByText(/email is required/i)).toBeInTheDocument());
    expect(registerMock).not.toHaveBeenCalled();
  });
});

describe('RegisterPage form submission', () => {
  const fillForm = () => {
    fireEvent.change(screen.getByLabelText(/email/i), { target: { value: 'new@example.com' } });
    fireEvent.change(screen.getByLabelText(/display name/i), { target: { value: 'New User' } });
    fireEvent.change(screen.getByLabelText(/^password$/i), { target: { value: 'SecurePass1!' } });
    fireEvent.change(screen.getByLabelText(/confirm password/i), { target: { value: 'SecurePass1!' } });
    fireEvent.click(screen.getByRole('button', { name: /create account/i }));
  };

  it('calls api.register with trimmed email, displayName, and password', async () => {
    registerMock.mockResolvedValue(registerResponse);
    render(<RegisterPage />);
    fillForm();
    await waitFor(() => {
      expect(registerMock).toHaveBeenCalledWith({
        email: 'new@example.com',
        password: 'SecurePass1!',
        displayName: 'New User',
      });
    });
  });

  it('shows success message after registration', async () => {
    registerMock.mockResolvedValue(registerResponse);
    render(<RegisterPage />);
    fillForm();
    await waitFor(() => {
      expect(screen.getByText(/registration successful/i)).toBeInTheDocument();
    });
  });

  it('shows server error message on failed registration', async () => {
    registerMock.mockRejectedValue(new ApiError(400, 'Email already in use'));
    render(<RegisterPage />);
    fillForm();
    await waitFor(() => {
      expect(screen.getByRole('alert')).toHaveTextContent(/email already in use/i);
    });
  });

  it('disables submit button while loading', async () => {
    registerMock.mockReturnValue(new Promise(() => {}));
    render(<RegisterPage />);
    fillForm();
    await waitFor(() => {
      expect(screen.getByRole('button', { name: /creating account/i })).toBeDisabled();
    });
  });
});
