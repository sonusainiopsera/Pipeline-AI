export interface HistoryListItem {
  id: number;
  detectedCategory: string;
  rootCause: string;
  suggestedFix: string;
  severity: string;
  confidence: number;
  createdAt: string;
}

export interface HistoryItem {
  id: number;
  logText: string | null;
  detectedCategory: string;
  rootCause: string;
  suggestedFix: string;
  customerUpdate: string;
  severity: string;
  confidence: number;
  createdAt: string;
}

export interface PageResponse<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
  first: boolean;
  last: boolean;
  empty: boolean;
}

export interface AnalyzedLog {
  id: number;
  logText?: string;
  category: string;
  rootCause: string;
  suggestedFix: string;
  customerUpdate: string;
  severity: string;
  confidence: number;
  createdAt: string;
  matchedPatterns?: string[];
}

export interface KnowledgeBaseEntry {
  id: number;
  errorPattern: string;
  category: string;
  rootCause: string;
  solution: string;
  severity: string;
}

export interface KnowledgeBaseRequest {
  errorPattern: string;
  category: string;
  rootCause: string;
  solution: string;
  severity: string;
}

export type UserRole = 'ANALYST' | 'KB_ADMIN' | 'MANAGER';

// ── Auth types ────────────────────────────────────────────────────────────────

export interface LoginRequest {
  email: string;
  password: string;
}

export interface RegisterRequest {
  email: string;
  password: string;
  displayName: string;
}

export interface LoginResponse {
  id: string;
  email: string;
  displayName: string;
  role: string;
  mfaEnabled: boolean;
  mfaRequired?: boolean;
}

export interface AuthMessageResponse {
  message: string;
}

export interface MfaSetupResponse {
  secret?: string;
  qrCodeUri: string;
  recoveryCodes: string[];
}

export interface MfaVerifyRequest {
  code: string;
}

export interface MfaChallengeRequest {
  code: string;
}

export interface MfaRecoverRequest {
  recoveryCode: string;
}
