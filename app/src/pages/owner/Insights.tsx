import { useState } from 'react';
import { Link } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { get } from '../../lib/api';
import { money, when } from '../../lib/format';
import { Bars, Card, Chip, Note, Row, Spinner, Stat, Tabs } from '../../components/ui';

const tz = Intl.DateTimeFormat().resolvedOptions().timeZone || 'Asia/Kolkata';
const hourLabel = (h: number) => `${h % 12 || 12} ${h < 12 ? 'AM' : 'PM'}`;

export default function Insights() {
  const [tab, setTab] = useState('overview');
  return (
    <>
      <h1 className="text-xl font-bold">Insights</h1>
      <Tabs tabs={[['overview', 'Overview'], ['payments', 'Payments'], ['refunds', 'Refunds']]} value={tab} onChange={setTab} />
      {tab === 'overview' && <Overview />}{tab === 'payments' && <Payments />}{tab === 'refunds' && <Refunds />}
    </>
  );
}

function Overview() {
  const dash = useQuery({ queryKey: ['owner-dash'], queryFn: () => get<any>('/shop-owner/dashboard', { tz }), refetchInterval: 30000 });
  const an = useQuery({ queryKey: ['owner-analytics'], queryFn: () => get<any>('/shop-owner/analytics', { tz }) });
  if (dash.isLoading || an.isLoading) return <Spinner />;
  const d = dash.data, a = an.data;
  if (!d || !a) return <Card><Note tone="mute">Create your shop first on the Orders tab.</Note></Card>;
  const live = Object.entries(d.liveOrdersByStatus as Record<string, number>).filter(([, n]) => n > 0);
  return (
    <>
      <div className="grid grid-cols-2 gap-2">
        <Stat label="Your earnings today" value={money(a.revenue.today)} sub="after platform fee and refunds" />
        <Stat label="Orders today" value={a.ordersToday} />
        <Stat label="Handed over today" value={a.completedToday} />
        <Stat label="Cancelled today" value={a.cancelledToday} />
        <Stat label="In the queue now" value={d.workload} />
        <Stat label="Avg. time to ready" value={a.avgPrepMinutes30d == null ? '—' : `${a.avgPrepMinutes30d} min`} sub="last 30 days" />
      </div>
      <Card className="grid gap-2">
        <h2 className="font-bold">Live orders</h2>
        {live.length ? <div className="flex flex-wrap gap-2">{live.map(([s, n]) => <Chip key={s}>{s} · {n}</Chip>)}</div> : <Note tone="mute">Nothing in progress.</Note>}
        <p className="text-xs text-mute">{d.activeOffers} active offer{d.activeOffers === 1 ? '' : 's'}</p>
      </Card>
      <Card className="grid grid-cols-3 gap-2 text-center">
        <div><b className="block">{money(a.revenue.today)}</b><span className="text-xs text-mute">Today</span></div>
        <div><b className="block">{money(a.revenue.last7Days)}</b><span className="text-xs text-mute">7 days</span></div>
        <div><b className="block">{money(a.revenue.last30Days)}</b><span className="text-xs text-mute">30 days</span></div>
      </Card>
      <Card className="grid gap-2"><h2 className="font-bold">Best sellers (30 days)</h2>
        {a.bestSelling.length ? <Bars items={a.bestSelling.map((b: any) => ({ label: b.name, value: Number(b.quantity) }))} format={(n) => `${n} sold`} /> : <Note tone="mute">No sales yet.</Note>}
      </Card>
      {a.popularPrintTypes.length > 0 && (
        <Card className="grid gap-2"><h2 className="font-bold">Popular print settings</h2>
          <Bars items={a.popularPrintTypes.map((p: any) => ({ label: `${p.page_size} · ${p.color_mode === 'COLOR' ? 'Colour' : 'B&W'} · ${p.sides === 'DOUBLE' ? 'double-sided' : 'single-sided'}`, value: Number(p.jobs) }))} format={(n) => `${n} jobs`} />
        </Card>
      )}
      <Card className="grid gap-2"><h2 className="font-bold">Busiest hours</h2>
        {a.peakHours.length ? <Bars items={a.peakHours.map((h: any) => ({ label: hourLabel(Number(h.hour)), value: Number(h.order_count) }))} format={(n) => `${n} orders`} /> : <Note tone="mute">Not enough orders yet.</Note>}
        <p className="text-xs text-mute">Times in {tz}.</p>
      </Card>
      <Card><h2 className="mb-1 font-bold">Low stock</h2>
        {a.lowStock.length ? a.lowStock.map((l: any) => <Row key={l.name}><span className="text-sm">{l.name}</span><Chip>{l.quantity} left (min {l.min_level})</Chip></Row>) : <Note tone="mute">Everything is stocked.</Note>}
        {a.lowStock.length > 0 && <Link to="/owner/catalog" className="mt-2 block text-sm text-accent">Restock in Catalog →</Link>}
      </Card>
      <Note tone="mute">These figures only count money and orders; no student personal details are shown.</Note>
    </>
  );
}

function Payments() {
  const q = useQuery({ queryKey: ['owner-payments'], queryFn: () => get<any[]>('/shop-owner/payments') });
  if (q.isLoading) return <Spinner />;
  return (
    <Card>
      {!q.data?.length && <Note tone="mute">No payments yet.</Note>}
      {q.data?.map((p) => (
        <Row key={p.id}>
          <div><b className="text-sm">#{p.order_code} · {p.payment_type.toLowerCase()}</b><p className="text-xs text-mute">{p.payment_method === 'GOOGLE_PAY' ? 'Google Pay' : 'PhonePe'} · {p.completed_at ? when(p.completed_at) : 'not completed'}</p></div>
          <div className="text-right"><b className={`block text-sm ${p.payment_type === 'REFUND' ? 'text-bad' : ''}`}>{p.payment_type === 'REFUND' ? '−' : ''}{money(p.amount)}</b><Chip>{p.status}</Chip></div>
        </Row>
      ))}
      <p className="mt-2 text-xs text-mute">Amounts are what students paid, including the platform fee. Your earnings are shown on the Overview tab.</p>
    </Card>
  );
}

function Refunds() {
  const q = useQuery({ queryKey: ['owner-refunds'], queryFn: () => get<any[]>('/shop-owner/refunds') });
  if (q.isLoading) return <Spinner />;
  return (
    <Card>
      {!q.data?.length && <Note tone="mute">No refunds.</Note>}
      {q.data?.map((r) => (
        <Row key={r.id}>
          <div><b className="text-sm">#{r.order_code}</b><p className="text-xs text-mute">{r.reason}</p><p className="text-xs text-mute">{when(r.created_at)}</p></div>
          <div className="text-right"><b className="block text-sm">{money(r.amount)}</b><Chip>{r.status}</Chip></div>
        </Row>
      ))}
    </Card>
  );
}
