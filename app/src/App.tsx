import { Navigate, Outlet, Route, Routes } from 'react-router-dom';
import { useAuth, homeFor, Role } from './lib/auth';
import { Spinner } from './components/ui';
import Layout from './components/Layout';
import Login from './pages/auth/Login';
import VerifyPhone from './pages/auth/VerifyPhone';
import Register from './pages/auth/Register';
import { ForgotPassword, ResetPassword, VerifyEmail } from './pages/auth/Forgot';
import Shops from './pages/student/Shops';
import ShopDetail from './pages/student/ShopDetail';
import Cart from './pages/student/Cart';
import Orders from './pages/student/Orders';
import OrderDetail from './pages/student/OrderDetail';
import Notifications from './pages/Notifications';
import Me from './pages/student/Me';
import OwnerOrders from './pages/owner/OwnerOrders';
import Catalog from './pages/owner/Catalog';
import Offers from './pages/owner/Offers';
import ShopSettings from './pages/owner/ShopSettings';
import AdminHome from './pages/admin/AdminHome';
import Money from './pages/admin/Money';
import Issues from './pages/admin/Issues';
import Audit from './pages/admin/Audit';
import Insights from './pages/owner/Insights';

/** Hides screens by role. This is only a convenience: the API enforces the same rules on the server. */
function Guard({ role }: { role: Role }) {
  const { user, ready } = useAuth();
  if (!ready) return <Spinner />;
  if (!user) return <Navigate to="/login" replace />;
  if (user.role !== role) return <Navigate to={homeFor(user.role)} replace />;
  return <Layout />;
}
function Home() {
  const { user, ready } = useAuth();
  if (!ready) return <Spinner />;
  return <Navigate to={user ? homeFor(user.role) : '/login'} replace />;
}
const Public = () => { const { user, ready } = useAuth(); return ready && user ? <Navigate to={homeFor(user.role)} replace /> : <Outlet />; };

export default function App() {
  return (
    <Routes>
      <Route path="/" element={<Home />} />
      <Route element={<Public />}><Route path="/login" element={<Login />} /><Route path="/register" element={<Register />} /></Route>
      <Route path="/verify-phone" element={<VerifyPhone />} /><Route path="/forgot-password" element={<ForgotPassword />} /><Route path="/reset-password" element={<ResetPassword />} /><Route path="/verify-email" element={<VerifyEmail />} />
      <Route path="/student" element={<Guard role="student" />}>
        <Route index element={<Navigate to="shops" replace />} />
        <Route path="shops" element={<Shops />} /><Route path="shops/:id" element={<ShopDetail />} />
        <Route path="cart" element={<Cart />} /><Route path="orders" element={<Orders />} /><Route path="orders/:id" element={<OrderDetail />} />
        <Route path="me" element={<Me />} /><Route path="notifications" element={<Notifications />} />
      </Route>
      <Route path="/owner" element={<Guard role="owner" />}>
        <Route index element={<Navigate to="orders" replace />} /><Route path="orders" element={<OwnerOrders />} /><Route path="catalog" element={<Catalog />} /><Route path="offers" element={<Offers />} /><Route path="shop" element={<ShopSettings />} /><Route path="insights" element={<Insights />} /><Route path="notifications" element={<Notifications />} />
      </Route>
      <Route path="/admin" element={<Guard role="admin" />}>
        <Route index element={<AdminHome />} /><Route path="money" element={<Money />} /><Route path="issues" element={<Issues />} /><Route path="audit" element={<Audit />} /><Route path="notifications" element={<Notifications />} />
      </Route>
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  );
}
