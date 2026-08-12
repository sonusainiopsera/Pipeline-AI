import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import '@testing-library/jest-dom';
import MfaEnrollPage from '../pages/MfaEnroll';
import { mfaSetupResponse } from '../__fixtures__/authResponses';

vi.mock('../api', () => ({
  mfaSetup: vi.fn(),
  mfaVerify: vi.fn(),
  ApiError: class ApiError extends Error {
    public status: number;
    constructor(status: number, message: string) {
      super(message);
      this.name = 'ApiError';
      this.status = status;
    }
  },
}));

import { mfaSetup, mfaVerify, ApiError } from '../api';

const mfaSetupMock = vi.mocked(mfaSetup);
const mfaVerifyMock = vi.mocked(mfaVerify);

beforeEach(() => {
  mfaSetupMock.mockReset();
  mfaVerifyMock.mockReset();
  Object.defineProperty(window, 'location', {
    value: { href: '' },
    writable: true,
    configurable: true,
  });
});

describe('MfaEnrollPage setup phase', () => {
  it('calls api.mfaSetup on mount', async () => {
    mfaSetupMock.mockResolvedValue(mfaSetupResponse);
    render(<MfaEnrollPage />);
    await waitFor(() => expect(mfaSetupMock).toHaveBeenCalledOnce());
  });

  it('renders the QR code URI from setup response', async () => {
    mfaSetupMock.mockResolvedValue(mfaSetupResponse);
    render(<MfaEnrollPage />);
    await waitFor(() => {
      expect(screen.getByDisplayValue(mfaSetupResponse.qrCodeUri)).toBeInTheDocument();
    });
  });

  it('renders a TOTP code verification input after setup loads', async () => {
    mfaSetupMock.mockResolvedValue(mfaSetupResponse);
    render(<MfaEnrollPage />);
    await waitFor(() => {
      expect(screen.getByLabelText(/verification code/i)).toBeInTheDocument();
    });
  });

  it('shows an error alert when setup fails', async () => {
    mfaSetupMock.mockRejectedValue(new ApiError(401, 'Authentication required'));
    render(<MfaEnrollPage />);
    await waitFor(() => {
      expect(screen.getByRole('alert')).toBeInTheDocument();
    });
  });
});

describe('MfaEnrollPage verification phase', () => {
  const setupAndVerify = async (code = '123456') => {
    mfaSetupMock.mockResolvedValue(mfaSetupResponse);
    mfaVerifyMock.mockResolvedValue({ message: 'MFA enrollment completed successfully' });
    render(<MfaEnrollPage />);
    await waitFor(() => screen.getByLabelText(/verification code/i));
    fireEvent.change(screen.getByLabelText(/verification code/i), { target: { value: code } });
    fireEvent.click(screen.getByRole('button', { name: /verify and enable mfa/i }));
  };

  it('calls api.mfaVerify with the entered code', async () => {
    await setupAndVerify('123456');
    await waitFor(() => {
      expect(mfaVerifyMock).toHaveBeenCalledWith({ code: '123456' });
    });
  });

  it('shows recovery codes after successful verification', async () => {
    await setupAndVerify();
    await waitFor(() => {
      expect(screen.getByText('AAAA-BBBB')).toBeInTheDocument();
      expect(screen.getByText('CCCC-DDDD')).toBeInTheDocument();
      expect(screen.getByText('EEEE-FFFF')).toBeInTheDocument();
    });
  });

  it('shows copy all codes button on recovery codes screen', async () => {
    await setupAndVerify();
    await waitFor(() => {
      expect(screen.getByRole('button', { name: /copy all codes/i })).toBeInTheDocument();
    });
  });

  it('continue to dashboard button is disabled until acknowledgement checked', async () => {
    await setupAndVerify();
    await waitFor(() => {
      expect(screen.getByRole('button', { name: /continue to dashboard/i })).toBeDisabled();
    });
  });

  it('enables continue button after checking acknowledgement', async () => {
    await setupAndVerify();
    await waitFor(() => screen.getByLabelText(/saved my recovery codes/i));
    fireEvent.click(screen.getByLabelText(/saved my recovery codes/i));
    await waitFor(() => {
      expect(screen.getByRole('button', { name: /continue to dashboard/i })).not.toBeDisabled();
    });
  });

  it('shows error message when verification code is invalid', async () => {
    mfaSetupMock.mockResolvedValue(mfaSetupResponse);
    mfaVerifyMock.mockRejectedValue(new ApiError(401, 'Invalid TOTP code'));
    render(<MfaEnrollPage />);
    await waitFor(() => screen.getByLabelText(/verification code/i));
    fireEvent.change(screen.getByLabelText(/verification code/i), { target: { value: '000000' } });
    fireEvent.click(screen.getByRole('button', { name: /verify and enable mfa/i }));
    await waitFor(() => {
      expect(screen.getByRole('alert')).toHaveTextContent(/invalid totp code/i);
    });
  });
});
