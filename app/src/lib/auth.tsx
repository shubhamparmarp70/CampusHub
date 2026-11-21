import { createContext, useContext, useEffect, useState, ReactNode } from 'react';
import { api, tokens, refreshSession, Session } from './api';

export type Role = 'student' | 'owner' | 'admin';
interface User { userId: number; fullName: string; roles: string[]; role: Role }
const toUser = (s: Session): User => ({ userId: s.userId, fullName: s.fullName, roles: s.roles, role: s.roles.includes('SUPER_ADMIN') ? 'admin' : s.roles.includes('SHOP_OWNER') ? 'owner' : 'student' });

export interface Pending { phoneVerificationRequired: boolean; email: string; maskedPhone: string; resendInSeconds: number }
interface Ctx { user: User | null; ready: boolean; login(email: string, password: string): Promise<User>; register(b: object): Promise<Pending>; verifyPhone(email: string, otp: string): Promise<User>; logout(): Promise<void> }
const AuthCtx = createContext<Ctx>(null as unknown as Ctx);
export const useAuth = () => useContext(AuthCtx);
export const homeFor = (r: Role) => (r === 'student' ? '/student/shops' : r === 'owner' ? '/owner/orders' : '/admin');

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<User | null>(null);
  const [ready, setReady] = useState(false);
  useEffect(() => {
    refreshSession().then((s) => { if (s) setUser(toUser(s)); setReady(true); });   // restore the session after a page reload
    const out = () => setUser(null);
    window.addEventListener('cd:logout', out);
    return () => window.removeEventListener('cd:logout', out);
  }, []);
  const accept = (s: Session) => { tokens.set(s.accessToken, s.refreshToken); const u = toUser(s); setUser(u); return u; };
  const value: Ctx = {
    user, ready,
    login: async (email, password) => accept((await api.post('/auth/login', { email, password })).data.data),
    register: async (b) => (await api.post('/auth/register', b)).data.data as Pending,       // no sign-in yet: the SMS code comes first
    verifyPhone: async (email, otp) => accept((await api.post('/auth/verify-phone', { email, otp })).data.data),
    logout: async () => { try { await api.post('/auth/logout', { refreshToken: tokens.refresh }); } catch { /* already signed out */ } tokens.clear(); setUser(null); },
  };
  return <AuthCtx.Provider value={value}>{children}</AuthCtx.Provider>;
}
