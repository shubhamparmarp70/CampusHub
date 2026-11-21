import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useForm } from 'react-hook-form';
import { z } from 'zod';
import { zodResolver } from '@hookform/resolvers/zod';
import { useAuth, homeFor } from '../../lib/auth';
import { errCode, errMsg } from '../../lib/api';
import { Button, Field, Input, Note } from '../../components/ui';

const schema = z.object({ email: z.string().email('Enter a valid email'), password: z.string().min(1, 'Enter your password') });
export default function Login() {
  const { login } = useAuth(); const nav = useNavigate(); const [err, setErr] = useState('');
  const f = useForm<z.infer<typeof schema>>({ resolver: zodResolver(schema) });
  return (
    <AuthShell title="Welcome back" sub="Order. Print. Pick up. Without the queue.">
      <form className="grid gap-3" onSubmit={f.handleSubmit(async (v) => { try { setErr(''); nav(homeFor((await login(v.email, v.password)).role)); } catch (e) { if (errCode(e) === 'PHONE_NOT_VERIFIED') nav('/verify-phone', { state: { email: v.email, sendNow: true } }); else setErr(errMsg(e)); } })}>
        <Field label="Email" error={f.formState.errors.email?.message}><Input type="email" autoComplete="username" {...f.register('email')} /></Field>
        <Field label="Password" error={f.formState.errors.password?.message}><Input type="password" autoComplete="current-password" {...f.register('password')} /></Field>
        <Note>{err}</Note>
        <Button type="submit" loading={f.formState.isSubmitting}>Sign in</Button>
        <div className="flex justify-between text-sm"><Link to="/forgot-password" className="text-accent">Forgot password?</Link><Link to="/register" className="text-accent">Create account</Link></div>
      </form>
    </AuthShell>
  );
}
export function AuthShell({ title, sub, children }: { title: string; sub: string; children: React.ReactNode }) {
  return (
    <div className="mx-auto flex min-h-full max-w-md flex-col justify-center gap-5 p-6">
      <div><h1 className="text-3xl font-extrabold tracking-tight">{title}</h1><p className="text-mute">{sub}</p></div>{children}
    </div>
  );
}
