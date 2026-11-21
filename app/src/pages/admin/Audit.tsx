import { useState } from 'react';
import { useInfiniteQuery } from '@tanstack/react-query';
import { get } from '../../lib/api';
import { when } from '../../lib/format';
import { Button, Card, Chip, Note, Row, Spinner } from '../../components/ui';

const ACTIONS = ['LOGIN', 'LOGIN_FAILED', 'REGISTER', 'ORDER_CREATED', 'ORDER_STATUS', 'ORDER_COLLECTED', 'PAYMENT_SUCCESS', 'PAYMENT_AMOUNT_MISMATCH', 'LATE_PAYMENT', 'REFUND_STARTED', 'REFUND_COMPLETED',
  'PRICE_CHANGED', 'PRINT_PRICES_CHANGED', 'DISCOUNT_CREATED', 'PLATFORM_FEE_CHANGED', 'SETTING_CHANGED', 'SHOP_APPROVED', 'SHOP_DISABLED', 'USER_SUSPENDED', 'USER_ACTIVE', 'FILE_ACCESS', 'WEBHOOK_BAD_SIGNATURE', 'REFRESH_REUSE_DETECTED'];

export default function Audit() {
  const [action, setAction] = useState('');
  const q = useInfiniteQuery({
    queryKey: ['audit', action], initialPageParam: undefined as number | undefined,
    queryFn: ({ pageParam }) => get<any[]>('/admin/audit-logs', { action: action || undefined, beforeId: pageParam }),
    getNextPageParam: (last) => (last.length === 100 ? last[last.length - 1].id : undefined),
  });
  const rows = q.data?.pages.flat() ?? [];
  const risky = (a: string) => ['LOGIN_FAILED', 'WEBHOOK_BAD_SIGNATURE', 'REFRESH_REUSE_DETECTED', 'PAYMENT_AMOUNT_MISMATCH', 'LATE_PAYMENT'].includes(a);
  return (
    <>
      <h1 className="text-xl font-bold">Audit log</h1>
      <select className="w-full rounded-xl border border-line bg-page px-3 py-2.5 text-sm" value={action} onChange={(e) => setAction(e.target.value)}>
        <option value="">All actions</option>{ACTIONS.map((a) => <option key={a} value={a}>{a}</option>)}
      </select>
      {q.isLoading ? <Spinner /> : (
        <Card>
          {!rows.length && <Note tone="mute">Nothing recorded for this filter.</Note>}
          {rows.map((r) => (
            <Row key={r.id}>
              <div className="min-w-0">
                <div className="flex flex-wrap items-center gap-1.5"><Chip>{r.action}</Chip>{risky(r.action) && <span className="text-xs font-semibold text-bad">check</span>}</div>
                <p className="mt-0.5 truncate text-xs text-mute">{r.actor_email ?? (r.actor_id ? `user ${r.actor_id}` : 'system')}{r.entity ? ` · ${r.entity}${r.entity_id ? ' ' + r.entity_id : ''}` : ''}</p>
              </div>
              <span className="whitespace-nowrap text-xs text-mute">{when(r.created_at)}</span>
            </Row>
          ))}
          {q.hasNextPage && <Button variant="ghost" className="mt-2 w-full" loading={q.isFetchingNextPage} onClick={() => q.fetchNextPage()}>Load more</Button>}
        </Card>
      )}
    </>
  );
}
