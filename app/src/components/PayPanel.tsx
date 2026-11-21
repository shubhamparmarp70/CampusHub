import { useState } from 'react';
import { useMutation } from '@tanstack/react-query';
import { post, errMsg } from '../lib/api';
import { SANDBOX, money } from '../lib/format';
import { Button, Note } from './ui';

/** Pays whatever is due on an order (advance while awaiting payment, remaining once READY). Only PhonePe and Google Pay. */
export default function PayPanel({ orderId, amount, label, onDone }: { orderId: number; amount: number; label: string; onDone: () => void }) {
  const [txn, setTxn] = useState<string | null>(null);
  const [err, setErr] = useState('');
  const pay = useMutation({
    mutationFn: (method: string) => post<any>(`/student/orders/${orderId}/pay`, { method }),
    onSuccess: (p) => { setErr(''); if (SANDBOX) setTxn(p.transactionId); else window.location.href = p.paymentUrl; },
    onError: (e) => setErr(errMsg(e)),
  });
  const confirm = useMutation({
    mutationFn: () => post(`/payments/sandbox/${txn}/succeed`),
    onSuccess: () => { setTxn(null); onDone(); },
    onError: (e) => setErr(errMsg(e)),
  });
  return (
    <div className="grid gap-2">
      {txn ? (
        <>
          <Note tone="mute">Sandbox mode: no real money moves. Press the button to pretend the payment succeeded.</Note>
          <Button loading={confirm.isPending} onClick={() => confirm.mutate()}>Simulate successful payment</Button>
        </>
      ) : (
        <div className="flex gap-2">
          <Button className="flex-1" loading={pay.isPending} onClick={() => pay.mutate('PHONEPE')}>{label} {money(amount)} · PhonePe</Button>
          <Button className="flex-1" variant="ghost" loading={pay.isPending} onClick={() => pay.mutate('GOOGLE_PAY')}>Google Pay</Button>
        </div>
      )}
      <Note>{err}</Note>
    </div>
  );
}
