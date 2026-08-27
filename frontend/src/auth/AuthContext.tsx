import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react';
import type { ReactNode } from 'react';
import {
  clearSession,
  getAccessToken,
  setSessionExpiredHandler,
  storeSession,
} from '../api/client';
import { login as loginRequest } from '../api/endpoints';

interface JwtClaims {
  sub: string;
  email: string;
  name: string;
  job_title: string;
}

// El actor sale solo del JWT: el userId propio se decodifica del payload, nunca
// se pide ni se envía por separado.
function decodeClaims(token: string): JwtClaims | null {
  try {
    const payload = token.split('.')[1];
    return JSON.parse(atob(payload.replace(/-/g, '+').replace(/_/g, '/'))) as JwtClaims;
  } catch {
    return null;
  }
}

interface AuthState {
  userId: string | null;
  name: string | null;
  authenticated: boolean;
  login: (email: string, password: string) => Promise<void>;
  logout: () => void;
}

const AuthContext = createContext<AuthState | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [claims, setClaims] = useState<JwtClaims | null>(() => {
    const token = getAccessToken();
    return token ? decodeClaims(token) : null;
  });

  const logout = useCallback(() => {
    clearSession();
    setClaims(null);
  }, []);

  useEffect(() => {
    setSessionExpiredHandler(() => setClaims(null));
  }, []);

  const login = useCallback(async (email: string, password: string) => {
    const auth = await loginRequest(email, password);
    storeSession(auth);
    setClaims(decodeClaims(auth.accessToken));
  }, []);

  const value = useMemo<AuthState>(
    () => ({
      userId: claims?.sub ?? null,
      name: claims?.name ?? null,
      authenticated: claims !== null,
      login,
      logout,
    }),
    [claims, login, logout],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthState {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error('useAuth requiere AuthProvider');
  return ctx;
}
