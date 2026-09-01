import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from 'react';
import { api, clearTokens, getAccessToken, setTokens } from './api';

export interface CurrentUser {
  id: string;
  username: string;
  fullName: string;
  email?: string;
  mustChangePassword: boolean;
  permissions: string[];
}

interface AuthState {
  user: CurrentUser | null;
  initializing: boolean;
  login: (username: string, password: string) => Promise<void>;
  logout: () => void;
  can: (permission: string) => boolean;
}

const AuthContext = createContext<AuthState | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<CurrentUser | null>(null);
  const [initializing, setInitializing] = useState(true);

  const loadMe = useCallback(async () => {
    const { data } = await api.get<CurrentUser>('/users/me');
    setUser(data);
  }, []);

  useEffect(() => {
    let active = true;
    (async () => {
      if (getAccessToken()) {
        try {
          await loadMe();
        } catch {
          clearTokens();
        }
      }
      if (active) setInitializing(false);
    })();
    const onLogout = () => {
      clearTokens();
      setUser(null);
    };
    window.addEventListener('sasi:logout', onLogout);
    return () => {
      active = false;
      window.removeEventListener('sasi:logout', onLogout);
    };
  }, [loadMe]);

  const login = useCallback(
    async (username: string, password: string) => {
      const { data } = await api.post('/auth/login', { username, password });
      setTokens(data.accessToken, data.refreshToken);
      await loadMe();
    },
    [loadMe],
  );

  const logout = useCallback(() => {
    clearTokens();
    setUser(null);
  }, []);

  const can = useCallback(
    (permission: string) => !!user && (user.permissions.includes(permission) || user.permissions.includes('ALL')),
    [user],
  );

  const value = useMemo<AuthState>(
    () => ({ user, initializing, login, logout, can }),
    [user, initializing, login, logout, can],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthState {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error('useAuth must be used within AuthProvider');
  return ctx;
}
