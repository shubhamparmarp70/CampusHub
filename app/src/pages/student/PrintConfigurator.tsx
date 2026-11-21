import { useState } from 'react';
import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api, get, post, errMsg } from '../../lib/api';
import { useDebounced } from '../../lib/hooks';
import { money } from '../../lib/format';
import { Button, Card, Field, Input, Note } from '../../components/ui';

const Select = ({ value, onChange, options }: { value: string; onChange(v: string): void; options: [string, string][] }) =>
  <select value={value} onChange={(e) => onChange(e.target.value)} className="w-full rounded-xl border border-line px-3 py-2.5 text-sm">{options.map(([v, l]) => <option key={v} value={v}>{l}</option>)}</select>;

const DEFAULTS = { size: 'A4', color: 'BLACK_WHITE', sides: 'SINGLE', copies: 1 as number | string, pageRange: '', orientation: 'PORTRAIT', binding: 'NONE', lamination: false, stapling: false };

/** The price shown here comes from the server (same rate card and rules as checkout). The cart still shows the final amount with offers and fees. */
export default function PrintConfigurator({ shopId }: { shopId: number }) {
  const qc = useQueryClient();
  const [file, setFile] = useState<{ fileId: number; name: string; pageCount: number } | null>(null);
  const [c, setC] = useState({ ...DEFAULTS });
  const [msg, setMsg] = useState(''); const [err, setErr] = useState(''); const [presetName, setPresetName] = useState('');
  const set = (k: string, v: any) => setC((p) => ({ ...p, [k]: v }));
  const body = (cfg: typeof c) => ({ ...cfg, copies: Number(cfg.copies), pageRange: cfg.pageRange.trim() || null });

  const presets = useQuery({ queryKey: ['presets'], queryFn: () => get<any[]>('/student/print-presets') });
  const dc = useDebounced(c, 400);
  const copiesOk = Number(dc.copies) >= 1 && Number(dc.copies) <= 500;
  const preview = useQuery({
    queryKey: ['print-preview', shopId, file?.fileId, dc], enabled: !!file && copiesOk, retry: false, placeholderData: keepPreviousData,
    queryFn: () => post<any>('/student/print-preview', { shopId, fileId: file!.fileId, config: body(dc) }),
  });

  const upload = useMutation({
    mutationFn: async (f: File) => { const fd = new FormData(); fd.append('file', f); return (await api.post('/student/files', fd)).data.data; },
    onSuccess: (d) => { setFile(d); setErr(''); setMsg(''); }, onError: (e) => setErr(errMsg(e)),
  });
  const add = useMutation({
    mutationFn: () => post<any>('/student/cart/print-jobs', { shopId, fileId: file!.fileId, config: body(c) }),
    onSuccess: (q) => { qc.setQueryData(['cart'], q); setMsg('Added to cart. Offers and fees are applied there.'); setErr(''); setFile(null); }, onError: (e) => { setErr(errMsg(e)); setMsg(''); },
  });
  const savePreset = useMutation({
    mutationFn: () => post('/student/print-presets', { name: presetName.trim(), config: { ...body(c), pageRange: null } }),
    onSuccess: () => { setPresetName(''); setMsg('Settings saved for next time.'); setErr(''); qc.invalidateQueries({ queryKey: ['presets'] }); }, onError: (e) => { setErr(errMsg(e)); setMsg(''); },
  });
  const loadPreset = (id: string) => { const p = presets.data?.find((x) => String(x.id) === id); if (p) setC({ ...DEFAULTS, ...p.config, pageRange: '' }); };

  return (
    <Card className="grid gap-3">
      <h2 className="font-bold">Print a document</h2>
      <Field label="Document (PDF, up to 10 MB)"><Input type="file" accept="application/pdf" onChange={(e) => e.target.files?.[0] && upload.mutate(e.target.files[0])} /></Field>
      {upload.isPending && <Note tone="mute">Uploading…</Note>}
      {file && <Note tone="ok">{file.name} · {file.pageCount} pages</Note>}
      {!!presets.data?.length && (
        <Field label="Use saved settings">
          <select className="w-full rounded-xl border border-line px-3 py-2.5 text-sm" defaultValue="" onChange={(e) => { loadPreset(e.target.value); e.target.value = ''; }}>
            <option value="">Choose…</option>{presets.data.map((p) => <option key={p.id} value={p.id}>{p.name}</option>)}
          </select>
        </Field>
      )}
      <div className="grid grid-cols-2 gap-2">
        <Field label="Page size"><Select value={c.size} onChange={(v) => set('size', v)} options={[['A4', 'A4'], ['A3', 'A3'], ['LEGAL', 'Legal']]} /></Field>
        <Field label="Colour"><Select value={c.color} onChange={(v) => set('color', v)} options={[['BLACK_WHITE', 'Black & white'], ['COLOR', 'Colour']]} /></Field>
        <Field label="Sides"><Select value={c.sides} onChange={(v) => set('sides', v)} options={[['SINGLE', 'Single-sided'], ['DOUBLE', 'Double-sided']]} /></Field>
        <Field label="Copies"><Input type="number" min={1} max={500} value={c.copies} onChange={(e) => set('copies', e.target.value)} /></Field>
        <Field label="Page range (blank = all)"><Input placeholder="1-5, 8" value={c.pageRange} onChange={(e) => set('pageRange', e.target.value)} /></Field>
        <Field label="Orientation"><Select value={c.orientation} onChange={(v) => set('orientation', v)} options={[['PORTRAIT', 'Portrait'], ['LANDSCAPE', 'Landscape']]} /></Field>
        <Field label="Binding"><Select value={c.binding} onChange={(v) => set('binding', v)} options={[['NONE', 'None'], ['SPIRAL', 'Spiral'], ['TAPE', 'Tape']]} /></Field>
        <div className="flex flex-col justify-end gap-1 pb-1 text-sm">
          <label className="flex items-center gap-2"><input type="checkbox" checked={c.lamination} onChange={(e) => set('lamination', e.target.checked)} />Lamination</label>
          <label className="flex items-center gap-2"><input type="checkbox" checked={c.stapling} onChange={(e) => set('stapling', e.target.checked)} />Stapling</label>
        </div>
      </div>

      {file && (
        <div className="rounded-xl bg-page p-3">
          {preview.data ? (
            <div className="flex items-center justify-between gap-3">
              <div className="text-sm"><b>{preview.data.billablePages}</b> pages · <b>{preview.data.sheets}</b> sheets{Number(c.copies) > 1 && <> × {c.copies} copies</>}<p className="text-xs text-mute">{money(preview.data.perCopy)} per copy</p></div>
              <div className="text-right"><div className="text-2xl font-extrabold">{money(preview.data.total)}</div><div className="text-xs text-mute">printing cost{preview.isFetching ? '…' : ''}</div></div>
            </div>
          ) : preview.isError ? <Note>{errMsg(preview.error)}</Note> : <Note tone="mute">{copiesOk ? 'Working out the price…' : 'Choose 1 to 500 copies.'}</Note>}
          {preview.data && preview.isError && <Note>{errMsg(preview.error)}</Note>}
        </div>
      )}
      <Note>{err}</Note><Note tone="ok">{msg}</Note>
      <Button disabled={!file || preview.isError || !copiesOk} loading={add.isPending} onClick={() => add.mutate()}>Add print job to cart</Button>
      <div className="flex gap-2">
        <Input placeholder="Name these settings, e.g. Lab report" maxLength={60} value={presetName} onChange={(e) => setPresetName(e.target.value)} />
        <Button variant="ghost" disabled={presetName.trim().length < 2} loading={savePreset.isPending} onClick={() => savePreset.mutate()}>Save</Button>
      </div>
    </Card>
  );
}
