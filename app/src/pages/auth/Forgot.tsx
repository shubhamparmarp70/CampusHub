import { useEffect, useState } from 'react';
import { Link, useSearchParams, useNavigate } from 'react-router-dom';
import { post, errMsg } from '../../lib/api';
import { Button, Field, Input, Note } from '../../components/ui';
import { AuthShell } from './Login';

export function ForgotPassword() {
  const [email, setEmail] = useState(''); const [msg, setMsg] = useState(''); const [err, setErr] = useState(''); const [busy, setBusy] = useState(false);
  return (
    <AuthShell title="Forgot password" sub="We'll email you a reset link.">
      <form className="grid gap-3" onSubmit={async (e) => { e.preventDefault(); setBusy(true); setErr(''); try { await post('/auth/forgot-password', { email }); setMsg('If that email has an account, a reset link is on its way.'); } catch (x) { setErr(errMsg(x)); } setBusy(false); }}>
        <Field label="Email"><Input type="email" required value={email} onChange={(e) => setEmail(e.target.value)} /></Field>
        <Note>{err}</Note><Note tone="ok">{msg}</Note>
        <Button type="submit" loading={busy}>Send reset link</Button><Link to="/login" className="text-center text-sm text-accent">Back to sign in</Link>
      </form>
    </AuthShell>
  );
}
export function ResetPassword() {
  const [params] = useSearchParams(); const nav = useNavigate();
  const [pw, setPw] = useState(''); const [err, setErr] = useState(''); const [busy, setBusy] = useState(false);
  return (
    <AuthShell title="Choose a new password" sub="Use at least 8 characters.">
      <form className="grid gap-3" onSubmit={async (e) => { e.preventDefault(); setBusy(true); setErr(''); try { await post('/auth/reset-password', { token: params.get('token'), newPassword: pw }); nav('/login'); } catch (x) { setErr(errMsg(x)); } setBusy(false); }}>
        <Field label="New password"><Input type="password" minLength={8} required value={pw} onChange={(e) => setPw(e.target.value)} /></Field>
        <Note>{err}</Note><Button type="submit" loading={busy}>Update password</Button>
      </form>
    </AuthShell>
  );
}
export function VerifyEmail() {
  const [params] = useSearchParams(); const [msg, setMsg] = useState('Verifying…');
  useEffect(() => { post('/auth/verify-email', { token: params.get('token') }).then(() => setMsg('Email verified. You can close this page.')).catch((e) => setMsg(errMsg(e))); }, [params]);
  return <AuthShell title="Email verification" sub={msg}><Link to="/login" className="text-accent">Go to sign in</Link></AuthShell>;
}
