import axios, { AxiosError, InternalAxiosRequestConfig } from 'axios';

// Access token lives in memory only; the refresh token is kept in localStorage.
// (For the Capacitor mobile build, move the refresh token to secure storage.)
let accessToken: string | null = null;
export const tokens = {
  set(access: string, refresh: string) { accessToken = access; localStorage.setItem('cd_refresh', refresh); },
  clear() { accessToken = null; localStorage.removeItem('cd_refresh'); },
  get refresh() { return localStorage.getItem('cd_refresh'); },
};

export const api = axios.create({ baseURL: '/api' });
api.interceptors.request.use((cfg) => { if (accessToken) cfg.headers.Authorization = `Bearer ${accessToken}`; return cfg; });

export interface Session { accessToken: string; refreshToken: string; userId: number; fullName: string; roles: string[] }

// One refresh at a time. Refresh tokens rotate, so two parallel refreshes would look like token theft to the server.
let refreshing: Promise<Session | null> | null = null;
export function refreshSession(): Promise<Session | null> {
  if (refreshing) return refreshing;
  const r = tokens.refresh;
  if (!r) return Promise.resolve(null);
  refreshing = axios.post('/api/auth/refresh', { refreshToken: r })
    .then((res) => { const d = res.data.data as Session; tokens.set(d.accessToken, d.refreshToken); return d; })
    .catch(() => { tokens.clear(); return null; })
    .finally(() => { refreshing = null; });
  return refreshing;
}

api.interceptors.response.use((r) => r, async (err: AxiosError) => {
  const cfg = err.config as (InternalAxiosRequestConfig & { _retry?: boolean }) | undefined;
  if (err.response?.status === 401 && cfg && !cfg._retry && !cfg.url?.startsWith('/auth/')) {
    cfg._retry = true;
    const s = await refreshSession();
    if (s) { cfg.headers.Authorization = `Bearer ${s.accessToken}`; return api(cfg); }
    window.dispatchEvent(new Event('cd:logout'));
  }
  return Promise.reject(err);
});

export function errMsg(e: unknown): string {
  if (axios.isAxiosError(e)) return (e.response?.data as any)?.message ?? 'Network problem. Please try again.';
  return 'Something went wrong.';
}
export const errCode = (e: unknown): string | undefined => (axios.isAxiosError(e) ? (e.response?.data as any)?.data?.code : undefined);
export const get = async <T,>(url: string, params?: object) => (await api.get(url, { params })).data.data as T;
export const post = async <T,>(url: string, body?: object) => (await api.post(url, body)).data.data as T;
export const put = async <T,>(url: string, body?: object) => (await api.put(url, body)).data.data as T;
export const patch = async <T,>(url: string, body?: object) => (await api.patch(url, body)).data.data as T;
export const del = async <T,>(url: string) => (await api.delete(url)).data.data as T;
