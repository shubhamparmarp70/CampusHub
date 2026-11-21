import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { get, patch, post, put, errMsg } from '../../lib/api';
import { money } from '../../lib/format';
import { Button, Card, Chip, Field, Input, Note, Row, Spinner } from '../../components/ui';

interface FormValues { categoryId: string; name: string; description: string; kind: string; sku: string; sellingPrice: string; costPrice: string; initialStock: string; minStock: string }
const sel = 'w-full rounded-xl border border-line px-3 py-2.5 text-sm';

export default function Products({ shop }: { shop: any }) {
  const qc = useQueryClient();
  const [editing, setEditing] = useState<any | 'new' | null>(null); const [err, setErr] = useState('');
  const list = useQuery({ queryKey: ['owner-products'], queryFn: () => get<any[]>('/shop-owner/products') });
  const cats = useQuery({ queryKey: ['categories', shop.vertical], queryFn: () => get<any[]>('/categories', { vertical: shop.vertical }) });
  const toggle = useMutation({ mutationFn: (p: any) => patch(`/shop-owner/products/${p.id}/active`, { active: !p.active }), onSuccess: () => qc.invalidateQueries({ queryKey: ['owner-products'] }), onError: (e) => setErr(errMsg(e)) });
  if (list.isLoading || cats.isLoading) return <Spinner />;
  return (
    <>
      {!cats.data?.length && <Card><Note>No categories exist yet. Ask the platform admin to add some (Admin → Categories). You can't add products without one.</Note></Card>}
      {editing ? (
        <ProductForm shop={shop} cats={cats.data ?? []} initial={editing === 'new' ? null : editing} onDone={() => { setEditing(null); qc.invalidateQueries({ queryKey: ['owner-products'] }); qc.invalidateQueries({ queryKey: ['owner-stock'] }); }} onCancel={() => setEditing(null)} />
      ) : (
        <Button disabled={!cats.data?.length} onClick={() => setEditing('new')}>+ Add product or service</Button>
      )}
      <Note>{err}</Note>
      <Card>
        {!list.data?.length && <Note tone="mute">No products yet.</Note>}
        {list.data?.map((p) => (
          <Row key={p.id}>
            <div className="min-w-0">
              <b className="block truncate text-sm">{p.name}</b>
              <p className="text-xs text-mute">{money(p.selling_price)} · {p.kind === 'SERVICE' ? 'Service' : `${p.quantity ?? 0} in stock`}{p.sku ? ` · ${p.sku}` : ''}</p>
            </div>
            <div className="flex items-center gap-2">
              {!p.active && <Chip>Hidden</Chip>}
              <Button variant="ghost" className="!px-3 !py-1.5" onClick={() => setEditing(p)}>Edit</Button>
              <Button variant="ghost" className="!px-3 !py-1.5" onClick={() => toggle.mutate(p)}>{p.active ? 'Hide' : 'Show'}</Button>
            </div>
          </Row>
        ))}
      </Card>
    </>
  );
}

function ProductForm({ shop, cats, initial, onDone, onCancel }: { shop: any; cats: any[]; initial: any | null; onDone: () => void; onCancel: () => void }) {
  const [err, setErr] = useState('');
  const { register, handleSubmit, watch, formState: { errors, isSubmitting } } = useForm<FormValues>({
    defaultValues: initial ? { categoryId: String(initial.category_id), name: initial.name, description: initial.description ?? '', kind: initial.kind, sku: initial.sku ?? '', sellingPrice: String(initial.selling_price), costPrice: initial.cost_price == null ? '' : String(initial.cost_price), initialStock: '', minStock: String(initial.min_level ?? 5) }
      : { categoryId: String(cats[0]?.id ?? ''), kind: 'ITEM', name: '', description: '', sku: '', sellingPrice: '', costPrice: '', initialStock: '0', minStock: '5' },
  });
  const kind = watch('kind');
  const submit = handleSubmit(async (v) => {
    const body = {
      categoryId: Number(v.categoryId), name: v.name.trim(), description: v.description || null, kind: v.kind, sku: v.sku.trim() || null,
      sellingPrice: Number(v.sellingPrice), costPrice: v.costPrice === '' ? null : Number(v.costPrice),
      initialStock: v.kind === 'ITEM' && !initial ? Number(v.initialStock || 0) : null, minStock: v.kind === 'ITEM' ? Number(v.minStock || 5) : null,
    };
    try { setErr(''); initial ? await put(`/shop-owner/products/${initial.id}`, body) : await post('/shop-owner/products', body); onDone(); } catch (e) { setErr(errMsg(e)); }
  });
  const price = { required: 'Enter a price', min: { value: 0, message: 'Can\'t be negative' } };
  return (
    <Card>
      <form className="grid gap-3" onSubmit={submit}>
        <h2 className="font-bold">{initial ? 'Edit product' : 'New product or service'}</h2>
        <Field label="Name" error={errors.name?.message}><Input {...register('name', { required: 'Enter a name' })} /></Field>
        <div className="grid grid-cols-2 gap-2">
          <Field label="Category"><select className={sel} {...register('categoryId')}>{cats.map((c) => <option key={c.id} value={c.id}>{c.name}</option>)}</select></Field>
          <Field label="Type"><select className={sel} disabled={!!initial} {...register('kind')}><option value="ITEM">Product (has stock)</option><option value="SERVICE">Service (e.g. binding)</option></select></Field>
          <Field label="Selling price (₹)" error={errors.sellingPrice?.message}><Input type="number" step="0.01" inputMode="decimal" {...register('sellingPrice', price)} /></Field>
          <Field label="Cost price (₹, optional)" error={errors.costPrice?.message}><Input type="number" step="0.01" inputMode="decimal" {...register('costPrice', { min: { value: 0, message: 'Can\'t be negative' } })} /></Field>
          <Field label="SKU (optional)"><Input {...register('sku')} /></Field>
          {kind === 'ITEM' && <Field label="Minimum stock alert"><Input type="number" min={0} {...register('minStock')} /></Field>}
          {kind === 'ITEM' && !initial && <Field label="Opening stock"><Input type="number" min={0} {...register('initialStock')} /></Field>}
        </div>
        <Field label="Description (optional)"><Input {...register('description')} /></Field>
        <Note>{err}</Note>
        <div className="flex gap-2"><Button type="submit" className="flex-1" loading={isSubmitting}>Save</Button><Button type="button" variant="ghost" onClick={onCancel}>Cancel</Button></div>
      </form>
    </Card>
  );
}
