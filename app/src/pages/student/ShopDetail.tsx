import { useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { Heart } from 'lucide-react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { del, get, post, errMsg } from '../../lib/api';
import { money } from '../../lib/format';
import { Button, Card, Chip, Note, Row, Spinner } from '../../components/ui';
import PrintConfigurator from './PrintConfigurator';

export default function ShopDetail() {
  const id = Number(useParams().id); const qc = useQueryClient();
  const [msg, setMsg] = useState(''); const [err, setErr] = useState('');
  const shop = useQuery({ queryKey: ['shop', id], queryFn: () => get<any>(`/shops/${id}`) });
  const products = useQuery({ queryKey: ['products', id], queryFn: () => get<any[]>(`/shops/${id}/products`) });
  const add = useMutation({
    mutationFn: (productId: number) => post<any>('/student/cart/items', { shopId: id, productId, quantity: 1 }),
    onSuccess: (q) => { qc.setQueryData(['cart'], q); setMsg('Added to cart.'); setErr(''); }, onError: (e) => { setErr(errMsg(e)); setMsg(''); },
  });
  const favs = useQuery({ queryKey: ['favorites'], queryFn: () => get<any[]>('/student/favorites') });
  const favIds = new Set((favs.data ?? []).map((f) => f.product_id));
  const toggleFav = useMutation({
    mutationFn: (v: { id: number; on: boolean }) => (v.on ? del(`/student/favorites/${v.id}`) : post(`/student/favorites/${v.id}`)),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['favorites'] }), onError: (e) => setErr(errMsg(e)),
  });
  const follow = useMutation({ mutationFn: () => post(`/student/shops/${id}/follow`), onSuccess: () => setMsg('You follow this shop now.'), onError: (e) => setErr(errMsg(e)) });
  if (shop.isLoading) return <Spinner />;
  const s = shop.data; if (!s) return <Note>Shop not found.</Note>;
  return (
    <>
      <Card className="grid gap-1">
        <div className="flex items-center justify-between"><h1 className="text-xl font-bold">{s.name}</h1><Chip>{s.open_status === 'OPEN' ? 'Open' : 'Closed'}</Chip></div>
        <p className="text-sm text-mute">{s.description}</p><p className="text-sm text-mute">{s.address}</p>
        <p className="text-sm">Estimated preparation time: <b>{s.estimatedPrepMinutes} minutes</b> <span className="text-mute">(a guide, not a promise)</span></p>
        <div className="mt-1 flex gap-2"><Button variant="ghost" className="!py-1.5" onClick={() => follow.mutate()}>Follow</Button><Link to="/student/cart"><Button className="!py-1.5">Go to cart</Button></Link></div>
      </Card>
      {s.offers?.length > 0 && <Card><h2 className="mb-1 font-bold">Offers</h2>{s.offers.map((o: any) => <Row key={o.id}><div><b className="text-sm">{o.name}</b><p className="text-xs text-mute">{o.description}</p></div><Chip>{o.code}</Chip></Row>)}</Card>}
      <Note>{err}</Note><Note tone="ok">{msg}</Note>
      {s.vertical === 'DESK' && <PrintConfigurator shopId={id} />}
      <Card>
        <h2 className="mb-1 font-bold">{s.vertical === 'DESK' ? 'Stationery & services' : 'Menu'}</h2>
        {products.isLoading && <Spinner />}
        {products.data?.map((p) => (
          <Row key={p.id}>
            <div><b className="text-sm">{p.name}</b><p className="text-xs text-mute">{p.category} · {money(p.selling_price)}{!p.in_stock && ' · out of stock'}</p></div>
            <div className="flex items-center gap-2"><button aria-label={favIds.has(p.id) ? 'Remove from favourites' : 'Add to favourites'} onClick={() => toggleFav.mutate({ id: p.id, on: favIds.has(p.id) })}><Heart className={`h-5 w-5 ${favIds.has(p.id) ? 'fill-accent text-accent' : 'text-mute'}`} /></button>
              <Button className="!px-3 !py-1.5" disabled={!p.in_stock || s.open_status !== 'OPEN'} onClick={() => add.mutate(p.id)}>Add</Button></div>
          </Row>
        ))}
        {products.data?.length === 0 && <Note tone="mute">Nothing listed yet.</Note>}
      </Card>
    </>
  );
}
