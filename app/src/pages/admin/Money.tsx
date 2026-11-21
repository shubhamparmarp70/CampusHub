import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { get, put, errMsg } from '../../lib/api';
import { money } from '../../lib/format';
import { Button, Card, Field, Input, Note, Row, Spinner } from '../../components/ui';

const FEE: [string, string, string][] = [
  ['platform_fee_percent', 'Platform fee (% of the order after discounts)', 'Allowed 0–20. Use 0 to switch the fee off.'],
  ['platform_fee_min', 'Minimum fee (₹)', 'Allowed 0–100'],
  ['platform_fee_max', 'Maximum fee (₹)', 'Allowed 0–500. Use 0 for no cap.'],
];
const SYS: [string, string, string][] = [
  ['otp_ttl_minutes', 'Pickup OTP is valid for (minutes)', 'Allowed 5–60'],
  ['payment_timeout_minutes', 'Unpaid orders expire after (minutes)', 'Allowed 10–120'],
  ['file_retention_days', 'Keep uploaded documents for (days)', 'Allowed 1–30'],
];
/** Display only. The server calculates the real fee when an order is created. */
const sampleFee = (base: number, pct: number, min: number, max: number) => {
  if (base <= 0 || pct <= 0) return 0;
  let f = Math.round(base * pct) / 100; if (f < min) f = min; if (max > 0 && f > max) f = max; return f;
};
const iso = (d: Date) => new Date(d.getTime() - d.getTimezoneOffset() * 60000).toISOString().slice(0, 10);

export default function Money() {
  const q = useQuery({ queryKey: ['settings'], queryFn: () => get<any[]>('/admin/settings') });
  if (q.isLoading) return <Spinner />;
  const v: Record<string, string> = Object.fromEntries((q.data ?? []).map((r) => [r.setting_key, r.setting_value]));
  const pct = Number(v.platform_fee_percent ?? 0), min = Number(v.platform_fee_min ?? 0), max = Number(v.platform_fee_max ?? 0);
  return (
    <>
      <h1 className="text-xl font-bold">Money</h1>
      <Card className="grid gap-3">
        <h2 className="font-bold">Platform fee</h2>
        <p className="text-sm text-mute">Charged to the student on top of the order total. The 50% advance is taken on the full amount. New orders use changes straight away; existing orders keep their fee.</p>
        {FEE.map(([k, label, hint]) => <SettingField key={k + v[k]} k={k} label={label} hint={hint} initial={v[k] ?? '0'} />)}
        <div className="rounded-xl bg-page p-3 text-sm">
          <b className="block text-xs text-mute">What students pay with the current setting</b>
          {[100, 200, 500].map((b) => <div key={b} className="flex justify-between"><span>{money(b)} order</span><span>{pct > 0 ? `+ ${money(sampleFee(b, pct, min, max))} fee` : 'no fee'}</span></div>)}
        </div>
      </Card>
      <Card className="grid gap-3">
        <h2 className="font-bold">System limits</h2>
        {SYS.map(([k, label, hint]) => <SettingField key={k + v[k]} k={k} label={label} hint={hint} initial={v[k] ?? ''} />)}
        <p className="text-xs text-mute">The 50% advance rule isn't editable here on purpose.</p>
      </Card>
      <Settlements />
    </>
  );
}

function SettingField({ k, label, hint, initial }: { k: string; label: string; hint: string; initial: string }) {
  const qc = useQueryClient(); const [val, setVal] = useState(initial); const [msg, setMsg] = useState(''); const [err, setErr] = useState('');
  const save = useMutation({ mutationFn: () => put(`/admin/settings/${k}`, { value: val }), onSuccess: () => { setMsg('Saved.'); setErr(''); qc.invalidateQueries({ queryKey: ['settings'] }); }, onError: (e) => { setErr(errMsg(e)); setMsg(''); } });
  return (
    <div className="grid gap-1">
      <Field label={label}><div className="flex gap-2"><Input type="number" step="0.01" inputMode="decimal" value={val} onChange={(e) => setVal(e.target.value)} /><Button loading={save.isPending} disabled={val === initial || val === ''} onClick={() => save.mutate()}>Save</Button></div></Field>
      <span className="text-xs text-mute">{hint}</span><Note>{err}</Note><Note tone="ok">{msg}</Note>
    </div>
  );
}

function Settlements() {
  const [from, setFrom] = useState(iso(new Date(Date.now() - 29 * 864e5))); const [to, setTo] = useState(iso(new Date()));
  const q = useQuery({ queryKey: ['settlements', from, to], queryFn: () => get<any[]>('/admin/settlements', { from, to }), enabled: !!from && !!to && from <= to });
  const rows = q.data ?? [];
  const sum = (k: string) => rows.reduce((a, r) => a + Number(r[k]), 0);
  const csv = () => {
    const lines = [['Shop', 'Orders', 'Gross paid', 'Platform fees', 'Shop payout'], ...rows.map((r) => [r.shop_name, r.orders, r.gross, r.platform_fees, r.shop_payout])];
    const blob = new Blob([lines.map((l) => l.map((c) => `"${String(c).replace(/"/g, '""')}"`).join(',')).join('\n')], { type: 'text/csv' });
    const a = document.createElement('a'); a.href = URL.createObjectURL(blob); a.download = `settlements_${from}_to_${to}.csv`; a.click(); URL.revokeObjectURL(a.href);
  };
  return (
    <Card className="grid gap-3">
      <h2 className="font-bold">Settlements</h2>
      <p className="text-sm text-mute">What each shop is owed for orders that were handed over in the period. This is a report only: paying shops out isn't automated.</p>
      <div className="grid grid-cols-2 gap-2"><Field label="From"><Input type="date" value={from} max={to} onChange={(e) => setFrom(e.target.value)} /></Field><Field label="To"><Input type="date" value={to} min={from} onChange={(e) => setTo(e.target.value)} /></Field></div>
      {q.isLoading && <Spinner />}
      {q.data && !rows.length && <Note tone="mute">No completed orders in this period.</Note>}
      {rows.map((r) => (
        <Row key={r.shop_id}>
          <div><b className="text-sm">{r.shop_name}</b><p className="text-xs text-mute">{r.orders} orders · paid {money(r.gross)} · fees {money(r.platform_fees)}</p></div>
          <div className="text-right"><b className="block">{money(r.shop_payout)}</b><span className="text-xs text-mute">owed</span></div>
        </Row>
      ))}
      {rows.length > 0 && <>
        <div className="grid grid-cols-3 gap-2 rounded-xl bg-page p-3 text-center text-sm">
          <div><b className="block">{money(sum('gross'))}</b><span className="text-xs text-mute">Paid</span></div>
          <div><b className="block">{money(sum('platform_fees'))}</b><span className="text-xs text-mute">Platform fees</span></div>
          <div><b className="block">{money(sum('shop_payout'))}</b><span className="text-xs text-mute">Owed to shops</span></div>
        </div>
        <Button variant="ghost" onClick={csv}>Download CSV</Button></>}
    </Card>
  );
}
