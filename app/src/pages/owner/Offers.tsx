import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { get, patch, post, errMsg } from '../../lib/api';
import { localToUtc, useMyShop, utcToLocalInput } from '../../lib/hooks';
import { money, when } from '../../lib/format';
import { Button, Card, Chip, Field, Input, Note, Row, Spinner } from '../../components/ui';
import ApprovalGate from '../../components/ApprovalGate';

const sel = 'w-full rounded-xl border border-line px-3 py-2.5 text-sm';

export default function Offers() {
  const qc = useQueryClient(); const shop = useMyShop(); const [open, setOpen] = useState(false); const [err, setErr] = useState('');
  const list = useQuery({ queryKey: ['owner-offers'], queryFn: () => get<any[]>('/shop-owner/discounts') });
  const toggle = useMutation({ mutationFn: (d: any) => patch(`/shop-owner/discounts/${d.id}/active`, { active: !d.active }), onSuccess: () => qc.invalidateQueries({ queryKey: ['owner-offers'] }), onError: (e) => setErr(errMsg(e)) });
  if (list.isLoading || shop.isLoading) return <Spinner />;
  if (!shop.data) return <Card><Note tone="mute">Create your shop first on the Orders tab.</Note></Card>;
  if (shop.data.approval !== 'APPROVED') return <><h1 className="text-xl font-bold">Offers</h1><ApprovalGate shop={shop.data} what="Creating offers" /></>;
  return (
    <>
      <h1 className="text-xl font-bold">Offers</h1>
      {open ? <OfferForm shop={shop.data} onDone={() => { setOpen(false); qc.invalidateQueries({ queryKey: ['owner-offers'] }); }} onCancel={() => setOpen(false)} /> : <Button onClick={() => setOpen(true)}>+ New offer</Button>}
      <Note>{err}</Note>
      <Card>
        {!list.data?.length && <Note tone="mute">No offers yet.</Note>}
        {list.data?.map((d) => (
          <Row key={d.id}>
            <div className="min-w-0">
              <b className="block text-sm">{d.name} <Chip>{d.code}</Chip></b>
              <p className="text-xs text-mute">{d.type === 'PERCENT' ? `${d.value}% off` : `${money(d.value)} off`}{Number(d.min_order_amount) > 0 ? ` over ${money(d.min_order_amount)}` : ''} · used {d.used_count}{d.max_usage ? `/${d.max_usage}` : ''}</p>
              <p className="text-xs text-mute">{when(d.starts_at)} → {when(d.ends_at)}</p>
            </div>
            <Button variant={d.active ? 'ghost' : 'solid'} className="!px-3 !py-1.5" onClick={() => toggle.mutate(d)}>{d.active ? 'Turn off' : 'Turn on'}</Button>
          </Row>
        ))}
      </Card>
      <Announce />
    </>
  );
}

