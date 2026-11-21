import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { get, patch, errMsg } from '../../lib/api';
import { post } from '../../lib/api';
import { money } from '../../lib/format';
import { Bars, Button, Card, Chip, Input, Note, Row, Spinner, Stat } from '../../components/ui';

export default function AdminHome() {
  const qc = useQueryClient(); const [err, setErr] = useState('');
  const shops = useQuery({ queryKey: ['admin-shops'], queryFn: () => get<any[]>('/admin/shops') });
  const users = useQuery({ queryKey: ['admin-users'], queryFn: () => get<any[]>('/admin/users') });
  const stats = useQuery({ queryKey: ['admin-stats'], queryFn: () => get<any>('/admin/analytics') });
  const done = { onSuccess: () => { setErr(''); qc.invalidateQueries(); }, onError: (e: unknown) => setErr(errMsg(e)) };
  const approve = useMutation({ mutationFn: (v: { id: number; approval: string }) => patch(`/admin/shops/${v.id}/approval`, { approval: v.approval }), ...done });
  const suspend = useMutation({ mutationFn: (v: { id: number; status: string }) => patch(`/admin/users/${v.id}/status`, { status: v.status }), ...done });
  const cats = useQuery({ queryKey: ['categories', 'all'], queryFn: () => get<any[]>('/categories') });
  const [cat, setCat] = useState({ vertical: 'DESK', name: '' });
  const addCat = useMutation({ mutationFn: () => post('/admin/categories', { vertical: cat.vertical, name: cat.name.trim() }), onSuccess: () => { setCat({ ...cat, name: '' }); setErr(''); qc.invalidateQueries({ queryKey: ['categories'] }); }, onError: (e) => setErr(errMsg(e)) });
  if (shops.isLoading || users.isLoading) return <Spinner />;
  return (
    <>
      <h1 className="text-xl font-bold">Platform overview</h1>
      {stats.data && <>
        <div className="grid grid-cols-2 gap-2">
          <Stat label="Platform fees earned (30 days)" value={money(stats.data.platformFeesEarned30d)} sub={`${money(stats.data.platformFeesInProgress)} in progress`} />
          <Stat label="Money paid by students (30 days)" value={money(stats.data.grossPayments30d)} sub="before shop payouts" />
          <Stat label="Stuck payments" value={stats.data.stuckPayments} sub="pending for 60+ minutes" />
          <Stat label="Failed orders (7 days)" value={stats.data.failedOrders7d} />
        </div>
        <Card className="grid gap-2"><h2 className="font-bold">Orders by status</h2>
          <Bars items={(stats.data.ordersByStatus as any[]).map((r) => ({ label: r.status, value: Number(r.c) }))} /></Card>
      </>}
      <Note>{err}</Note>
      <Card><h2 className="mb-1 font-bold">Shops</h2>
        {shops.data?.map((s) => <Row key={s.id}><div><b className="text-sm">{s.name}</b><p className="text-xs text-mute">{s.vertical} · {s.owner_email} · {s.orders} orders</p></div>
          <div className="flex items-center gap-2"><Chip>{s.approval}</Chip><Button variant={s.approval === 'APPROVED' ? 'danger' : 'solid'} className="!px-3 !py-1.5" onClick={() => approve.mutate({ id: s.id, approval: s.approval === 'APPROVED' ? 'DISABLED' : 'APPROVED' })}>{s.approval === 'APPROVED' ? 'Disable' : 'Approve'}</Button></div></Row>)}
        {!shops.data?.length && <Note tone="mute">No shops yet.</Note>}
      </Card>
      <Card className="grid gap-2"><h2 className="font-bold">Categories</h2>
        <p className="text-xs text-mute">Owners pick from these when adding products. Add a few for each shop type.</p>
        <div className="flex flex-wrap gap-1.5">{cats.data?.map((c) => <Chip key={c.id}>{c.vertical === 'DESK' ? '🖨' : '🍽'} {c.name}</Chip>)}{!cats.data?.length && <Note tone="mute">None yet.</Note>}</div>
        <div className="flex gap-2"><select className="rounded-xl border border-line bg-page px-2 text-sm" value={cat.vertical} onChange={(e) => setCat({ ...cat, vertical: e.target.value })}><option value="DESK">CampusDesk</option><option value="FOOD">Canteen</option></select>
          <Input placeholder="e.g. Notebooks" value={cat.name} onChange={(e) => setCat({ ...cat, name: e.target.value })} /><Button disabled={cat.name.trim().length < 2} loading={addCat.isPending} onClick={() => addCat.mutate()}>Add</Button></div>
      </Card>
      <Card><h2 className="mb-1 font-bold">Users</h2>
        {users.data?.map((u) => <Row key={u.id}><div><b className="text-sm">{u.full_name}</b><p className="text-xs text-mute">{u.email} · {u.roles} · {u.status}</p></div>
          {!String(u.roles).includes('SUPER_ADMIN') && <Button variant="ghost" className="!px-3 !py-1.5" onClick={() => suspend.mutate({ id: u.id, status: u.status === 'SUSPENDED' ? 'ACTIVE' : 'SUSPENDED' })}>{u.status === 'SUSPENDED' ? 'Restore' : 'Suspend'}</Button>}</Row>)}
      </Card>
    </>
  );
}
