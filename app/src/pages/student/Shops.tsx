import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Clock, Star } from 'lucide-react';
import { get, post, errMsg } from '../../lib/api';
import { useService } from '../../lib/service';
import { useAuth } from '../../lib/auth';
import { Button, Card, Chip, Input, Spinner, Note } from '../../components/ui';

export default function Shops() {
  const { vertical } = useService(); const { user } = useAuth(); const [q, setQ] = useState(''); const nav = useNavigate(); const qc = useQueryClient(); const [err, setErr] = useState('');
  const recent = useQuery({ queryKey: ['orders'], queryFn: () => get<any[]>('/student/orders') });
  const last = recent.data?.find((o) => o.status === 'COLLECTED' && o.vertical === vertical);
  const again = useMutation({ mutationFn: (id: number) => post<any>(`/student/cart/reorder/${id}`), onSuccess: (d) => { qc.setQueryData(['cart'], d.quote); nav('/student/cart'); }, onError: (e) => setErr(errMsg(e)) });
  const shops = useQuery({ queryKey: ['shops', vertical, q], queryFn: () => get<any[]>('/shops', { vertical, q: q || undefined }) });
  return (
    <>
      <div><h1 className="text-xl font-bold">Hi, {user?.fullName.split(' ')[0]}</h1>
        <p className="text-sm text-mute">{vertical === 'FOOD' ? 'Order food ahead and skip the queue.' : 'Search stationery, printing, binding…'}</p></div>
      <Input placeholder={vertical === 'FOOD' ? 'Search canteens…' : 'Search stationery, printing, binding…'} value={q} onChange={(e) => setQ(e.target.value)} />
      {last && <Card className="flex items-center justify-between gap-3"><div className="min-w-0"><b className="block truncate text-sm">Order again from {last.shop_name}</b><p className="text-xs text-mute">Your last order, #{last.order_code}</p></div><Button className="!py-1.5" loading={again.isPending} onClick={() => again.mutate(last.id)}>Reorder</Button></Card>}
      <Note>{err}</Note>
      {shops.isLoading && <Spinner />}
      {shops.data?.length === 0 && <Card><Note tone="mute">No shops here yet.</Note></Card>}
      {shops.data?.map((s) => (
        <Link key={s.id} to={`/student/shops/${s.id}`}>
          <Card className="grid gap-1">
            <div className="flex items-center justify-between"><b>{s.name}</b><Chip>{s.open_status === 'OPEN' ? 'Open' : 'Closed'}</Chip></div>
            <p className="line-clamp-2 text-sm text-mute">{s.description || s.address}</p>
            <div className="flex gap-4 text-xs text-mute">
              <span className="flex items-center gap-1"><Star className="h-3.5 w-3.5" />{Number(s.avg_rating).toFixed(1)} ({s.rating_count})</span>
              <span className="flex items-center gap-1"><Clock className="h-3.5 w-3.5" />~{s.estimatedPrepMinutes} min</span>
            </div>
          </Card>
        </Link>
      ))}
    </>
  );
}
