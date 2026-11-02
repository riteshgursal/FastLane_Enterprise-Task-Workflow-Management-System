import type { AuthResponse } from './types';

/** Error thrown for any non-2xx response, carrying the server's Problem Details. */
export class ApiError extends Error {
  constructor(
    public status: number,
    public code: string,
    message: string,
    public fieldErrors: { field: string; message: string }[] = [],
  ) {
    super(message);
  }
}

let accessToken: string | null = null;
let refreshing: Promise<AuthResponse | null> | null = null;
let onSessionLost: () => void = () => {};

export function setAccessToken(token: string | null) {
  accessToken = token;
}

export function onSessionExpired(cb: () => void) {
  onSessionLost = cb;
}

/** Exchanges the HttpOnly refresh cookie for a new access token. Concurrent callers share one request. */
export function refreshSession(): Promise<AuthResponse | null> {
  if (!refreshing) {
    refreshing = fetch('/api/v1/auth/refresh', {
      method: 'POST',
      credentials: 'include',
      headers: { 'X-Flowlane-Csrf': '1' },
    })
      .then(async (res) => {
        if (!res.ok) return null;
        const body = (await res.json()) as AuthResponse;
        accessToken = body.accessToken;
        return body;
      })
      .catch(() => null)
      .finally(() => {
        refreshing = null;
      });
  }
  return refreshing;
}

interface Options {
  method?: string;
  body?: unknown;
  form?: FormData;
  csrf?: boolean;
}

export async function api<T>(path: string, opts: Options = {}, retried = false): Promise<T> {
  const headers: Record<string, string> = {};
  if (accessToken) headers.Authorization = `Bearer ${accessToken}`;
  if (opts.body !== undefined) headers['Content-Type'] = 'application/json';
  if (opts.csrf) headers['X-Flowlane-Csrf'] = '1';

  const res = await fetch(`/api/v1${path}`, {
    method: opts.method ?? (opts.body !== undefined || opts.form ? 'POST' : 'GET'),
    headers,
    credentials: 'include',
    body: opts.form ?? (opts.body !== undefined ? JSON.stringify(opts.body) : undefined),
  });

  if (res.status === 401 && !retried && !path.startsWith('/auth/')) {
    const refreshed = await refreshSession();
    if (refreshed) return api<T>(path, opts, true);
    onSessionLost();
  }
  if (!res.ok) {
    let problem: { code?: string; detail?: string; title?: string; errors?: { field: string; message: string }[] } = {};
    try {
      problem = await res.json();
    } catch {
      /* non-JSON error body */
    }
    throw new ApiError(res.status, problem.code ?? 'UNKNOWN', problem.detail ?? problem.title ?? 'Something went wrong.', problem.errors ?? []);
  }
  if (res.status === 204) return undefined as T;
  const type = res.headers.get('content-type') ?? '';
  return (type.includes('json') ? res.json() : res.text()) as Promise<T>;
}

/** Downloads a protected file using the in-memory access token. */
export async function download(path: string, fallbackName: string) {
  const res = await fetch(`/api/v1${path}`, {
    headers: accessToken ? { Authorization: `Bearer ${accessToken}` } : {},
    credentials: 'include',
  });
  if (!res.ok) throw new ApiError(res.status, 'DOWNLOAD_FAILED', 'Download failed.');
  const blob = await res.blob();
  const url = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url;
  a.download = fallbackName;
  a.click();
  URL.revokeObjectURL(url);
}

export function errorMessage(e: unknown): string {
  return e instanceof ApiError ? e.message : 'Something went wrong. Try again.';
}
