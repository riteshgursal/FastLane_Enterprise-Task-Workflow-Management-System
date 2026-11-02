import { createContext, useCallback, useContext, useEffect, useState, type ReactNode } from 'react';
import { useQueryClient } from '@tanstack/react-query';
import { api, onSessionExpired, refreshSession, setAccessToken } from '../lib/api';
import type { AuthResponse, User } from '../lib/types';
import { useTheme } from './ThemeContext';

interface AuthState {
  user: User | null;
  loading: boolean;
  login: (email: string, password: string) => Promise<void>;
  register: (displayName: string, email: string, password: string) => Promise<void>;
  logout: () => Promise<void>;
  setUser: (u: User) => void;
}

const AuthContext = createContext<AuthState>(null as unknown as AuthState);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<User | null>(null);
  const [loading, setLoading] = useState(true);
  const qc = useQueryClient();
  const { setTheme } = useTheme();

  const accept = useCallback(
    (res: AuthResponse) => {
      setAccessToken(res.accessToken);
      setUser(res.user);
      if (res.user.theme) setTheme(res.user.theme);
    },
    [setTheme],
  );

  useEffect(() => {
    onSessionExpired(() => {
      setAccessToken(null);
      setUser(null);
      qc.clear();
    });
    refreshSession()
      .then((res) => res && accept(res))
      .finally(() => setLoading(false));
  }, [accept, qc]);

  const login = async (email: string, password: string) => {
    accept(await api<AuthResponse>('/auth/login', { body: { email, password } }));
  };

  const register = async (displayName: string, email: string, password: string) => {
    accept(await api<AuthResponse>('/auth/register', { body: { displayName, email, password } }));
  };

  const logout = async () => {
    try {
      await api('/auth/logout', { method: 'POST', csrf: true });
    } finally {
      setAccessToken(null);
      setUser(null);
      qc.clear();
    }
  };

  return <AuthContext.Provider value={{ user, loading, login, register, logout, setUser }}>{children}</AuthContext.Provider>;
}

export const useAuth = () => useContext(AuthContext);
