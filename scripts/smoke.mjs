// End-to-end check of the running backend: the same calls the React app makes, in the same order.
// Usage (Node 18+):  ADMIN_EMAIL=you@example.com ADMIN_PASSWORD='your-12+char-password' node scripts/smoke.mjs
// Optional: API=http://localhost:5173/api  (to go through the Vite dev proxy and prove the app's route to the backend)
const API = process.env.API || 'http://localhost:8080/api';
const stamp = Date.now();
let passed = 0;
async function call(method, path, token, body) {
  const r = await fetch(API + path, { method, headers: { 'Content-Type': 'application/json', ...(token ? { Authorization: 'Bearer ' + token } : {}) }, body: body ? JSON.stringify(body) : undefined });
  const j = await r.json().catch(() => ({}));
  return { status: r.status, ...j };
}
const step = (name) => process.stdout.write(`• ${name} ... `);
const done = () => { passed++; console.log('ok'); };
function need(r, label) { if (r.status >= 300) { console.log('FAILED'); throw new Error(`${label}: HTTP ${r.status} ${r.message ?? ''} ${JSON.stringify(r.data ?? '')}`); } return r.data; }
function expect(cond, label) { if (!cond) { console.log('FAILED'); throw new Error('Expected: ' + label); } }

const { ADMIN_EMAIL, ADMIN_PASSWORD } = process.env;
if (!ADMIN_EMAIL || !ADMIN_PASSWORD) { console.error('Set ADMIN_EMAIL and ADMIN_PASSWORD (the same values the backend uses to create the admin).'); process.exit(2); }

