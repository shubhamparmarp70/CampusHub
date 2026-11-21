import { useEffect, useRef, useState } from 'react';
import { Link, Navigate, useLocation, useNavigate } from 'react-router-dom';
import { useAuth, homeFor } from '../../lib/auth';
import { api, errMsg } from '../../lib/api';
import { Button, Field, Input, Note } from '../../components/ui';
import { AuthShell } from './Login';

interface NavState { email?: string; maskedPhone?: string; wait?: number; sendNow?: boolean }

export default function VerifyPhone() {
  const state = useLocation().state as NavState | null;
  const { verifyPhone } = useAuth(); const nav = useNavigate();
  const email = state?.email;
  const [otp, setOtp] = useState(''); const [err, setErr] = useState(''); const [busy, setBusy] = useState(false);
  const [info, setInfo] = useState(state?.maskedPhone ? `We sent a 6-digit code to ${state.maskedPhone}.` : '');
  const [wait, setWait] = useState(state?.wait ?? 0); const sentOnce = useRef(false);

  useEffect(() => { if (wait <= 0) return; const t = setTimeout(() => setWait((w) => w - 1), 1000); return () => clearTimeout(t); }, [wait]);
  const resend = async () => {
    try {
      setErr(''); const r = (await api.post('/auth/resend-phone-otp', { email })).data.data;
      setWait(r.resendInSeconds ?? 60); setInfo(r.maskedPhone ? `We sent a new code to ${r.maskedPhone}.` : 'If that number is registered, a new code is on its way.');
    } catch (e) { setErr(errMsg(e)); }
  };
  useEffect(() => { if (state?.sendNow && email && !sentOnce.current) { sentOnce.current = true; resend(); } }, []);   // arrived from sign-in with an unverified number
  if (!email) return <Navigate to="/login" replace />;

  return (
    <AuthShell title="Verify your mobile" sub="Enter the code we texted you to finish creating your account.">
      <form className="grid gap-3" onSubmit={async (e) => { e.preventDefault(); setBusy(true); setErr(''); try { nav(homeFor((await verifyPhone(email, otp)).role)); } catch (x) { setErr(errMsg(x)); } setBusy(false); }}>
        <Note tone="mute">{info}</Note>
        <Field label="6-digit code"><Input inputMode="numeric" autoComplete="one-time-code" maxLength={6} placeholder="••••••" value={otp} onChange={(e) => setOtp(e.target.value.replace(/\D/g, ''))} className="text-center text-xl tracking-[0.4em]" /></Field>
        <Note>{err}</Note>
        <Button type="submit" loading={busy} disabled={otp.length !== 6}>Verify and continue</Button>
        <Button type="button" variant="ghost" disabled={wait > 0} onClick={resend}>{wait > 0 ? `Send a new code in ${wait}s` : 'Send a new code'}</Button>
        <Link to="/login" className="text-center text-sm text-accent">Back to sign in</Link>
      </form>
    </AuthShell>
  );
}
