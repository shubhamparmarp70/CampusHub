import { NavLink, Outlet } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { AlertTriangle, BarChart3, Bell, Boxes, ClipboardList, LogOut, Package, ScrollText, Settings, ShieldCheck, ShoppingCart, Store, Tag, User, Wallet } from 'lucide-react';
import { useAuth } from '../lib/auth';
import { useService } from '../lib/service';
import { get } from '../lib/api';

const NAV = {
  student: [['/student/shops', 'Shops', Store], ['/student/cart', 'Cart', ShoppingCart], ['/student/orders', 'Orders', Package], ['/student/me', 'Me', User]],
  owner: [['/owner/orders', 'Orders', ClipboardList], ['/owner/catalog', 'Catalog', Boxes], ['/owner/offers', 'Offers', Tag], ['/owner/insights', 'Insights', BarChart3], ['/owner/shop', 'Shop', Settings]],
  admin: [['/admin', 'Overview', ShieldCheck], ['/admin/money', 'Money', Wallet], ['/admin/issues', 'Issues', AlertTriangle], ['/admin/audit', 'Audit', ScrollText]],
} as const;

export default function Layout() {
  const { user, logout } = useAuth();
  const { vertical, setVertical } = useService();
  const unread = useQuery({ queryKey: ['unread'], queryFn: () => get<number>('/notifications/unread-count'), refetchInterval: 15000 });
  if (!user) return null;
  return (
    <div className="mx-auto flex h-full max-w-md flex-col bg-page md:my-4 md:h-[calc(100%-2rem)] md:overflow-hidden md:rounded-[28px] md:border md:border-line md:shadow-2xl">
      <header className="safe-top border-b border-line bg-surface px-4 pb-3 pt-3">
        <div className="flex items-center gap-2">
          <b className="flex-1 text-lg tracking-tight">Campus Hub</b>
          <span className="rounded-full bg-page px-2.5 py-0.5 text-xs font-semibold">{user.role}</span>
          <NavLink to={`/${user.role}/notifications`} aria-label="Notifications" className="relative p-1 text-mute">
            <Bell className="h-5 w-5" />
            {(unread.data ?? 0) > 0 && <span className="absolute -right-1 -top-0.5 rounded-full bg-bad px-1.5 text-[10px] font-bold text-white">{unread.data}</span>}
          </NavLink>
          <button aria-label="Log out" onClick={logout} className="p-1 text-mute"><LogOut className="h-5 w-5" /></button>
        </div>
        {user.role === 'student' && (
          <div className="mt-3 flex gap-1 rounded-xl bg-page p-1" role="tablist">
            {([['FOOD', '🍽 Canteen'], ['DESK', '🖨 CampusDesk']] as const).map(([v, label]) => (
              <button key={v} role="tab" aria-selected={vertical === v} onClick={() => setVertical(v)}
                className={`flex-1 rounded-lg py-2 text-sm font-semibold ${vertical === v ? 'bg-accent text-white' : 'text-mute'}`}>{label}</button>
            ))}
          </div>
        )}
      </header>
      <main className="flex-1 overflow-auto p-4"><div className="grid content-start gap-3"><Outlet /></div></main>
      <nav className="safe-bottom flex border-t border-line bg-surface">
        {NAV[user.role].map(([to, label, Icon]) => (
          <NavLink key={to} to={to} end className={({ isActive }) => `flex flex-1 flex-col items-center gap-0.5 py-2 text-xs font-semibold ${isActive ? 'text-accent' : 'text-mute'}`}>
            <Icon className="h-5 w-5" />{label}
          </NavLink>
        ))}
      </nav>
    </div>
  );
}
