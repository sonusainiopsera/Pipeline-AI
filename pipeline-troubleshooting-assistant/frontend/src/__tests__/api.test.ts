import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import {
  analyze,
  login,
  logout,
  register,
  ApiError,
} from '../api';
import {
  loginResponse,
  registerResponse,
  logoutResponse,
} from '../__fixtures__/authResponses';

// ── fetch mock helpers ────────────────────────────────────────────────────────

function mockFetch(responses: Array<{ status: number; body?: unknown }>): void {
  let callIndex = 0;
  vi.stubGlobal(
    'fetch',
    vi.fn(() => {
      const resp = responses[callIndex] ?? responses[responses.length - 1];
      callIndex++;
      return Promise.resolve({
        ok: resp.status >= 200 && resp.status < 300,
        status: resp.status,
        statusText: resp.status === 200 ? 'OK' : 'Error',
        json: () => Promise.resolve(resp.body ?? {}),
      });
    }),
  );
}

// ── helpers ───────────────────────────────────────────────────────────────────

beforeEach(() => {
  vi.unstubAllGlobals();
  // Reset module-level isRefreshing state between tests by re-importing
  // is not straightforward; instead we rely on fresh fetch mocks per test.
});

afterEach(() => {
  vi.unstubAllGlobals();
});

// ── AC1: credentials: 'include' on all requests ───────────────────────────────

describe('credentials: include', () => {
  it('includes credentials on a regular request', async () => {
    mockFetch([{ status: 200, body: loginResponse }]);

    await login({ email: 'user@example.com', password: 'ValidPass123!' });

    const fetchMock = vi.mocked(global.fetch);
    expect(fetchMock).toHaveBeenCalledOnce();
    const [, opts] = fetchMock.mock.calls[0] as [string, RequestInit];
    expect(opts.credentials).toBe('include');
  });

  it('includes credentials on analyze request', async () => {
    const body = { id: 1, category: 'Memory', rootCause: 'OOM', suggestedFix: 'fix', customerUpdate: '', severity: 'HIGH', confidence: 90, createdAt: '2024-01-01T00:00:00' };
    mockFetch([{ status: 200, body }]);

    await analyze('java.lang.OutOfMemoryError');

    const fetchMock = vi.mocked(global.fetch);
    const [, opts] = fetchMock.mock.calls[0] as [string, RequestInit];
    expect(opts.credentials).toBe('include');
  });
});

// ── AC2: 401 triggers refresh attempt ─────────────────────────────────────────

describe('401 interceptor', () => {
  it('triggers POST /api/auth/refresh on 401 response', async () => {
    mockFetch([
      { status: 401 },                            // original request fails
      { status: 200, body: { message: 'ok' } },  // refresh succeeds
      { status: 200, body: { id: 1, category: 'Memory', rootCause: '', suggestedFix: '', customerUpdate: '', severity: 'HIGH', confidence: 90, createdAt: '' } }, // retry succeeds
    ]);

    await analyze('log text');

    const fetchMock = vi.mocked(global.fetch);
    expect(fetchMock).toHaveBeenCalledTimes(3);
    const [refreshUrl, refreshOpts] = fetchMock.mock.calls[1] as [string, RequestInit];
    expect(refreshUrl).toContain('/auth/refresh');
    expect(refreshOpts.method).toBe('POST');
    expect(refreshOpts.credentials).toBe('include');
  });

  // AC3: successful refresh retries original request
  it('retries original request after successful refresh', async () => {
    const analysisBody = { id: 1, category: 'Docker', rootCause: 'r', suggestedFix: 's', customerUpdate: '', severity: 'LOW', confidence: 70, createdAt: '' };
    mockFetch([
      { status: 401 },
      { status: 200, body: { message: 'ok' } },
      { status: 200, body: analysisBody },
    ]);

    const result = await analyze('some log');

    expect(result.category).toBe('Docker');
  });

  // AC4: failed refresh redirects to /login
  it('redirects to /login when refresh also returns 401', async () => {
    const locationSpy = vi.fn();
    Object.defineProperty(window, 'location', {
      value: { href: '' },
      writable: true,
    });
    Object.defineProperty(window.location, 'href', {
      set: locationSpy,
      configurable: true,
    });

    mockFetch([
      { status: 401 },  // original request
      { status: 401 },  // refresh also fails
    ]);

    await expect(analyze('log')).rejects.toThrow();
    expect(locationSpy).toHaveBeenCalledWith('/login');
  });

  it('does not trigger refresh loop on 401 from /auth/login', async () => {
    mockFetch([{ status: 401 }]);

    await expect(
      login({ email: 'u@example.com', password: 'bad' }),
    ).rejects.toBeInstanceOf(ApiError);

    // Only one fetch call — no retry loop
    expect(vi.mocked(global.fetch)).toHaveBeenCalledOnce();
  });
});

// ── Auth API functions ────────────────────────────────────────────────────────

describe('auth API functions', () => {
  it('login sends email and password', async () => {
    mockFetch([{ status: 200, body: loginResponse }]);

    const result = await login({ email: 'user@example.com', password: 'ValidPass123!' });

    const fetchMock = vi.mocked(global.fetch);
    const [url, opts] = fetchMock.mock.calls[0] as [string, RequestInit];
    expect(url).toContain('/auth/login');
    expect(opts.method).toBe('POST');
    expect(JSON.parse(opts.body as string)).toMatchObject({
      email: 'user@example.com',
      password: 'ValidPass123!',
    });
    expect(result.email).toBe('user@example.com');
  });

  it('register sends email, password, displayName', async () => {
    mockFetch([{ status: 201, body: registerResponse }]);

    const result = await register({
      email: 'new@example.com',
      password: 'SecurePass123!',
      displayName: 'New User',
    });

    const fetchMock = vi.mocked(global.fetch);
    const [url, opts] = fetchMock.mock.calls[0] as [string, RequestInit];
    expect(url).toContain('/auth/register');
    expect(JSON.parse(opts.body as string)).toMatchObject({
      email: 'new@example.com',
      displayName: 'New User',
    });
    expect(result.message).toContain('Registration successful');
  });

  it('logout sends POST and returns message', async () => {
    mockFetch([{ status: 200, body: logoutResponse }]);

    const result = await logout();

    const fetchMock = vi.mocked(global.fetch);
    const [url, opts] = fetchMock.mock.calls[0] as [string, RequestInit];
    expect(url).toContain('/auth/logout');
    expect(opts.method).toBe('POST');
    expect(result.message).toBe('Logged out successfully');
  });
});

// ── ApiError ──────────────────────────────────────────────────────────────────

describe('ApiError', () => {
  it('throws ApiError with status on non-ok response', async () => {
    mockFetch([{ status: 400, body: { message: 'Bad Request' } }]);

    try {
      await register({ email: 'bad', password: 'weak', displayName: '' });
      expect.fail('should have thrown');
    } catch (err) {
      expect(err).toBeInstanceOf(ApiError);
      expect((err as ApiError).status).toBe(400);
    }
  });
});
