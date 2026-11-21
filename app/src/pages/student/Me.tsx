import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api, del, get, put, errMsg } from '../../lib/api';
import { useAuth } from '../../lib/auth';
import { money, when } from '../../lib/format';
import { Button, Card, Chip, Field, Input, Note, Row, Spinner, Tabs } from '../../components/ui';

export default function Me() {
  const [tab, setTab] = useState('profile'); const { logout } = useAuth();
  return (
    <>
      <h1 className="text-xl font-bold">Me</h1>
      <Tabs tabs={[['profile', 'Profile'], ['payments', 'Payments'], ['following', 'Following'], ['favorites', 'Favourites'], ['reviews', 'Reviews'], ['settings', 'Settings']]} value={tab} onChange={setTab} />
      {tab === 'profile' && <Profile />}{tab === 'payments' && <Payments />}{tab === 'following' && <Following />}
      {tab === 'favorites' && <Favorites />}{tab === 'reviews' && <Reviews />}{tab === 'settings' && <Settings />}
      <Button variant="ghost" onClick={logout}>Log out</Button>
    </>
  );
}

function Profile() {
  const qc = useQueryClient(); const q = useQuery({ queryKey: ['profile'], queryFn: () => get<any>('/student/profile') });
  const [f, setF] = useState({ fullName: '', phone: '', collegeName: '', department: '', semester: '', studentCode: '' });
  const [msg, setMsg] = useState(''); const [err, setErr] = useState('');
  useEffect(() => { const d = q.data; if (d) setF({ fullName: d.fullName ?? '', phone: d.phone ?? '', collegeName: d.collegeName ?? '', department: d.department ?? '', semester: d.semester == null ? '' : String(d.semester), studentCode: d.studentCode ?? '' }); }, [q.data]);
  const save = useMutation({
    mutationFn: () => put('/student/profile', { fullName: f.fullName, semester: f.semester === '' ? null : Number(f.semester), collegeName: f.collegeName || null, department: f.department || null, studentCode: f.studentCode || null }),
    onSuccess: () => { setMsg('Profile saved.'); setErr(''); qc.invalidateQueries({ queryKey: ['profile'] }); }, onError: (e) => { setErr(errMsg(e)); setMsg(''); },
  });
  const photo = useMutation({
    mutationFn: async (file: File) => { const fd = new FormData(); fd.append('file', file); return api.post('/student/profile/photo', fd); },
    onSuccess: () => { setMsg('Photo updated.'); setErr(''); qc.invalidateQueries({ queryKey: ['profile'] }); }, onError: (e) => { setErr(errMsg(e)); setMsg(''); },
  });
  if (q.isLoading) return <Spinner />;
  const set = (k: string, v: string) => setF((p) => ({ ...p, [k]: v }));
  return (
    <Card className="grid gap-3">
      <div className="flex items-center gap-3">
        {q.data?.photoUrl ? <img src={q.data.photoUrl} alt="Your profile" className="h-16 w-16 rounded-full object-cover" />
          : <div className="flex h-16 w-16 items-center justify-center rounded-full bg-accent text-2xl font-bold text-white">{(f.fullName || '?').charAt(0).toUpperCase()}</div>}
        <label className="text-sm text-accent"><span className="cursor-pointer font-semibold">{photo.isPending ? 'Uploading…' : 'Change photo'}</span>
          <input type="file" accept="image/jpeg,image/png" className="hidden" onChange={(e) => e.target.files?.[0] && photo.mutate(e.target.files[0])} /></label>
      </div>
      <Field label="Full name"><Input value={f.fullName} onChange={(e) => set('fullName', e.target.value)} /></Field>
      <Field label="Email"><Input value={q.data?.email ?? ''} disabled /></Field>
      <Field label="Mobile number (verified)"><Input value={f.phone} disabled /></Field>
      <Field label="College"><Input value={f.collegeName} onChange={(e) => set('collegeName', e.target.value)} /></Field>
      <div className="grid grid-cols-2 gap-2">
        <Field label="Department"><Input value={f.department} onChange={(e) => set('department', e.target.value)} /></Field>
        <Field label="Semester"><Input type="number" min={1} max={12} value={f.semester} onChange={(e) => set('semester', e.target.value)} /></Field>
      </div>
      <Field label="Student ID"><Input value={f.studentCode} onChange={(e) => set('studentCode', e.target.value)} /></Field>
      <Note tone="mute">College details are only for your profile. Shops and the platform use them for nothing else.</Note>
      <Note>{err}</Note><Note tone="ok">{msg}</Note>
      <Button disabled={f.fullName.trim().length < 2} loading={save.isPending} onClick={() => save.mutate()}>Save profile</Button>
    </Card>
  );
}

