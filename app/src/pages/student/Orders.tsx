import { Link } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { get } from '../../lib/api';
import { money, when } from '../../lib/format';
import { Card, Chip, Spinner } from '../../components/ui';

export default function Orders() {
  const q = useQuery({ queryKey: ['orders'], queryFn: () => get<any[]>('/student/orders'), refetchInterval: 15000 });
  if (q.isLoading) return <Spinner />;
  return (
    <>
      <h1 className="text-xl font-bold">My orders</h1>
      {!q.data?.length && <Card><p className="text-sm text-mute">No orders yet.</p></Card>}
      {q.data?.map((o) => (
        <Link key={o.id} to={`/student/orders/${o.id}`}>
          <Card className="grid gap-1">
            <div className="flex justify-between"><b>#{o.order_code}</b><Chip>{o.status.replace('_', ' ')}</Chip></div>
            <p className="text-sm text-mute">{o.shop_name} · {money(o.total_amount)}</p>
            {['PLACED', 'ACCEPTED', 'QUEUED', 'PROCESSING', 'PRINTING', 'BINDING'].includes(o.status) && <p className="text-xs text-mute">Estimated: {o.est_prep_min}–{o.est_prep_max} minutes</p>}
            <p className="text-xs text-mute">{when(o.created_at)}</p>
          </Card>
        </Link>
      ))}
    </>
  );
}
