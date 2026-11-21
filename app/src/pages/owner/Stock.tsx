import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { get, post, put, errMsg } from '../../lib/api';
import { when } from '../../lib/format';
import { Button, Card, Chip, Input, Note, Spinner } from '../../components/ui';

export default function Stock() {
  const [lowOnly, setLowOnly] = useState(false);
  const q = useQuery({ queryKey: ['owner-stock', lowOnly], queryFn: () => get<any[]>('/shop-owner/inventory', { lowOnly }) });
  if (q.isLoading) return <Spinner />;
  return (
    <>
      <label className="flex items-center gap-2 text-sm"><input type="checkbox" checked={lowOnly} onChange={(e) => setLowOnly(e.target.checked)} />Show only low stock</label>
      {!q.data?.length && <Card><Note tone="mute">{lowOnly ? 'Nothing is running low.' : 'No stocked products yet. Add a product first.'}</Note></Card>}
      {q.data?.map((i) => <StockRow key={i.productId} item={i} />)}
    </>
  );
}

const ACTIONS: [string, string][] = [['ADD', 'Add stock'], ['REDUCE', 'Remove stock'], ['ADJUST', 'Set to exact count'], ['DAMAGED', 'Mark damaged']];

function StockRow({ item }: { item: any }) {
  const qc = useQueryClient();
  const [type, setType] = useState('ADD'); const [qty, setQty] = useState(''); const [note, setNote] = useState(''); const [min, setMin] = useState(String(item.min_level));
  const [showHist, setShowHist] = useState(false); const [err, setErr] = useState(''); const [ok, setOk] = useState('');
  const low = item.low === true || Number(item.low) === 1;
  const done = (m: string) => () => { setErr(''); setOk(m); setQty(''); setNote(''); qc.invalidateQueries({ queryKey: ['owner-stock'] }); qc.invalidateQueries({ queryKey: ['owner-products'] }); qc.invalidateQueries({ queryKey: ['hist', item.productId] }); };
  const bad = (e: unknown) => { setErr(errMsg(e)); setOk(''); };
  const adjust = useMutation({ mutationFn: () => post(`/shop-owner/inventory/${item.productId}/adjust`, { type, quantity: Number(qty), note: note || null }), onSuccess: done('Stock updated.'), onError: bad });
  const saveMin = useMutation({ mutationFn: () => put(`/shop-owner/inventory/${item.productId}/min`, { minLevel: Number(min) }), onSuccess: done('Minimum saved.'), onError: bad });
  const hist = useQuery({ queryKey: ['hist', item.productId], queryFn: () => get<any[]>(`/shop-owner/inventory/${item.productId}/history`), enabled: showHist });
  return (
    <Card className="grid gap-2">
      <div className="flex items-center justify-between gap-2">
        <div className="min-w-0"><b className="block truncate">{item.name}</b><p className="text-xs text-mute">{item.sku ?? 'No SKU'}</p></div>
        <div className="text-right"><div className="text-2xl font-extrabold">{item.quantity}</div>{low && <Chip>Low stock</Chip>}</div>
      </div>
      <div className="grid grid-cols-[1fr_5rem] gap-2">
        <select className="w-full rounded-xl border border-line px-3 py-2.5 text-sm" value={type} onChange={(e) => setType(e.target.value)}>{ACTIONS.map(([v, l]) => <option key={v} value={v}>{l}</option>)}</select>
        <Input type="number" min={0} inputMode="numeric" placeholder="Qty" value={qty} onChange={(e) => setQty(e.target.value)} />
      </div>
      <Input placeholder="Note (optional), e.g. new delivery" value={note} onChange={(e) => setNote(e.target.value)} />
      <Button disabled={qty === '' || Number(qty) < 0} loading={adjust.isPending} onClick={() => adjust.mutate()}>Apply</Button>
      <div className="flex items-end gap-2">
        <label className="flex-1 text-xs text-mute">Alert me when stock is at or below<Input type="number" min={0} value={min} onChange={(e) => setMin(e.target.value)} /></label>
        <Button variant="ghost" loading={saveMin.isPending} onClick={() => saveMin.mutate()}>Save</Button>
      </div>
      <Note>{err}</Note><Note tone="ok">{ok}</Note>
      <button className="text-left text-xs text-accent" onClick={() => setShowHist((s) => !s)}>{showHist ? 'Hide history' : 'Show history'}</button>
      {showHist && (hist.isLoading ? <Spinner /> : hist.data?.map((h, n) => (
        <p key={n} className="text-xs text-mute">{when(h.created_at)} · {h.txn_type} {h.quantity_change > 0 ? '+' : ''}{h.quantity_change} → {h.quantity_after}{h.note ? ` · ${h.note}` : ''}</p>
      )))}
    </Card>
  );
}
