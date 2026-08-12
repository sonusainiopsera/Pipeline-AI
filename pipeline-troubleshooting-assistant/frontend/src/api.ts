import type {
  AnalyzedLog,
  AuthMessageResponse,
  HistoryItem,
  HistoryListItem,
  KnowledgeBaseEntry,
  KnowledgeBaseRequest,
  LoginRequest,
  LoginResponse,
  MfaChallengeRequest,
  MfaRecoverRequest,
  MfaSetupResponse,
  MfaVerifyRequest,
  PageResponse,
  RegisterRequest,
} from './types';

export type {
  AnalyzedLog,
  AuthMessageResponse,
  HistoryItem,
  HistoryListItem,
  KnowledgeBaseEntry,
  KnowledgeBaseRequest,
  LoginRequest,
  LoginResponse,
  MfaChallengeRequest,
  MfaRecoverRequest,
  MfaSetupResponse,
  MfaVerifyRequest,
  PageResponse,
  RegisterRequest,
};

const BASE = '/api';

// ── 401 refresh interceptor state ─────────────────────────────────────────────

let isRefreshing = false;
let refreshQueue: Array<(succeeded: boolean) => void> = [];

function drainQueue(succeeded: boolean): void {
  const queue = refreshQueue;
  refreshQueue = [];
  isRefreshing = false;
  queue.forEach(cb => cb(succeeded));
}

async function attemptRefresh(): Promise<boolean> {
  try {
    const res = await fetch(`${BASE}/auth/refresh`, {
      method: 'POST',
      credentials: 'include',
    });
    return res.ok;
  } catch {
    return false;
  }
}

// ── Core request helper ────────────────────────────────────────────────────────

async function request<T>(url: string, options: RequestInit = {}): Promise<T> {
  const res = await fetch(url, {
    ...options,
    credentials: 'include',
    headers: {
      'Content-Type': 'application/json',
      ...(options.headers ?? {}),
    },
  });

  if (res.status === 401) {
    // Never intercept the refresh endpoint itself — prevents infinite loop
    if (url.includes('/auth/refresh') || url.includes('/auth/login')) {
      throw new ApiError(401, 'Unauthorized');
    }
    return handle401<T>(url, options);
  }

  if (!res.ok) {
    let message = res.statusText;
    try {
      const body = (await res.json()) as { message?: string };
      if (body.message) message = body.message;
    } catch {
      // ignore parse errors
    }
    throw new ApiError(res.status, message);
  }

  if (res.status === 204) return undefined as T;
  return res.json() as Promise<T>;
}

async function handle401<T>(url: string, options: RequestInit): Promise<T> {
  if (isRefreshing) {
    return new Promise<T>((resolve, reject) => {
      refreshQueue.push(succeeded => {
        if (succeeded) {
          request<T>(url, options).then(resolve).catch(reject);
        } else {
          reject(new ApiError(401, 'Session expired. Please log in again.'));
        }
      });
    });
  }

  isRefreshing = true;
  const refreshed = await attemptRefresh();

  if (refreshed) {
    drainQueue(true);
    return request<T>(url, options);
  } else {
    drainQueue(false);
    window.location.href = '/login';
    throw new ApiError(401, 'Session expired. Please log in again.');
  }
}

export class ApiError extends Error {
  constructor(
    public readonly status: number,
    message: string,
  ) {
    super(message);
    this.name = 'ApiError';
  }
}

// ── Auth endpoints ─────────────────────────────────────────────────────────────

export async function login(credentials: LoginRequest): Promise<LoginResponse> {
  return request<LoginResponse>(`${BASE}/auth/login`, {
    method: 'POST',
    body: JSON.stringify(credentials),
  });
}

export async function register(data: RegisterRequest): Promise<AuthMessageResponse> {
  return request<AuthMessageResponse>(`${BASE}/auth/register`, {
    method: 'POST',
    body: JSON.stringify(data),
  });
}

export async function verifyEmail(token: string): Promise<AuthMessageResponse> {
  return request<AuthMessageResponse>(`${BASE}/auth/verify?token=${encodeURIComponent(token)}`);
}

export async function resendVerification(email: string): Promise<AuthMessageResponse> {
  return request<AuthMessageResponse>(`${BASE}/auth/verify/resend`, {
    method: 'POST',
    body: JSON.stringify({ email }),
  });
}

export async function refreshToken(): Promise<AuthMessageResponse> {
  return request<AuthMessageResponse>(`${BASE}/auth/refresh`, {
    method: 'POST',
  });
}

export async function logout(): Promise<AuthMessageResponse> {
  return request<AuthMessageResponse>(`${BASE}/auth/logout`, {
    method: 'POST',
  });
}

export async function getMe(): Promise<LoginResponse> {
  return request<LoginResponse>(`${BASE}/auth/me`);
}

export async function mfaSetup(): Promise<MfaSetupResponse> {
  return request<MfaSetupResponse>(`${BASE}/auth/mfa/setup`, { method: 'POST' });
}

export async function mfaVerify(data: MfaVerifyRequest): Promise<AuthMessageResponse> {
  return request<AuthMessageResponse>(`${BASE}/auth/mfa/verify`, {
    method: 'POST',
    body: JSON.stringify(data),
  });
}

export async function mfaChallenge(data: MfaChallengeRequest): Promise<LoginResponse> {
  return request<LoginResponse>(`${BASE}/auth/mfa/challenge`, {
    method: 'POST',
    body: JSON.stringify(data),
  });
}

export async function mfaRecover(data: MfaRecoverRequest): Promise<LoginResponse> {
  return request<LoginResponse>(`${BASE}/auth/mfa/recover`, {
    method: 'POST',
    body: JSON.stringify(data),
  });
}

// ── Analysis endpoints ─────────────────────────────────────────────────────────

export async function analyze(logText: string): Promise<AnalyzedLog> {
  return request<AnalyzedLog>(`${BASE}/analyze`, {
    method: 'POST',
    body: JSON.stringify({ logText }),
  });
}

export async function getHistory(): Promise<AnalyzedLog[]> {
  return request<AnalyzedLog[]>(`${BASE}/history`);
}

export async function history(page = 0, size = 20): Promise<PageResponse<HistoryListItem>> {
  return request<PageResponse<HistoryListItem>>(`${BASE}/history?page=${page}&size=${size}`);
}

export async function historyDetail(id: number): Promise<HistoryItem> {
  return request<HistoryItem>(`${BASE}/history/${id}`);
}

// ── Knowledge base endpoints ───────────────────────────────────────────────────

export async function getKnowledgeBase(): Promise<KnowledgeBaseEntry[]> {
  return request<KnowledgeBaseEntry[]>(`${BASE}/errors`);
}

export async function createKnowledgeBaseEntry(
  data: KnowledgeBaseRequest,
): Promise<KnowledgeBaseEntry> {
  return request<KnowledgeBaseEntry>(`${BASE}/errors`, {
    method: 'POST',
    body: JSON.stringify(data),
  });
}

export async function updateKnowledgeBaseEntry(
  id: number,
  data: KnowledgeBaseRequest,
): Promise<KnowledgeBaseEntry> {
  return request<KnowledgeBaseEntry>(`${BASE}/errors/${id}`, {
    method: 'PUT',
    body: JSON.stringify(data),
  });
}

export async function deleteKnowledgeBaseEntry(id: number): Promise<void> {
  return request<void>(`${BASE}/errors/${id}`, { method: 'DELETE' });
}
