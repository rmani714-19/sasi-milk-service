import axios, { AxiosError, type AxiosRequestConfig } from 'axios';

const ACCESS_KEY = 'sasi.accessToken';
const REFRESH_KEY = 'sasi.refreshToken';

export function getAccessToken(): string | null {
  return localStorage.getItem(ACCESS_KEY);
}
export function getRefreshToken(): string | null {
  return localStorage.getItem(REFRESH_KEY);
}
export function setTokens(access: string, refresh: string) {
  localStorage.setItem(ACCESS_KEY, access);
  localStorage.setItem(REFRESH_KEY, refresh);
}
export function clearTokens() {
  localStorage.removeItem(ACCESS_KEY);
  localStorage.removeItem(REFRESH_KEY);
}

export const api = axios.create({ baseURL: '/api/v1' });

api.interceptors.request.use((config) => {
  const token = getAccessToken();
  if (token) config.headers.Authorization = `Bearer ${token}`;
  return config;
});

let refreshing: Promise<string | null> | null = null;

async function refreshAccessToken(): Promise<string | null> {
  const refreshToken = getRefreshToken();
  if (!refreshToken) return null;
  try {
    const { data } = await axios.post('/api/v1/auth/refresh', { refreshToken });
    setTokens(data.accessToken, data.refreshToken);
    return data.accessToken as string;
  } catch {
    clearTokens();
    return null;
  }
}

api.interceptors.response.use(
  (r) => r,
  async (error: AxiosError) => {
    const original = error.config as AxiosRequestConfig & { _retried?: boolean };
    const status = error.response?.status;
    const isAuthCall = original?.url?.includes('/auth/');

    if (status === 401 && original && !original._retried && !isAuthCall) {
      original._retried = true;
      refreshing = refreshing ?? refreshAccessToken();
      const newToken = await refreshing;
      refreshing = null;
      if (newToken) {
        original.headers = { ...original.headers, Authorization: `Bearer ${newToken}` };
        return api(original);
      }
      window.dispatchEvent(new CustomEvent('sasi:logout'));
    }
    return Promise.reject(error);
  },
);

/** Turn an axios error into a readable message (RFC-7807 aware). */
export function errorMessage(err: unknown): string {
  if (axios.isAxiosError(err)) {
    const data = err.response?.data as { detail?: string; errors?: { field: string; message: string }[] } | undefined;
    if (data?.errors?.length) return data.errors.map((e) => `${e.field}: ${e.message}`).join('; ');
    if (data?.detail) return data.detail;
    return err.message;
  }
  return err instanceof Error ? err.message : 'Something went wrong';
}
