import { useEffect, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { get, put, errMsg } from '../../lib/api';
import { Button, Card, Field, Input, Note, Spinner } from '../../components/ui';

const SIZES: [string, string][] = [['A4', 'A4'], ['A3', 'A3'], ['LEGAL', 'Legal']];
const COLORS: [string, string][] = [['BLACK_WHITE', 'B&W'], ['COLOR', 'Colour']];
const ADDONS: [string, string, string][] = [
  ['duplexDiscountPerPage', 'Double-sided discount per page (₹)', 'Only used with the "per page" rule'],
  ['spiralBinding', 'Spiral binding (₹ per copy)', ''], ['tapeBinding', 'Tape binding (₹ per copy)', ''],
  ['laminationPerPage', 'Lamination (₹ per page)', ''], ['staplingPerCopy', 'Stapling (₹ per copy)', ''],
];
const KEYMAP: Record<string, string> = { spiralBinding: 'spiral', tapeBinding: 'tape' };   // the GET response uses shorter names

/** Students never send prices; the server reads this rate card for every quote. */
export default function PrintPrices() {
  const qc = useQueryClient();
  const q = useQuery({ queryKey: ['owner-pricing'], queryFn: () => get<any>('/shop-owner/printing-prices') });
  const [rates, setRates] = useState<Record<string, string>>({}); const [add, setAdd] = useState<Record<string, string>>({}); const [rule, setRule] = useState('PER_PAGE');
  const [err, setErr] = useState(''); const [ok, setOk] = useState('');
  useEffect(() => {
    if (!q.data) return;
    const r: Record<string, string> = {};
    for (const [s] of SIZES) for (const [c] of COLORS) { const v = q.data.pageRates?.[s]?.[c]; r[`${s}|${c}`] = v == null ? '' : String(v); }
    setRates(r);
    const a: Record<string, string> = {};
    for (const [k] of ADDONS) a[k] = String(q.data[KEYMAP[k] ?? k] ?? 0);
    setAdd(a); setRule(q.data.duplexRule ?? 'PER_PAGE');
  }, [q.data]);
  const save = useMutation({
    mutationFn: () => put('/shop-owner/printing-prices', {
      rates: SIZES.flatMap(([s]) => COLORS.map(([c]) => ({ size: s, color: c, price: rates[`${s}|${c}`] }))).filter((r) => r.price !== '' && r.price != null).map((r) => ({ ...r, price: Number(r.price) })),
      duplexDiscountPerPage: Number(add.duplexDiscountPerPage || 0), spiralBinding: Number(add.spiralBinding || 0), tapeBinding: Number(add.tapeBinding || 0),
      laminationPerPage: Number(add.laminationPerPage || 0), staplingPerCopy: Number(add.staplingPerCopy || 0), duplexRule: rule,
    }),
    onSuccess: () => { setErr(''); setOk('Saved. New orders use these prices straight away.'); qc.invalidateQueries({ queryKey: ['owner-pricing'] }); }, onError: (e) => { setErr(errMsg(e)); setOk(''); },
  });
  if (q.isLoading) return <Spinner />;
  const none = Object.values(rates).every((v) => v === '');
  return (
    <>
      <Card className="grid gap-3">
        <h2 className="font-bold">Price per printed page (₹)</h2>
        <Note tone="mute">Leave a box empty if you don't offer that size and colour.</Note>
        <div className="grid grid-cols-[4rem_1fr_1fr] items-center gap-2 text-sm">
          <span /><b className="text-xs text-mute">B&W</b><b className="text-xs text-mute">Colour</b>
          {SIZES.map(([s, label]) => (
            <div key={s} className="contents">
              <b>{label}</b>
              {COLORS.map(([c]) => <Input key={c} type="number" min={0} step="0.5" inputMode="decimal" placeholder="—" value={rates[`${s}|${c}`] ?? ''} onChange={(e) => setRates((p) => ({ ...p, [`${s}|${c}`]: e.target.value }))} />)}
            </div>
          ))}
        </div>
      </Card>
      <Card className="grid gap-3">
        <h2 className="font-bold">Double-sided pricing</h2>
        <select className="w-full rounded-xl border border-line px-3 py-2.5 text-sm" value={rule} onChange={(e) => setRule(e.target.value)}>
          <option value="PER_PAGE">Per page (optional discount for double-sided)</option>
          <option value="PER_SHEET">Per sheet (one sheet holds two pages)</option>
        </select>
        <h2 className="mt-1 font-bold">Add-ons</h2>
        {ADDONS.map(([k, label, hint]) => (
          <Field key={k} label={label}><Input type="number" min={0} step="0.5" inputMode="decimal" value={add[k] ?? ''} onChange={(e) => setAdd((p) => ({ ...p, [k]: e.target.value }))} />{hint && <span className="text-xs text-mute">{hint}</span>}</Field>
        ))}
      </Card>
      <Note>{err}</Note><Note tone="ok">{ok}</Note>
      {none && <Note>Add at least one page price before saving.</Note>}
      <Button disabled={none} loading={save.isPending} onClick={() => save.mutate()}>Save printing prices</Button>
    </>
  );
}
