import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { get, post } from '../lib/api';
import { when } from '../lib/format';
import { Button, Card, Spinner } from './ui';

export default function NotificationList() {
  const qc = useQueryClient();
  const q = useQuery({ queryKey: ['notifications'], queryFn: () => get<any[]>('/notifications'), refetchInterval: 15000 });   // upgrade to the SSE stream later for instant updates
  const refresh = () => { qc.invalidateQueries({ queryKey: ['notifications'] }); qc.invalidateQueries({ queryKey: ['unread'] }); };
  const readAll = useMutation({ mutationFn: () => post('/notifications/read-all'), onSuccess: refresh });
  const read = useMutation({ mutationFn: (id: number) => post(`/notifications/${id}/read`), onSuccess: refresh });
  if (q.isLoading) return <Spinner />;
  return (
    <>
      <div className="flex items-center justify-between"><h1 className="text-xl font-bold">Notifications</h1><Button variant="ghost" className="!py-1.5" onClick={() => readAll.mutate()}>Mark all read</Button></div>
      {!q.data?.length && <Card><p className="text-sm text-mute">Nothing yet.</p></Card>}
      {q.data?.map((n) => (
        <Card key={n.id} className={n.read_at ? 'opacity-70' : ''}>
          <button className="w-full text-left" onClick={() => !n.read_at && read.mutate(n.id)}>
            <div className="flex justify-between gap-2"><b className="text-sm">{n.title}</b><span className="text-xs text-mute">{when(n.created_at)}</span></div>
            {n.body && <p className="mt-1 text-sm text-mute">{n.body}</p>}
          </button>
        </Card>
      ))}
    </>
  );
}
