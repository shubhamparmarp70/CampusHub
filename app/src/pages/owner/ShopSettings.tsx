import { useEffect, useState } from 'react';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { put, errMsg } from '../../lib/api';
import { useMyShop } from '../../lib/hooks';
import { Button, Card, Field, Input, Note, Spinner } from '../../components/ui';

export default function ShopSettings() {
  const qc = useQueryClient(); const shop = useMyShop();
  const [f, setF] = useState({ name: '', description: '', address: '', phone: '' });
  const [p, setP] = useState({ beforeProcessingPercent: '100', duringProcessingPercent: '50', afterPrintingPercent: '0', readyAllowed: false });
  const [msg, setMsg] = useState(''); const [err, setErr] = useState('');
  useEffect(() => {
    const s = shop.data; if (!s) return;
    setF({ name: s.name ?? '', description: s.description ?? '', address: s.address ?? '', phone: s.phone ?? '' });
    if (s.cancellation_policy) { try { const j = typeof s.cancellation_policy === 'string' ? JSON.parse(s.cancellation_policy) : s.cancellation_policy;
      setP({ beforeProcessingPercent: String(j.beforeProcessingPercent ?? 100), duringProcessingPercent: String(j.duringProcessingPercent ?? 50), afterPrintingPercent: String(j.afterPrintingPercent ?? 0), readyAllowed: !!j.readyAllowed }); } catch { /* keep defaults */ } }
  }, [shop.data]);
  const done = (m: string) => () => { setMsg(m); setErr(''); qc.invalidateQueries({ queryKey: ['my-shop'] }); };
  const bad = (e: unknown) => { setErr(errMsg(e)); setMsg(''); };
  const saveShop = useMutation({ mutationFn: () => put('/shop-owner/shop', { name: f.name.trim(), description: f.description, address: f.address, phone: f.phone, openingHours: shop.data?.opening_hours ?? null, logoKey: shop.data?.logo_key ?? null }), onSuccess: done('Shop details saved.'), onError: bad });
  const savePolicy = useMutation({
    mutationFn: () => put('/shop-owner/shop/cancellation-policy', { beforeProcessingPercent: Number(p.beforeProcessingPercent), duringProcessingPercent: Number(p.duringProcessingPercent), afterPrintingPercent: Number(p.afterPrintingPercent), readyAllowed: p.readyAllowed }),
    onSuccess: done('Cancellation policy saved.'), onError: bad,
  });
  if (shop.isLoading) return <Spinner />;
  if (!shop.data) return <Card><Note tone="mute">Create your shop first on the Orders tab.</Note></Card>;
  const pct = (k: 'beforeProcessingPercent' | 'duringProcessingPercent' | 'afterPrintingPercent', label: string) =>
    <Field label={label}><Input type="number" min={0} max={100} value={p[k]} onChange={(e) => setP({ ...p, [k]: e.target.value })} /></Field>;
  return (
    <>
      <h1 className="text-xl font-bold">Shop settings</h1>
      <Card className="grid gap-3">
        <h2 className="font-bold">Details</h2>
        <Field label="Shop name"><Input value={f.name} onChange={(e) => setF({ ...f, name: e.target.value })} /></Field>
        <Field label="Description"><Input value={f.description} onChange={(e) => setF({ ...f, description: e.target.value })} /></Field>
        <Field label="Address"><Input value={f.address} onChange={(e) => setF({ ...f, address: e.target.value })} /></Field>
        <Field label="Phone"><Input inputMode="tel" value={f.phone} onChange={(e) => setF({ ...f, phone: e.target.value })} /></Field>
        <Button disabled={f.name.trim().length < 2} loading={saveShop.isPending} onClick={() => saveShop.mutate()}>Save details</Button>
      </Card>
      <Card className="grid gap-3">
        <h2 className="font-bold">Cancellation and refunds</h2>
        <Note tone="mute">What percent of the money a student has paid is refunded if they cancel at each stage.</Note>
        <div className="grid grid-cols-3 gap-2">
          {pct('beforeProcessingPercent', 'Before work starts')}{pct('duringProcessingPercent', 'While processing')}{pct('afterPrintingPercent', 'Printing / binding')}
        </div>
        <label className="flex items-center gap-2 text-sm"><input type="checkbox" checked={p.readyAllowed} onChange={(e) => setP({ ...p, readyAllowed: e.target.checked })} />Allow cancelling once the order is READY (uses the "Printing / binding" percent)</label>
        <Button loading={savePolicy.isPending} onClick={() => savePolicy.mutate()}>Save policy</Button>
      </Card>
      <Note>{err}</Note><Note tone="ok">{msg}</Note>
    </>
  );
}