function Payments() {
  const q = useQuery({ queryKey: ['my-payments'], queryFn: () => get<any[]>('/student/payments') });
  if (q.isLoading) return <Spinner />;
  return (
    <Card>
      {!q.data?.length && <Note tone="mute">No payments yet.</Note>}
      {q.data?.map((p) => (
        <Row key={p.id}>
          <div><b className="text-sm">#{p.order_code} · {p.payment_type.toLowerCase()}</b><p className="text-xs text-mute">{p.payment_method === 'GOOGLE_PAY' ? 'Google Pay' : 'PhonePe'} · {when(p.completed_at ?? p.created_at)}</p></div>
          <div className="text-right"><b className={`block text-sm ${p.payment_type === 'REFUND' ? 'text-ok' : ''}`}>{p.payment_type === 'REFUND' ? '+' : ''}{money(p.amount)}</b><Chip>{p.status}</Chip></div>
        </Row>
      ))}
    </Card>
  );
}

function Following() {
  const qc = useQueryClient(); const q = useQuery({ queryKey: ['following'], queryFn: () => get<any[]>('/student/following') });
  const unfollow = useMutation({ mutationFn: (id: number) => del(`/student/shops/${id}/follow`), onSuccess: () => qc.invalidateQueries({ queryKey: ['following'] }) });
  if (q.isLoading) return <Spinner />;
  return (
    <Card>
      {!q.data?.length && <Note tone="mute">You don't follow any shops yet. Open a shop and tap Follow to hear about its offers.</Note>}
      {q.data?.map((s) => (
        <Row key={s.id}>
          <Link to={`/student/shops/${s.id}`} className="min-w-0"><b className="block truncate text-sm">{s.name}</b><p className="text-xs text-mute">{s.vertical === 'DESK' ? 'CampusDesk' : 'Canteen'} · {s.open_status === 'OPEN' ? 'Open' : 'Closed'}</p></Link>
          <Button variant="ghost" className="!px-3 !py-1.5" onClick={() => unfollow.mutate(s.id)}>Unfollow</Button>
        </Row>
      ))}
    </Card>
  );
}

function Favorites() {
  const qc = useQueryClient(); const q = useQuery({ queryKey: ['favorites'], queryFn: () => get<any[]>('/student/favorites') });
  const remove = useMutation({ mutationFn: (id: number) => del(`/student/favorites/${id}`), onSuccess: () => qc.invalidateQueries({ queryKey: ['favorites'] }) });
  if (q.isLoading) return <Spinner />;
  return (
    <Card>
      {!q.data?.length && <Note tone="mute">No favourites yet. Tap the heart on a product to save it.</Note>}
      {q.data?.map((p) => (
        <Row key={p.product_id}>
          <Link to={`/student/shops/${p.shop_id}`} className="min-w-0"><b className="block truncate text-sm">{p.name}</b><p className="text-xs text-mute">{p.shop_name} · {money(p.selling_price)}{!Number(p.in_stock) && ' · out of stock'}</p></Link>
          <Button variant="ghost" className="!px-3 !py-1.5" onClick={() => remove.mutate(p.product_id)}>Remove</Button>
        </Row>
      ))}
    </Card>
  );
}

function Reviews() {
  const q = useQuery({ queryKey: ['my-reviews'], queryFn: () => get<any[]>('/student/reviews') });
  if (q.isLoading) return <Spinner />;
  return (
    <Card>
      {!q.data?.length && <Note tone="mute">You haven't reviewed any shops yet. You can review after you collect an order.</Note>}
      {q.data?.map((r) => (
        <Row key={r.id}>
          <Link to={`/student/orders/${r.order_id}`} className="min-w-0"><b className="block text-sm">{r.shop_name} <span className="text-accent">{'★'.repeat(r.rating)}</span></b>{r.comment && <p className="truncate text-xs text-mute">{r.comment}</p>}<p className="text-xs text-mute">{when(r.created_at)}</p></Link>
        </Row>
      ))}
    </Card>
  );
}

const PREFS: [string, string, string][] = [
  ['orderUpdates', 'Order updates', 'Accepted, printing, ready, collected'], ['paymentUpdates', 'Payment updates', 'Payments and refunds'],
  ['importantShopUpdates', 'Important shop updates', 'Announcements from shops you follow'], ['newDiscounts', 'New discounts', 'Offers from shops you follow'],
  ['newProducts', 'New products', 'New items at shops you follow'], ['promotionalOffers', 'Promotional offers', 'Marketing messages'], ['lowPriceDeals', 'Low price deals', 'Price drops'],
];
function Settings() {
  const qc = useQueryClient(); const q = useQuery({ queryKey: ['prefs'], queryFn: () => get<any>('/student/notification-preferences') });
  const presets = useQuery({ queryKey: ['presets'], queryFn: () => get<any[]>('/student/print-presets') });
  const [p, setP] = useState<Record<string, boolean>>({}); const [msg, setMsg] = useState(''); const [err, setErr] = useState('');
  useEffect(() => { if (q.data) setP(Object.fromEntries(PREFS.map(([k]) => [k, !!q.data[k]]))); }, [q.data]);
  const save = useMutation({ mutationFn: () => put('/student/notification-preferences', p), onSuccess: () => { setMsg('Preferences saved.'); setErr(''); qc.invalidateQueries({ queryKey: ['prefs'] }); }, onError: (e) => { setErr(errMsg(e)); setMsg(''); } });
  const rmPreset = useMutation({ mutationFn: (id: number) => del(`/student/print-presets/${id}`), onSuccess: () => qc.invalidateQueries({ queryKey: ['presets'] }) });
  if (q.isLoading) return <Spinner />;
  return (
    <>
      <Card className="grid gap-2">
        <h2 className="font-bold">Notifications</h2>
        {PREFS.map(([k, label, hint]) => (
          <label key={k} className="flex items-center justify-between gap-3 border-t border-line py-2 first:border-t-0">
            <span><b className="block text-sm">{label}</b><span className="text-xs text-mute">{hint}</span></span>
            <input type="checkbox" className="h-5 w-5" checked={!!p[k]} onChange={(e) => setP({ ...p, [k]: e.target.checked })} />
          </label>
        ))}
        <Note>{err}</Note><Note tone="ok">{msg}</Note>
        <Button loading={save.isPending} onClick={() => save.mutate()}>Save preferences</Button>
      </Card>
      <Card><h2 className="mb-1 font-bold">Saved print settings</h2>
        {!presets.data?.length && <Note tone="mute">None yet. Save your usual print options from a shop's print form.</Note>}
        {presets.data?.map((s) => (
          <Row key={s.id}><div><b className="text-sm">{s.name}</b><p className="text-xs text-mute">{s.config.size} · {s.config.color === 'COLOR' ? 'Colour' : 'B&W'} · {s.config.sides === 'DOUBLE' ? 'double-sided' : 'single-sided'} · {s.config.copies} copy</p></div>
            <Button variant="danger" className="!px-3 !py-1.5" onClick={() => rmPreset.mutate(s.id)}>Delete</Button></Row>
        ))}
      </Card>
    </>
  );
}
