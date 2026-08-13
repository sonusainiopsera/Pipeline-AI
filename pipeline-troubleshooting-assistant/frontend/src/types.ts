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

// ── Dashboard types ───────────────────────────────────────────────────────────

export interface CategoryStat {
  category: string;
  count: number;
  percentage: number;
}

export interface DashboardData {
  totalErrors: number;
  analyzedLogs: number;
  mostCommonIssue: string | null;
  categoryBreakdown: Record<string, number>;
  averageConfidence: number;
  analysesLast7Days: number;
  analysesLast30Days: number;
  topCategories: CategoryStat[];
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

export interface AuditLogEntry {
  id: number;
  actorEmail: string;
  action: string;
  resourceType: string;
  resourceId: string | null;
  details: Record<string, unknown> | null;
  ipAddress: string | null;
  createdAt: string;
}

export interface AuditLogPage {
  content: AuditLogEntry[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
}

// ── User profile types ────────────────────────────────────────────────────────

export interface UserProfile {
  displayName: string;
  email: string;
  role: string;
  mfaEnabled: boolean;
  createdAt: string;
}

export interface UpdateProfileRequest {
  displayName: string;
}

export interface ChangePasswordRequest {
  currentPassword: string;
  newPassword: string;
}

export interface SessionInfo {
  sessionId: string;
  createdAt: string;
  lastUsedAt: string | null;
  ipAddress: string | null;
  isCurrent: boolean;
}

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
  verificationUrl?: string | null;
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