function OfferForm({ shop, onDone, onCancel }: { shop: any; onDone: () => void; onCancel: () => void }) {
  const products = useQuery({ queryKey: ['owner-products'], queryFn: () => get<any[]>('/shop-owner/products') });
  const [f, setF] = useState({ name: '', description: '', code: '', type: 'PERCENT', value: '', minOrderAmount: '', maxUsage: '', startsAt: utcToLocalInput(new Date()), endsAt: utcToLocalInput(new Date(Date.now() + 7 * 864e5)) });
  const [targets, setTargets] = useState<string[]>([]); const [err, setErr] = useState('');
  const set = (k: string, v: string) => setF((p) => ({ ...p, [k]: v }));
  const toggleT = (t: string) => setTargets((p) => (p.includes(t) ? p.filter((x) => x !== t) : [...p, t]));
  const create = useMutation({
    mutationFn: () => post('/shop-owner/discounts', {
      name: f.name.trim(), description: f.description || null, code: f.code.trim() || null, type: f.type, value: Number(f.value),
      minOrderAmount: f.minOrderAmount === '' ? 0 : Number(f.minOrderAmount), maxUsage: f.maxUsage === '' ? null : Number(f.maxUsage),
      appliesTo: targets, startsAt: localToUtc(f.startsAt), endsAt: localToUtc(f.endsAt),
    }),
    onSuccess: onDone, onError: (e) => setErr(errMsg(e)),
  });
  const valid = f.name.trim().length > 1 && Number(f.value) > 0 && f.startsAt && f.endsAt;
  return (
    <Card className="grid gap-3">
      <h2 className="font-bold">New offer</h2>
      <Field label="Offer name"><Input placeholder="20% off spiral binding" value={f.name} onChange={(e) => set('name', e.target.value)} /></Field>
      <Field label="Description (optional)"><Input value={f.description} onChange={(e) => set('description', e.target.value)} /></Field>
      <div className="grid grid-cols-2 gap-2">
        <Field label="Discount type"><select className={sel} value={f.type} onChange={(e) => set('type', e.target.value)}><option value="PERCENT">Percentage</option><option value="FIXED">Fixed amount (₹)</option></select></Field>
        <Field label={f.type === 'PERCENT' ? 'Percent off' : 'Rupees off'}><Input type="number" min={0} step="0.5" value={f.value} onChange={(e) => set('value', e.target.value)} /></Field>
        <Field label="Minimum order (₹, optional)"><Input type="number" min={0} value={f.minOrderAmount} onChange={(e) => set('minOrderAmount', e.target.value)} /></Field>
        <Field label="Max uses (optional)"><Input type="number" min={1} value={f.maxUsage} onChange={(e) => set('maxUsage', e.target.value)} /></Field>
        <Field label="Starts"><Input type="datetime-local" value={f.startsAt} onChange={(e) => set('startsAt', e.target.value)} /></Field>
        <Field label="Ends"><Input type="datetime-local" value={f.endsAt} onChange={(e) => set('endsAt', e.target.value)} /></Field>
      </div>
      <Field label="Code (optional, we'll make one if blank)"><Input value={f.code} onChange={(e) => set('code', e.target.value.toUpperCase())} /></Field>
      <div>
        <span className="mb-1 block text-xs text-mute">Applies to (tick nothing = the whole order)</span>
        <div className="grid max-h-40 gap-1 overflow-auto rounded-xl border border-line p-2 text-sm">
          {shop.vertical === 'DESK' && <label className="flex items-center gap-2"><input type="checkbox" checked={targets.includes('PRINTING')} onChange={() => toggleT('PRINTING')} />Printing jobs</label>}
          {products.data?.map((p) => <label key={p.id} className="flex items-center gap-2"><input type="checkbox" checked={targets.includes(String(p.id))} onChange={() => toggleT(String(p.id))} />{p.name}</label>)}
        </div>
      </div>
      <Note>{err}</Note>
      <div className="flex gap-2"><Button className="flex-1" disabled={!valid} loading={create.isPending} onClick={() => create.mutate()}>Create offer</Button><Button variant="ghost" onClick={onCancel}>Cancel</Button></div>
    </Card>
  );
}

function Announce() {
  const [f, setF] = useState({ category: 'DISCOUNT', title: '', body: '' }); const [msg, setMsg] = useState(''); const [err, setErr] = useState('');
  const send = useMutation({
    mutationFn: () => post<number>('/shop-owner/announcements', { category: f.category, title: f.title.trim(), body: f.body || null }),
    onSuccess: (n) => { setMsg(`Sent to ${n} follower${n === 1 ? '' : 's'} (only those who allow this kind of update will see it).`); setErr(''); setF({ ...f, title: '', body: '' }); },
    onError: (e) => { setErr(errMsg(e)); setMsg(''); },
  });
  return (
    <Card className="grid gap-3">
      <h2 className="font-bold">Tell your followers</h2>
      <Note tone="mute">Up to 3 announcements a day.</Note>
      <select className={sel} value={f.category} onChange={(e) => setF({ ...f, category: e.target.value })}>
        <option value="DISCOUNT">New discount</option><option value="NEW_PRODUCT">New product</option><option value="SHOP_UPDATE">Shop update</option><option value="PROMO">Promotion</option>
      </select>
      <Input placeholder="Title" maxLength={160} value={f.title} onChange={(e) => setF({ ...f, title: e.target.value })} />
      <Input placeholder="Message (optional)" maxLength={500} value={f.body} onChange={(e) => setF({ ...f, body: e.target.value })} />
      <Note>{err}</Note><Note tone="ok">{msg}</Note>
      <Button disabled={f.title.trim().length < 2} loading={send.isPending} onClick={() => send.mutate()}>Send</Button>
    </Card>
  );
}
