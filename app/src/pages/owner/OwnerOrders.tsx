import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { get, patch, post, errMsg } from '../../lib/api';
import { money, when } from '../../lib/format';
import { Button, Card, Chip, Field, Input, Note, Spinner } from '../../components/ui';

const NEXT: Record<string, Record<string, string[]>> = {
  FOOD: { PLACED: ['ACCEPTED'], ACCEPTED: ['PROCESSING'], QUEUED: ['PROCESSING'], PROCESSING: ['READY'] },
  DESK: { PLACED: ['ACCEPTED'], ACCEPTED: ['PROCESSING'], QUEUED: ['PROCESSING'], PROCESSING: ['PRINTING', 'READY'], PRINTING: ['BINDING', 'READY'], BINDING: ['READY'] },
};
const TABS = ['PLACED', 'ACCEPTED', 'PROCESSING', 'PRINTING', 'BINDING', 'READY', 'COLLECTED'];

export default function OwnerOrders() {
  const qc = useQueryClient();
  const shop = useQuery({ queryKey: ['my-shop'], queryFn: () => get<any>('/shop-owner/shop'), retry: false });
  if (shop.isLoading) return <Spinner />;
  if (shop.isError || !shop.data) return <CreateShop onDone={() => qc.invalidateQueries({ queryKey: ['my-shop'] })} />;
  return <Queue shop={shop.data} />;
}

function CreateShop({ onDone }: { onDone: () => void }) {
  const [vertical, setVertical] = useState('DESK'); const [name, setName] = useState(''); const [err, setErr] = useState('');
  const create = useMutation({ mutationFn: () => post('/shop-owner/shop', { vertical, name }), onSuccess: onDone, onError: (e) => setErr(errMsg(e)) });
  return (
    <Card className="grid gap-3">
      <h1 className="text-xl font-bold">Set up your shop</h1>
      <Note tone="mute">After you create it, the platform team approves it before it can go live.</Note>
      <Field label="Shop type"><select className="w-full rounded-xl border border-line px-3 py-2.5 text-sm" value={vertical} onChange={(e) => setVertical(e.target.value)}><option value="DESK">Stationery / printing (CampusDesk)</option><option value="FOOD">Canteen / food</option></select></Field>
      <Field label="Shop name"><Input value={name} onChange={(e) => setName(e.target.value)} /></Field>
      <Note>{err}</Note><Button disabled={name.trim().length < 2} loading={create.isPending} onClick={() => create.mutate()}>Create shop</Button>
    </Card>
  );
}

function Queue({ shop }: { shop: any }) {
  const qc = useQueryClient(); const [tab, setTab] = useState('PLACED'); const [err, setErr] = useState(''); const [otps, setOtps] = useState<Record<number, string>>({});
  const orders = useQuery({ queryKey: ['owner-orders', tab], queryFn: () => get<any[]>('/shop-owner/orders', { status: tab }), refetchInterval: 10000 });
  const refresh = () => { setErr(''); qc.invalidateQueries({ queryKey: ['owner-orders'] }); qc.invalidateQueries({ queryKey: ['my-shop'] }); };
  const bad = (e: unknown) => setErr(errMsg(e));
  const move = useMutation({ mutationFn: (v: { id: number; status: string }) => post(`/shop-owner/orders/${v.id}/status`, { status: v.status }), onSuccess: refresh, onError: bad });
  const reject = useMutation({ mutationFn: (id: number) => post(`/shop-owner/orders/${id}/reject`, { reason: 'Unable to fulfil this order' }), onSuccess: refresh, onError: bad });
  const verify = useMutation({ mutationFn: (id: number) => post(`/shop-owner/orders/${id}/verify-pickup`, { otp: otps[id] }), onSuccess: refresh, onError: bad });
  const toggle = useMutation({ mutationFn: () => patch('/shop-owner/shop/status', { openStatus: shop.open_status === 'OPEN' ? 'CLOSED' : 'OPEN' }), onSuccess: refresh, onError: bad });
  return (
    <>
      <Card className="flex items-center justify-between gap-3">
        <div><h1 className="text-lg font-bold">{shop.name}</h1><p className="text-xs text-mute">{shop.approval === 'APPROVED' ? 'Approved' : `Waiting for approval (${shop.approval.toLowerCase()})`}</p></div>
        <Button variant="ghost" className="!py-1.5" loading={toggle.isPending} onClick={() => toggle.mutate()}>{shop.open_status === 'OPEN' ? 'Close shop' : 'Open shop'}</Button>
      </Card>
      <div className="-mx-1 flex gap-1 overflow-x-auto px-1">{TABS.filter((t) => shop.vertical === 'DESK' || !['PRINTING', 'BINDING'].includes(t)).map((t) => <button key={t} onClick={() => setTab(t)} className={`whitespace-nowrap rounded-full px-3 py-1.5 text-xs font-semibold ${tab === t ? 'bg-accent text-white' : 'bg-surface text-mute'}`}>{t}</button>)}</div>
      <Note>{err}</Note>
      {orders.isLoading && <Spinner />}
      {orders.data?.length === 0 && <Card><p className="text-sm text-mute">No orders here.</p></Card>}
      {orders.data?.map((o) => (
        <Card key={o.id} className="grid gap-2">
          <div className="flex justify-between"><b>#{o.order_code} · {o.student_name}</b><Chip>{o.status}</Chip></div>
          <p className="text-sm text-mute">{money(o.total_amount)} · advance {money(o.advance_amount)} paid · remaining {money(o.remaining_amount)} · {when(o.created_at)}</p>
          <div className="flex flex-wrap gap-2">
            {(NEXT[shop.vertical][o.status] ?? []).map((n) => <Button key={n} className="!py-1.5" loading={move.isPending} onClick={() => move.mutate({ id: o.id, status: n })}>{n === 'READY' ? 'Mark READY (sends OTP)' : `Move to ${n}`}</Button>)}
            {['PLACED', 'ACCEPTED', 'QUEUED', 'PROCESSING'].includes(o.status) && <Button variant="danger" className="!py-1.5" onClick={() => confirm('Reject this order and refund the student in full?') && reject.mutate(o.id)}>Reject</Button>}
          </div>
          {o.status === 'READY' && (
            <div className="flex gap-2"><Input inputMode="numeric" maxLength={6} placeholder="Student's 6-digit OTP" value={otps[o.id] ?? ''} onChange={(e) => setOtps((p) => ({ ...p, [o.id]: e.target.value }))} />
              <Button loading={verify.isPending} disabled={(otps[o.id] ?? '').length !== 6} onClick={() => verify.mutate(o.id)}>Hand over</Button></div>
          )}
        </Card>
      ))}
      
    </>
  );
}
