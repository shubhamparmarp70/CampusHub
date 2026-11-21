import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Minus, Plus, Trash2 } from 'lucide-react';
import { del, get, patch, post, put, errMsg } from '../../lib/api';
import { SANDBOX, money } from '../../lib/format';
import { Button, Card, Input, Note, Row, Spinner } from '../../components/ui';

/** Every number here comes from the server's quote. The app never calculates or sends prices. */
export default function Cart() {
  const qc = useQueryClient(); const nav = useNavigate();
  const [code, setCode] = useState(''); const [err, setErr] = useState('');
  const [pending, setPending] = useState<{ orderId: number; txn: string; url: string } | null>(null);
  const cart = useQuery({ queryKey: ['cart'], queryFn: () => get<any>('/student/cart') });
  const upd = { onSuccess: (q: any) => { qc.setQueryData(['cart'], q); setErr(''); }, onError: (e: unknown) => setErr(errMsg(e)) };
  const qty = useMutation({ mutationFn: (v: { id: number; quantity: number }) => patch<any>(`/student/cart/items/${v.id}`, { quantity: v.quantity }), ...upd });
  const rm = useMutation({ mutationFn: (id: number) => del<any>(`/student/cart/items/${id}`), ...upd });
  const clear = useMutation({ mutationFn: () => del<any>('/student/cart'), ...upd });
  const disc = useMutation({ mutationFn: () => put<any>('/student/cart/discount', { code }), ...upd });
  const checkout = useMutation({
    mutationFn: (method: string) => post<any>('/student/orders/checkout', { method }),
    onSuccess: (o) => { qc.invalidateQueries({ queryKey: ['cart'] }); if (SANDBOX) setPending({ orderId: o.orderId, txn: o.payment.transactionId, url: o.payment.paymentUrl }); else window.location.href = o.payment.paymentUrl; },
    onError: (e) => setErr(errMsg(e)),
  });
  const confirm = useMutation({ mutationFn: () => post(`/payments/sandbox/${pending!.txn}/succeed`), onSuccess: () => nav(`/student/orders/${pending!.orderId}`), onError: (e) => setErr(errMsg(e)) });

  if (cart.isLoading) return <Spinner />;
  const q = cart.data; const lines: any[] = q?.lines ?? [];
  if (pending) return (
    <Card className="grid gap-3"><h1 className="text-xl font-bold">Pay the 50% advance</h1>
      <Note tone="mute">Sandbox mode: no real money moves.</Note>
      <Button loading={confirm.isPending} onClick={() => confirm.mutate()}>Simulate successful payment</Button>
      <Button variant="ghost" onClick={() => nav(`/student/orders/${pending.orderId}`)}>Pay later from my orders</Button><Note>{err}</Note></Card>
  );
  return (
    <>
      <div className="flex items-center justify-between"><h1 className="text-xl font-bold">Your cart</h1>{lines.length > 0 && <button className="text-sm text-mute" onClick={() => clear.mutate()}>Clear</button>}</div>
      {!lines.length && <Card><Note tone="mute">Your cart is empty. Pick a shop and add something.</Note></Card>}
      {lines.length > 0 && (
        <Card>
          {lines.map((l) => (
            <Row key={l.cartItemId}>
              <div className="min-w-0"><b className="block text-sm">{l.productId ? l.description : 'Print job'}</b>
                <p className="text-xs text-mute">{l.productId ? `${money(l.unitPrice)} each` : l.description}</p></div>
              <div className="text-right">
                <div className="text-sm font-bold">{money(l.lineTotal)}</div>
                <div className="mt-1 flex items-center justify-end gap-2">
                  {l.productId && <><button aria-label="Less" onClick={() => qty.mutate({ id: l.cartItemId, quantity: l.quantity - 1 })}><Minus className="h-4 w-4" /></button><span className="text-sm">{l.quantity}</span>
                    <button aria-label="More" onClick={() => qty.mutate({ id: l.cartItemId, quantity: l.quantity + 1 })}><Plus className="h-4 w-4" /></button></>}
                  <button aria-label="Remove" onClick={() => rm.mutate(l.cartItemId)}><Trash2 className="h-4 w-4 text-bad" /></button>
                </div>
              </div>
            </Row>
          ))}
        </Card>
      )}
      {q?.issues?.map((i: string) => <Note key={i}>{i}</Note>)}
      {lines.length > 0 && (
        <>
          <Card className="grid gap-2">
            <div className="flex gap-2"><Input placeholder="Offer code" value={code} onChange={(e) => setCode(e.target.value)} /><Button variant="ghost" loading={disc.isPending} onClick={() => disc.mutate()}>Apply</Button></div>
            <Note>{q?.discountError}</Note>
            <div className="grid gap-1 text-sm">
              <div className="flex justify-between"><span>Subtotal</span><span>{money(q.subtotal)}</span></div>
              {Number(q.discount) > 0 && <div className="flex justify-between text-ok"><span>Discount {q.discountName && `(${q.discountName})`}</span><span>−{money(q.discount)}</span></div>}
              {Number(q.platformFee) > 0 && <div className="flex justify-between"><span>Platform fee</span><span>{money(q.platformFee)}</span></div>}
              <div className="flex justify-between border-t border-line pt-1 text-base font-extrabold"><span>Total</span><span>{money(q.total)}</span></div>
              <div className="flex justify-between"><span>Pay now (50%)</span><b>{money(q.advance)}</b></div>
              <div className="flex justify-between text-mute"><span>Pay at pickup</span><span>{money(q.remaining)}</span></div>
            </div>
            <p className="text-xs text-mute">Estimated preparation time: {q.estimatedMinMinutes}–{q.estimatedMaxMinutes} minutes. You'll get a notification when it's ready.</p>
          </Card>
          <Note>{err}</Note>
          <div className="flex gap-2">
            <Button className="flex-1" loading={checkout.isPending} disabled={!!q.issues?.length} onClick={() => checkout.mutate('PHONEPE')}>Pay with PhonePe</Button>
            <Button className="flex-1" variant="ghost" loading={checkout.isPending} disabled={!!q.issues?.length} onClick={() => checkout.mutate('GOOGLE_PAY')}>Google Pay</Button>
          </div>
        </>
      )}
    </>
  );
}
