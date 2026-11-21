import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useForm } from 'react-hook-form';
import { z } from 'zod';
import { zodResolver } from '@hookform/resolvers/zod';
import { useAuth, homeFor } from '../../lib/auth';
import { errMsg } from '../../lib/api';
import { Button, Field, Input, Note } from '../../components/ui';
import { AuthShell } from './Login';

const schema = z.object({
  fullName: z.string().min(2, 'Enter your name'), email: z.string().email('Enter a valid email'),
  phone: z.string().regex(/^[0-9+\- ]{8,20}$/, 'Enter a valid phone number'),
  password: z.string().min(8, 'At least 8 characters').max(72), role: z.enum(['STUDENT', 'SHOP_OWNER']),
});
export default function Register() {
  const { register: signUp } = useAuth(); const nav = useNavigate(); const [err, setErr] = useState('');
  const f = useForm<z.infer<typeof schema>>({ resolver: zodResolver(schema), defaultValues: { role: 'STUDENT' } });
  const e = f.formState.errors;
  return (
    <AuthShell title="Create your account" sub="Students order. Shop owners run their shop.">
      <form className="grid gap-3" onSubmit={f.handleSubmit(async (v) => { try { setErr(''); const p = await signUp(v); nav('/verify-phone', { state: { email: p.email, maskedPhone: p.maskedPhone, wait: p.resendInSeconds } }); } catch (x) { setErr(errMsg(x)); } })}>
        <Field label="I am a"><select className="w-full rounded-xl border border-line px-3 py-2.5 text-sm" {...f.register('role')}><option value="STUDENT">Student</option><option value="SHOP_OWNER">Shop owner</option></select></Field>
        <Field label="Full name" error={e.fullName?.message}><Input {...f.register('fullName')} /></Field>
        <Field label="Email" error={e.email?.message}><Input type="email" {...f.register('email')} /></Field>
        <Field label="Mobile number (we'll text you a code)" error={e.phone?.message}><Input inputMode="tel" placeholder="98765 43210" {...f.register('phone')} /></Field>
        <Field label="Password" error={e.password?.message}><Input type="password" autoComplete="new-password" {...f.register('password')} /></Field>
        <Note>{err}</Note>
        <Button type="submit" loading={f.formState.isSubmitting}>Create account</Button>
        <Link to="/login" className="text-center text-sm text-accent">I already have an account</Link>
      </form>
    </AuthShell>
  );
}
