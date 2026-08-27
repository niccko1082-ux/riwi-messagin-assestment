import type { ApiError, AuthResponse } from './types';

export const API_BASE_URL =
  import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080';

const ACCESS_KEY = 'riwi.accessToken';
const REFRESH_KEY = 'riwi.refreshToken';

export function getAccessToken(): string | null {
  return localStorage.getItem(ACCESS_KEY);
}

export function storeSession(auth: AuthResponse): void {
  localStorage.setItem(ACCESS_KEY, auth.accessToken);
  localStorage.setItem(REFRESH_KEY, auth.refreshToken);
}

export function clearSession(): void {
  localStorage.removeItem(ACCESS_KEY);
  localStorage.removeItem(REFRESH_KEY);
}

export class ApiRequestError extends Error {
  readonly error: ApiError;

  constructor(error: ApiError) {
    super(error.message);
    this.error = error;
  }
}

let onSessionExpired: () => void = () => {};
export function setSessionExpiredHandler(handler: () => void): void {
  onSessionExpired = handler;
}

// Un solo refresh en vuelo: varias peticiones con 401 simultáneas no deben rotar el
// refresh token más de una vez (la rotación invalida el token anterior).
let refreshInFlight: Promise<boolean> | null = null;

async function tryRefresh(): Promise<boolean> {
  if (!refreshInFlight) {
    refreshInFlight = (async () => {
      const refreshToken = localStorage.getItem(REFRESH_KEY);
      if (!refreshToken) return false;
      const res = await fetch(`${API_BASE_URL}/api/auth/refresh`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ refreshToken }),
      });
      if (!res.ok) return false;
      storeSession((await res.json()) as AuthResponse);
      return true;
    })().finally(() => {
      refreshInFlight = null;
    });
  }
  return refreshInFlight;
}

async function rawRequest(path: string, init: RequestInit): Promise<Response> {
  const headers = new Headers(init.headers);
  const token = getAccessToken();
  if (token) headers.set('Authorization', `Bearer ${token}`);
  if (init.body) headers.set('Content-Type', 'application/json');
  return fetch(`${API_BASE_URL}${path}`, { ...init, headers });
}

export async function apiFetch<T>(path: string, init: RequestInit = {}): Promise<T> {
  let res = await rawRequest(path, init);
  if (res.status === 401 && getAccessToken()) {
    if (await tryRefresh()) {
      res = await rawRequest(path, init);
    } else {
      clearSession();
      onSessionExpired();
    }
  }
  if (!res.ok) {
    let error: ApiError;
    try {
      error = (await res.json()) as ApiError;
    } catch {
      error = {
        timestamp: new Date().toISOString(),
        status: res.status,
        error: res.statusText,
        message: res.statusText,
        correlationId: null,
        path,
      };
    }
    throw new ApiRequestError(error);
  }
  if (res.status === 204) return undefined as T;
  return (await res.json()) as T;
}