try {
  step('admin signs in'); const admin = need(await call('POST', '/auth/login', null, { email: ADMIN_EMAIL, password: ADMIN_PASSWORD }), 'admin login').accessToken; done();
  step('sign-up needs the SMS code: login is blocked until the number is verified');
  const phoneOf = (d) => d + String(stamp).slice(-9);
  async function signUp(role, name, email, phone) {
    const pending = need(await call('POST', '/auth/register', null, { fullName: name, email, password: 'password123', phone, role }), 'register');
    expect(pending.phoneVerificationRequired === true, 'register asks for the SMS code and returns no token');
    const blocked = await call('POST', '/auth/login', null, { email, password: 'password123' });
    expect(blocked.status === 403 && blocked.data?.code === 'PHONE_NOT_VERIFIED', 'login is refused before verification');
    const sms = await call('GET', '/dev/last-sms?phone=' + encodeURIComponent('+91' + phone));
    if (sms.status !== 200) throw new Error('Cannot read the SMS code. The smoke test needs app.sms.provider=console (the default for development).');
    const code = /(\d{6})/.exec(sms.data.message)[1];
    const wrong = await call('POST', '/auth/verify-phone', null, { email, otp: code === '000000' ? '111111' : '000000' });
    expect(wrong.status === 400, 'a wrong code is rejected');
    return need(await call('POST', '/auth/verify-phone', null, { email, otp: code }), 'verify phone').accessToken;
  }
  const owner = await signUp('SHOP_OWNER', 'Smoke Owner', `owner${stamp}@test.in`, phoneOf('9'));
  const student = await signUp('STUDENT', 'Smoke Student', `student${stamp}@test.in`, phoneOf('8')); done();
  step('roles are enforced (student cannot use owner or admin APIs)');
  expect((await call('GET', '/shop-owner/orders', student)).status === 403, 'student gets 403 on owner API');
  expect((await call('GET', '/admin/users', student)).status === 403, 'student gets 403 on admin API');
  expect((await call('GET', '/student/orders')).status === 401, 'no token gets 401'); done();
  step('owner creates a CampusDesk shop; adding products is locked until the admin approves, then works');
  const catId = need(await call('POST', '/admin/categories', admin, { vertical: 'DESK', name: `Stationery ${stamp}` }), 'create category');
  const shopId = need(await call('POST', '/shop-owner/shop', owner, { vertical: 'DESK', name: `Smoke Copy ${stamp}` }), 'create shop');
  const locked = await call('POST', '/shop-owner/products', owner, { categoryId: catId, name: 'Pen', kind: 'ITEM', sellingPrice: 10, initialStock: 100, minStock: 5 });
  expect(locked.status === 403 && locked.data?.code === 'SHOP_NOT_APPROVED', 'owner cannot add products before approval');
  need(await call('PATCH', `/admin/shops/${shopId}/approval`, admin, { approval: 'APPROVED' }), 'approve shop'); done();
  step('owner sets printing prices, adds a product with stock, opens the shop');
  need(await call('PUT', '/shop-owner/printing-prices', owner, { rates: [{ size: 'A4', color: 'BLACK_WHITE', price: 2 }, { size: 'A4', color: 'COLOR', price: 10 }], duplexDiscountPerPage: 0.5, spiralBinding: 40, tapeBinding: 25, laminationPerPage: 15, staplingPerCopy: 5, duplexRule: 'PER_PAGE' }), 'printing prices');
  const productId = need(await call('POST', '/shop-owner/products', owner, { categoryId: catId, name: 'Pen', kind: 'ITEM', sellingPrice: 10, initialStock: 100, minStock: 5 }), 'create product');
  need(await call('PATCH', '/shop-owner/shop/status', owner, { openStatus: 'OPEN' }), 'open shop'); done();
  step('student sees the shop and its product');
  const shops = need(await call('GET', '/shops?vertical=DESK', student), 'list shops'); expect(shops.some((s) => s.id === shopId), 'shop is listed');
  const products = need(await call('GET', `/shops/${shopId}/products`, student), 'list products'); expect(products.some((p) => p.id === productId && Number(p.in_stock) === 1), 'product is in stock'); done();
  step('student adds 3 pens; server prices the cart and splits 50%');
  const q = need(await call('POST', '/student/cart/items', student, { shopId, productId, quantity: 3 }), 'add to cart');
  expect(Number(q.subtotal) === 30, 'subtotal is 30 (3 x 10)');
  expect(Math.abs(Number(q.advance) + Number(q.remaining) - Number(q.total)) < 0.001, 'advance + remaining = total'); done();
  step('checkout, then payment confirmed twice (must only count once)');
  const co = need(await call('POST', '/student/orders/checkout', student, { method: 'PHONEPE' }), 'checkout');
  const orderId = co.orderId, txn = co.payment.transactionId;
  need(await call('POST', `/payments/sandbox/${txn}/succeed`, student), 'confirm payment'); need(await call('POST', `/payments/sandbox/${txn}/succeed`, student), 'confirm payment again');
  const placed = need(await call('GET', `/student/orders/${orderId}`, student), 'read order'); expect(placed.status === 'PLACED', 'order is PLACED');
  const inv = need(await call('GET', '/shop-owner/inventory', owner), 'inventory'); expect(inv.find((i) => i.productId === productId).quantity === 97, 'stock went 100 -> 97 exactly once'); done();
  step('owner accepts, processes and marks READY');
  for (const s of ['ACCEPTED', 'PROCESSING', 'READY']) need(await call('POST', `/shop-owner/orders/${orderId}/status`, owner, { status: s }), 'status ' + s); done();
  step('student receives the pickup OTP notification');
  const notes = need(await call('GET', '/notifications', student), 'notifications');
  const otp = notes.map((n) => /OTP: (\d{6})/.exec(n.body ?? '')).find(Boolean)?.[1]; expect(otp, 'an OTP appears in notifications'); done();
  step('pickup is refused before the remaining payment, then works after it');
  const early = await call('POST', `/shop-owner/orders/${orderId}/verify-pickup`, owner, { otp }); expect(early.status === 409 && early.data?.code === 'REMAINING_PAYMENT_PENDING', 'refused until remaining amount is paid');
  const pay2 = need(await call('POST', `/student/orders/${orderId}/pay`, student, { method: 'GOOGLE_PAY' }), 'pay remaining');
  need(await call('POST', `/payments/sandbox/${pay2.transactionId}/succeed`, student), 'confirm remaining');
  const wrong = await call('POST', `/shop-owner/orders/${orderId}/verify-pickup`, owner, { otp: otp === '000000' ? '111111' : '000000' }); expect(wrong.status === 400, 'wrong OTP is rejected');
  need(await call('POST', `/shop-owner/orders/${orderId}/verify-pickup`, owner, { otp }), 'verify pickup');
  expect((await call('POST', `/shop-owner/orders/${orderId}/verify-pickup`, owner, { otp })).status === 409, 'the OTP cannot be used twice');
  const fin = need(await call('GET', `/student/orders/${orderId}`, student), 'final order'); expect(fin.status === 'COLLECTED', 'order is COLLECTED'); done();
  step('student reviews the shop; owner and admin dashboards load');
  need(await call('POST', `/student/orders/${orderId}/review`, student, { rating: 5, comment: 'Quick!' }), 'review');
  need(await call('GET', '/shop-owner/analytics', owner), 'owner analytics'); need(await call('GET', '/admin/analytics', admin), 'admin analytics');
  need(await call('GET', '/admin/settlements?from=2020-01-01&to=2099-01-01', admin), 'settlements'); done();
  console.log(`\nAll ${passed} groups passed. Frontend and backend agree on the whole order flow.`);
} catch (e) { console.error('\n' + e.message); process.exit(1); }
