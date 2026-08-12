import type {
  AuthMessageResponse,
  LoginResponse,
  MfaSetupResponse,
} from '../types';

export const loginResponse: LoginResponse = {
  id: 'usr-1234',
  email: 'user@example.com',
  displayName: 'Test User',
  role: 'ANALYST',
  mfaEnabled: false,
};

export const mfaRequiredResponse: LoginResponse = {
  id: '',
  email: 'user@example.com',
  displayName: '',
  role: '',
  mfaEnabled: true,
  mfaRequired: true,
};

export const registerResponse: AuthMessageResponse = {
  message: 'Registration successful. Please verify your email.',
};

export const verifyEmailResponse: AuthMessageResponse = {
  message: 'Email verified successfully. You can now log in.',
};

export const resendVerificationResponse: AuthMessageResponse = {
  message: 'Verification email sent.',
};

export const logoutResponse: AuthMessageResponse = {
  message: 'Logged out successfully',
};

export const refreshResponse: AuthMessageResponse = {
  message: 'Token refreshed',
};

export const mfaSetupResponse: MfaSetupResponse = {
  secret: 'JBSWY3DPEHPK3PXP',
  qrCodeUrl: 'otpauth://totp/PipelineAssistant:user@example.com?secret=JBSWY3DPEHPK3PXP',
  recoveryCodes: ['AAAA-BBBB', 'CCCC-DDDD', 'EEEE-FFFF'],
};

export const unauthorizedResponse = { message: 'Session expired. Please log in again.' };
