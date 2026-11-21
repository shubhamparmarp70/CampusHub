import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { get, post, errMsg } from '../../lib/api';
import { money, when } from '../../lib/format';
import { Button, Card, Chip, Note, Row, Spinner, Tabs } from '../../components/ui';

const sel = 'w-full rounded-xl border border-line bg-page px-3 py-2.5 text-sm';

export default function Issues() {
  const [tab, setTab] = useState('refunds');
  return (
    <>
      <h1 className="text-xl font-bold">Issues</h1>
      <Tabs tabs={[['refunds', 'Refunds'], ['payments', 'Payments'], ['orders', 'Orders']]} value={tab} onChange={setTab} />
      {tab === 'refunds' && <Refunds />}{tab === 'payments' && <Payments />}{tab === 'orders' && <Problems />}
    </>
  );
}

function Refunds() {
  const qc = useQueryClient(); const [status, setStatus] = useState(''); const [err, setErr] = useState(''); const [ok, setOk] = useState('');
  const q = useQuery({ queryKey: ['admin-refunds', status], queryFn: () => get<any[]>('/admin/refunds', { status: status || undefined }) });
  const retry = useMutation({ mutationFn: (id: number) => post(`/admin/refunds/${id}/retry`), onSuccess: () => { setOk('Retried. Check the status below.'); setErr(''); qc.invalidateQueries({ queryKey: ['admin-refunds'] }); }, onError: (e) => { setErr(errMsg(e)); setOk(''); } });
  return (
    <>
      <select className={sel} value={status} onChange={(e) => setStatus(e.target.value)}>
        <option value="">All refunds</option><option value="REQUESTED">Requested (waiting or failed)</option><option value="APPROVED">Waiting for the payment provider</option><option value="COMPLETED">Completed</option>
      </select>
      <Note>{err}</Note><Note tone="ok">{ok}</Note>
      {q.isLoading ? <Spinner /> : (
        <Card>
          {!q.data?.length && <Note tone="mute">No refunds here.</Note>}
          {q.data?.map((r) => (
            <Row key={r.id}>
              <div className="min-w-0"><b className="text-sm">#{r.order_code} · {money(r.amount)}</b><p className="truncate text-xs text-mute">{r.reason}</p><p className="text-xs text-mute">{when(r.created_at)}</p></div>
              <div className="flex flex-col items-end gap-1"><Chip>{r.status}</Chip>
                {r.status !== 'COMPLETED' && <Button variant="ghost" className="!px-3 !py-1" loading={retry.isPending} onClick={() => retry.mutate(r.id)}>Retry</Button>}</div>
            </Row>
          ))}
        </Card>
      )}
    </>
  );
}

function Payments() {
  const [mode, setMode] = useState('stuck');
  const params = mode === 'stuck' ? { stuckMinutes: 30 } : mode === 'failed' ? { status: 'FAILED' } : {};
  const q = useQuery({ queryKey: ['admin-payments', mode], queryFn: () => get<any[]>('/admin/payments', params) });
  return (
    <>
      <select className={sel} value={mode} onChange={(e) => setMode(e.target.value)}>
        <option value="stuck">Stuck: pending for 30+ minutes</option><option value="failed">Failed</option><option value="all">Latest payments</option>
      </select>
      {q.isLoading ? <Spinner /> : (
        <Card>
          {!q.data?.length && <Note tone="mute">{mode === 'stuck' ? 'No stuck payments. 🎉' : 'Nothing here.'}</Note>}
          {q.data?.map((p) => (
            <Row key={p.id}>
              <div className="min-w-0"><b className="text-sm">#{p.order_code} · {p.payment_type.toLowerCase()}</b><p className="truncate text-xs text-mute">{p.transaction_id}</p><p className="text-xs text-mute">{when(p.created_at)}</p></div>
              <div className="text-right"><b className="block text-sm">{money(p.amount)}</b><Chip>{p.status}</Chip></div>
            </Row>
          ))}
        </Card>
      )}
      <Note tone="mute">A payment stuck in "pending" usually means the provider's confirmation never arrived. Check the provider dashboard before doing anything with the order.</Note>
    </>
  );
}

function Problems() {
  const q = useQuery({ queryKey: ['admin-problems'], queryFn: () => get<any[]>('/admin/orders/problems') });
  if (q.isLoading) return <Spinner />;
  return (
    <Card>
      <p className="mb-1 text-xs text-mute">Failed orders and orders waiting for a refund.</p>
      {!q.data?.length && <Note tone="mute">No problem orders.</Note>}
      {q.data?.map((o) => (
        <Row key={o.id}>
          <div className="min-w-0"><b className="text-sm">#{o.order_code} · {o.shop_name}</b><p className="truncate text-xs text-mute">{o.cancel_reason ?? 'No reason recorded'}</p><p className="text-xs text-mute">{when(o.updated_at)}</p></div>
          <div className="text-right"><b className="block text-sm">{money(o.total_amount)}</b><Chip>{o.status.replace('_', ' ')}</Chip></div>
        </Row>
      ))}
    </Card>
  );
}
