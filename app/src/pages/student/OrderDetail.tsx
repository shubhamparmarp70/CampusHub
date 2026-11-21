import { useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { get, post, put, errMsg } from '../../lib/api';
import { FLOW, money } from '../../lib/format';
import { Button, Card, Chip, Input, Note, Row, Spinner } from '../../components/ui';
import PayPanel from '../../components/PayPanel';

export default function OrderDetail() {
  const id = Number(useParams().id); const qc = useQueryClient();
  const [msg, setMsg] = useState(''); const [err, setErr] = useState(''); const [rating, setRating] = useState(5); const [comment, setComment] = useState('');
  const q = useQuery({ queryKey: ['order', id], queryFn: () => get<any>(`/student/orders/${id}`), refetchInterval: 8000 });
  const refresh = () => { qc.invalidateQueries({ queryKey: ['order', id] }); qc.invalidateQueries({ queryKey: ['orders'] }); };
  const ok = (m: string) => () => { setMsg(m); setErr(''); refresh(); };
  const bad = (e: unknown) => { setErr(errMsg(e)); setMsg(''); };
  const cancel = useMutation({ mutationFn: () => post<any>(`/student/orders/${id}/cancel`, {}), onSuccess: (d) => { setMsg(d.message); refresh(); }, onError: bad });
  const otp = useMutation({ mutationFn: () => post(`/student/orders/${id}/otp`), onSuccess: ok('Sent. Open Alerts to see your pickup OTP.'), onError: bad });
  const again = useMutation({
    mutationFn: () => post<any>(`/student/cart/reorder/${id}`),
    onSuccess: (d) => { qc.setQueryData(['cart'], d.quote); setErr(''); setMsg(`Added ${d.added} item${d.added === 1 ? '' : 's'} to your cart.${d.unavailable ? ` ${d.unavailable} no longer available.` : ''}${d.printJobsSkipped ? ' Print jobs can\'t be repeated: upload your document again.' : ''}`); },
    onError: bad,
  });
  const review = useMutation({ mutationFn: () => post(`/student/orders/${id}/review`, { rating, comment }), onSuccess: ok('Thanks for your review!'), onError: bad });
  if (q.isLoading) return <Spinner />;
  const o = q.data; if (!o) return <Note>Order not found.</Note>;
  const flow = FLOW[o.vertical]; const idx = flow.indexOf(o.status);
  const remainingPaid = o.payments?.some((p: any) => p.payment_type === 'REMAINING' && p.status === 'SUCCESS');
  return (
    <>
      <Card className="grid gap-2">
        <div className="flex items-center justify-between"><h1 className="text-xl font-bold">#{o.order_code}</h1><Chip>{o.status.replace('_', ' ')}</Chip></div>
        <p className="text-sm text-mute">{o.shop_name}</p>
        {idx >= 0 && <div className="flex flex-wrap gap-1.5">{flow.map((s, n) => <span key={s} className={`rounded-full px-2 py-0.5 text-[11px] ${n < idx ? 'text-ok' : n === idx ? 'bg-accent text-white' : 'bg-page text-mute'}`}>{s}</span>)}</div>}
        {idx >= 0 && idx < flow.indexOf('READY') && <p className="text-sm">Estimated preparation time: <b>{o.est_prep_min}–{o.est_prep_max} minutes</b></p>}
      </Card>
      <Card>
        {o.items.map((i: any) => <Row key={i.id}><span className="text-sm">{i.description}{i.quantity > 1 && ` × ${i.quantity}`}</span><b className="text-sm">{money(i.line_total)}</b></Row>)}
        <div className="mt-2 grid gap-1 border-t border-line pt-2 text-sm">
          <div className="flex justify-between"><span>Subtotal</span><span>{money(o.subtotal)}</span></div>
          {Number(o.discount_amount) > 0 && <div className="flex justify-between text-ok"><span>Discount</span><span>−{money(o.discount_amount)}</span></div>}
          {Number(o.platform_fee) > 0 && <div className="flex justify-between"><span>Platform fee</span><span>{money(o.platform_fee)}</span></div>}
          <div className="flex justify-between font-extrabold"><span>Total</span><span>{money(o.total_amount)}</span></div>
          <div className="flex justify-between"><span>Advance (50%)</span><span>{money(o.advance_amount)}</span></div>
          <div className="flex justify-between"><span>Remaining</span><span>{remainingPaid ? 'Paid' : money(o.remaining_amount)}</span></div>
        </div>
      </Card>
      {o.status === 'PAYMENT_PENDING' && <Card className="grid gap-2"><b>Pay the 50% advance to place your order</b><PayPanel orderId={id} amount={o.advance_amount} label="Pay" onDone={ok('Payment received. Your order is placed.')} /></Card>}
      {o.status === 'READY' && (
        <Card className="grid gap-3">
          <b>Your order is ready. Visit the shop.</b>
          {!remainingPaid ? <><p className="text-sm text-mute">Pay the remaining amount here (or at the shop), then show your OTP.</p><PayPanel orderId={id} amount={o.remaining_amount} label="Pay" onDone={ok('Payment received. Show your OTP at the counter.')} /></> : <Note tone="ok">Remaining payment done. Show your OTP at the counter.</Note>}
          <p className="text-sm text-mute">Your pickup OTP was sent to your notifications. <Link to="/student/notifications" className="text-accent">Open Alerts</Link></p>
          <Button variant="ghost" loading={otp.isPending} onClick={() => otp.mutate()}>Send me a new OTP</Button>
        </Card>
      )}
      {o.canReview && (
        <Card className="grid gap-2"><b>How was {o.shop_name}?</b>
          <div className="flex gap-1">{[1, 2, 3, 4, 5].map((n) => <button key={n} aria-label={`${n} stars`} onClick={() => setRating(n)} className={`text-2xl ${n <= rating ? 'text-accent' : 'text-line'}`}>★</button>)}</div>
          <Input placeholder="Optional comment" value={comment} onChange={(e) => setComment(e.target.value)} />
          <Button loading={review.isPending} onClick={() => review.mutate()}>Submit review</Button></Card>
      )}
      <Note>{err}</Note><Note tone="ok">{msg}</Note>
      {['COLLECTED', 'REFUNDED', 'CANCELLED'].includes(o.status) && <div className="grid gap-2"><Button variant="ghost" loading={again.isPending} onClick={() => again.mutate()}>Order again</Button>{msg.startsWith('Added') && <Link to="/student/cart" className="text-center text-sm text-accent">Go to cart →</Link>}</div>}
      {o.canCancel && <Button variant="danger" loading={cancel.isPending} onClick={() => confirm('Cancel this order? Your refund depends on the shop\'s policy and how far the order has come.') && cancel.mutate()}>Cancel order</Button>}
    </>
  );
}
