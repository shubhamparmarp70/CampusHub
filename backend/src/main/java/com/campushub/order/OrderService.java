package com.campushub.order;
import com.campushub.cart.*;
import com.campushub.catalog.*;
import com.campushub.common.*;
import com.campushub.notification.NotificationService;
import com.campushub.payments.*;
import com.campushub.refund.RefundService;
import com.campushub.security.AuthUser;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import static com.campushub.order.OrderStatus.*;

@Service
public class OrderService {
  private static final Set<OrderStatus> OWNER_TARGETS = EnumSet.of(ACCEPTED, QUEUED, PROCESSING, PRINTING, BINDING, READY);
  private final Db db; private final PricingEngine engine; private final OrderTransitions tr; private final PaymentService payments; private final DiscountService discounts;
  private final NotificationService notes; private final OtpService otp; private final ShopAccess access; private final AuditService audit; private final RefundService refunds; private final Settings settings;
  public OrderService(Db db, PricingEngine e, OrderTransitions tr, PaymentService p, DiscountService d, NotificationService n, OtpService o, ShopAccess a, AuditService au, RefundService r, Settings s){
    this.db = db; engine = e; this.tr = tr; payments = p; discounts = d; notes = n; otp = o; access = a; audit = au; refunds = r; settings = s; }

  // ---------------- checkout ----------------
  /** Recomputes everything from the database, writes the order with a price snapshot, and starts the 50% advance payment. Stock is NOT touched until the advance is verified. */
  @Transactional
  public Map<String,Object> checkout(AuthUser u, String method){
    if (!"ACTIVE".equals(db.scalar(String.class, "SELECT status FROM users WHERE id=?", u.id()).orElse(""))) throw new BusinessException(HttpStatus.FORBIDDEN, "ACCOUNT_INACTIVE", "Your account can't place orders right now.");
    var cart = db.one("SELECT id, shop_id FROM carts WHERE student_id=?", u.id()).orElseThrow(() -> BusinessException.badRequest("EMPTY_CART", "Your cart is empty."));
    var shop = db.one("SELECT vertical, approval, open_status FROM shops WHERE id=?", cart.get("shop_id")).orElseThrow();
    if (!"APPROVED".equals(shop.get("approval"))) throw BusinessException.conflict("SHOP_UNAVAILABLE", "This shop isn't available right now.");
    if (!"OPEN".equals(shop.get("open_status"))) throw BusinessException.conflict("SHOP_CLOSED", "This shop is closed right now.");
    PaymentMethod pm = PaymentMethod.of(method);
    Quote q = engine.quote(u.id(), true);
    if (q.lines().isEmpty()) throw BusinessException.badRequest("EMPTY_CART", "Your cart is empty.");
    if (q.total().signum() <= 0 || q.advance().signum() <= 0) throw BusinessException.badRequest("ZERO_TOTAL", "The order total must be more than zero.");
    long orderId = db.insert("INSERT INTO orders(order_code, student_id, shop_id, discount_id, status, payment_status, subtotal, discount_amount, tax_amount, platform_fee, total_amount, advance_amount, remaining_amount, est_prep_min, est_prep_max) VALUES (?,?,?,?,'CREATED','UNPAID',?,?,0,?,?,?,?,?,?)",
      Tokens.random(12), u.id(), q.shopId(), q.discountId(), q.subtotal(), q.discount(), q.platformFee(), q.total(), q.advance(), q.remaining(), q.estimatedMinMinutes(), q.estimatedMaxMinutes());
    String code = ("FOOD".equals(shop.get("vertical")) ? "CN" : "CD") + (1000 + orderId);
    db.update("UPDATE orders SET order_code=? WHERE id=?", code, orderId);
    tr.recordCreated(orderId, u.id());
    for (Quote.Line l : q.lines()) {
      if (l.productId() != null) {
        db.insert("INSERT INTO order_items(order_id, product_id, description, unit_price, quantity, line_total) VALUES (?,?,?,?,?,?)", orderId, l.productId(), l.description(), l.unitPrice(), l.quantity(), l.lineTotal());
      } else {
        long job = db.insert("INSERT INTO print_jobs(order_id, file_id) VALUES (?,?)", orderId, l.fileId());
        var c = l.config(); var b = l.breakdown();
        db.update("INSERT INTO print_configurations(print_job_id, page_size, color_mode, sides, copies, page_range, orientation, binding, lamination, stapling, billable_pages, sheets) VALUES (?,?,?,?,?,?,?,?,?,?,?,?)",
          job, c.size().name(), c.color().name(), c.sides().name(), c.copies(), c.pageRange(), c.orientation().name(), c.binding().name(), c.lamination(), c.stapling(), b.billablePages(), b.sheets());
        db.insert("INSERT INTO order_items(order_id, print_job_id, description, unit_price, quantity, line_total) VALUES (?,?,?,?,1,?)", orderId, job, l.description(), l.unitPrice(), l.lineTotal());
      }
    }
    if (q.discountId() != null) discounts.redeem(orderId, q.discountId(), u.id());
    tr.move(orderId, PAYMENT_PENDING, u.id(), "Waiting for the 50% advance");
    var pay = payments.start(orderId, "ADVANCE", q.advance(), pm);
    db.update("DELETE FROM carts WHERE id=?", cart.get("id"));
    audit.log(u.id(), u.email(), "ORDER_CREATED", "order", code);
    Map<String,Object> out = new LinkedHashMap<>();
    out.put("orderId", orderId); out.put("orderCode", code); out.put("subtotal", q.subtotal()); out.put("discount", q.discount()); out.put("platformFee", q.platformFee()); out.put("total", q.total()); out.put("advanceAmount", q.advance()); out.put("remainingAmount", q.remaining());
    out.put("estimatedPrepTime", q.estimatedMinMinutes() + "–" + q.estimatedMaxMinutes() + " minutes"); out.put("payment", pay);
    return out;
  }

  /** Unpaid orders don't hold stock or discount uses forever. */
  @Transactional
  public void expireOne(long orderId){
    var o = db.one("SELECT status FROM orders WHERE id=? FOR UPDATE", orderId).orElse(null);
    if (o == null || !"PAYMENT_PENDING".equals(o.get("status"))) return;
    tr.move(orderId, FAILED, null, "Payment not completed in time");
    db.update("UPDATE payments SET status='FAILED' WHERE order_id=? AND status IN ('INITIATED','PENDING')", orderId);
    discounts.release(orderId);
  }
  public List<Long> expiredUnpaid(){
    int mins = settings.getInt("payment_timeout_minutes", 30);
    return db.rows("SELECT id FROM orders WHERE status='PAYMENT_PENDING' AND created_at < NOW(3) - INTERVAL ? MINUTE LIMIT 200", mins).stream().map(r -> Db.l(r.get("id"))).toList();
  }

  // ---------------- student reads ----------------
  public List<Map<String,Object>> studentOrders(long studentId){
    return db.rows("SELECT o.id, o.order_code, o.status, o.payment_status, o.total_amount, o.est_prep_min, o.est_prep_max, o.created_at, s.name shop_name, s.vertical FROM orders o JOIN shops s ON s.id=o.shop_id WHERE o.student_id=? ORDER BY o.id DESC LIMIT 100", studentId); }

  public Map<String,Object> studentOrder(long studentId, long orderId){
    var o = db.one("SELECT o.id, o.order_code, o.status, o.payment_status, o.subtotal, o.discount_amount, o.tax_amount, o.platform_fee, o.total_amount, o.advance_amount, o.remaining_amount, o.est_prep_min, o.est_prep_max, o.created_at, o.updated_at, s.id shop_id, s.name shop_name, s.vertical FROM orders o JOIN shops s ON s.id=o.shop_id WHERE o.id=? AND o.student_id=?", orderId, studentId)
      .orElseThrow(() -> BusinessException.notFound("Order not found."));
    Map<String,Object> m = new LinkedHashMap<>(o); detail(m, orderId, true);
    var otpRow = db.one("SELECT used_at IS NOT NULL AS used, expires_at > ? AS valid FROM pickup_otps WHERE order_id=?", LocalDateTime.now(), orderId);
    m.put("pickupOtpStatus", otpRow.map(r -> Db.bool(r.get("used")) ? "USED" : Db.bool(r.get("valid")) ? "ACTIVE" : "EXPIRED").orElse("NONE"));   // the code itself is only ever sent by notification
    String st = (String) o.get("status");
    m.put("canCancel", Set.of("PAYMENT_PENDING","PLACED","ACCEPTED","QUEUED","PROCESSING","PRINTING","BINDING","READY").contains(st));
    m.put("canPay", st.equals("PAYMENT_PENDING") || (st.equals("READY") && db.count("SELECT COUNT(*) FROM payments WHERE order_id=? AND payment_type='REMAINING' AND status='SUCCESS'", orderId) == 0));
    m.put("canReview", st.equals("COLLECTED") && db.count("SELECT COUNT(*) FROM reviews WHERE order_id=?", orderId) == 0);
    return m;
  }
  private void detail(Map<String,Object> m, long orderId, boolean student){
    m.put("items", db.rows("SELECT id, description, unit_price, quantity, line_total FROM order_items WHERE order_id=?", orderId));
    m.put("printJobs", db.rows("SELECT pj.id, pj.file_id fileId, pj.status, pc.page_size, pc.color_mode, pc.sides, pc.copies, pc.page_range, pc.orientation, pc.binding, pc.lamination, pc.stapling, pc.billable_pages, pc.sheets FROM print_jobs pj JOIN print_configurations pc ON pc.print_job_id=pj.id WHERE pj.order_id=?", orderId));
    m.put("history", db.rows("SELECT previous_status, new_status, remarks, created_at FROM order_status_history WHERE order_id=? ORDER BY id", orderId));
    if (student) m.put("payments", db.rows("SELECT id, payment_type, amount, payment_method, status, created_at, completed_at FROM payments WHERE order_id=? ORDER BY id", orderId));
  }

  @Transactional
  public void resendOtp(long studentId, long orderId){
    var o = db.one("SELECT order_code, shop_id, status FROM orders WHERE id=? AND student_id=? FOR UPDATE", orderId, studentId).orElseThrow(() -> BusinessException.notFound("Order not found."));
    if (!"READY".equals(o.get("status"))) throw BusinessException.conflict("NOT_READY", "Your order isn't ready yet.");
    if (db.count("SELECT COUNT(*) FROM pickup_otps WHERE order_id=? AND created_at > NOW(3) - INTERVAL 60 SECOND", orderId) > 0) throw new BusinessException(HttpStatus.TOO_MANY_REQUESTS, "OTP_TOO_SOON", "Please wait a minute before asking for another OTP.");
    String code = otp.generate(orderId);
    notes.notify(studentId, orderId, "ORDER", "Your new pickup OTP", "OTP for order " + o.get("order_code") + ": " + code);
  }

  // ---------------- owner ----------------
  public List<Map<String,Object>> ownerOrders(AuthUser u, String status){
    return db.rows("SELECT o.id, o.order_code, o.status, o.payment_status, o.total_amount, o.advance_amount, o.remaining_amount, o.est_prep_min, o.est_prep_max, o.created_at, SUBSTRING_INDEX(us.full_name,' ',1) student_name FROM orders o JOIN users us ON us.id=o.student_id WHERE o.shop_id=? AND o.status NOT IN ('CREATED','PAYMENT_PENDING','FAILED') AND (? IS NULL OR o.status=?) ORDER BY o.id DESC LIMIT 200", access.shopId(u), status, status); }

  public Map<String,Object> ownerOrder(AuthUser u, Long id, String code){
    var o = db.one("SELECT o.id, o.order_code, o.status, o.payment_status, o.subtotal, o.discount_amount, o.platform_fee, (o.total_amount - o.platform_fee) shop_earnings, o.total_amount, o.advance_amount, o.remaining_amount, o.est_prep_min, o.est_prep_max, o.created_at, SUBSTRING_INDEX(us.full_name,' ',1) student_name FROM orders o JOIN users us ON us.id=o.student_id WHERE o.shop_id=? AND (o.id=? OR o.order_code=?)", access.shopId(u), id, code == null ? null : code.toUpperCase())
      .orElseThrow(() -> BusinessException.notFound("Order not found."));
    Map<String,Object> m = new LinkedHashMap<>(o); detail(m, Db.l(o.get("id")), false);
    m.put("remainingPaid", db.count("SELECT COUNT(*) FROM payments WHERE order_id=? AND payment_type='REMAINING' AND status='SUCCESS'", o.get("id")) > 0);
    return m;
  }

  @Transactional
  public void setStatus(AuthUser u, long orderId, OrderStatus target, String remarks){
    long shop = access.shopId(u);
    var o = db.one("SELECT o.order_code, o.student_id, o.remaining_amount, s.name shop_name, s.vertical FROM orders o JOIN shops s ON s.id=o.shop_id WHERE o.id=? AND o.shop_id=? FOR UPDATE", orderId, shop).orElseThrow(() -> BusinessException.notFound("Order not found."));
    if (!OWNER_TARGETS.contains(target)) throw BusinessException.badRequest("BAD_STATUS", "You can't set that status directly.");
    if ("FOOD".equals(o.get("vertical")) && (target == PRINTING || target == BINDING)) throw BusinessException.conflict("INVALID_ORDER_STATE", "Printing and binding steps are only for CampusDesk orders.");
    tr.move(orderId, target, u.id(), remarks);
    long student = Db.l(o.get("student_id")); String code = (String) o.get("order_code");
    switch (target) {
      case ACCEPTED -> notes.notify(student, orderId, "ORDER", "Order accepted", "Order " + code + " was accepted by " + o.get("shop_name") + ".");
      case PROCESSING -> notes.notify(student, orderId, "ORDER", "Preparing your order", "Order " + code + " is being prepared.");
      case PRINTING -> notes.notify(student, orderId, "ORDER", "Printing started", "Your documents for order " + code + " are printing.");
      case BINDING -> notes.notify(student, orderId, "ORDER", "Binding started", "Order " + code + " is being bound.");
      case READY -> {
        db.update("UPDATE orders SET ready_at=NOW(3) WHERE id=?", orderId);
        String pin = otp.generate(orderId);                              // OTP exists only from this moment
        BigDecimal rem = Db.bd(o.get("remaining_amount"));
        notes.notify(student, orderId, "ORDER", "Your order #" + code + " is ready for pickup.", o.get("shop_name") + ". Remaining to pay: " + Fmt.rs(rem) + " (PhonePe or Google Pay). Pickup OTP: " + pin);
      }
      default -> {}
    }
    audit.log(u.id(), u.email(), "ORDER_STATUS", "order", code + " -> " + target);
  }

  /** Needs: right shop, status READY, remaining payment SUCCESS, valid / unexpired / unused OTP. Wrong guesses are counted and kept. */
  @Transactional(noRollbackFor = BusinessException.class)
  public Map<String,Object> verifyPickup(AuthUser u, long orderId, String code){
    long shop = access.shopId(u);
    var o = db.one("SELECT order_code, student_id, status FROM orders WHERE id=? AND shop_id=? FOR UPDATE", orderId, shop).orElseThrow(() -> BusinessException.notFound("Order not found."));
    if (!"READY".equals(o.get("status"))) throw BusinessException.conflict("NOT_READY", "This order isn't ready for pickup.");
    if (db.count("SELECT COUNT(*) FROM payments WHERE order_id=? AND payment_type='REMAINING' AND status='SUCCESS'", orderId) == 0) throw BusinessException.conflict("REMAINING_PAYMENT_PENDING", "The remaining payment hasn't been completed yet.");
    otp.verify(orderId, code);
    tr.move(orderId, COLLECTED, u.id(), "Handed over with OTP");
    db.update("UPDATE orders SET collected_at=NOW(3) WHERE id=?", orderId);
    db.update("UPDATE print_files f JOIN print_jobs pj ON pj.file_id=f.id SET f.delete_after=LEAST(f.delete_after, ?) WHERE pj.order_id=?", LocalDateTime.now().plusHours(24), orderId);
    notes.notify(Db.l(o.get("student_id")), orderId, "ORDER", "Order collected", "Order " + o.get("order_code") + " was collected. How was it? You can leave a review.");
    audit.log(u.id(), u.email(), "ORDER_COLLECTED", "order", String.valueOf(o.get("order_code")));
    return Map.of("orderCode", o.get("order_code"), "status", "COLLECTED");
  }

  @Transactional
  public void reject(AuthUser u, long orderId, String reason){ refunds.rejectByOwner(access.shopId(u), orderId, reason, u.id()); }
  @Transactional
  public Map<String,Object> cancel(AuthUser u, long orderId, String reason){ return refunds.cancelByStudent(u.id(), orderId, reason); }
  public Map<String,Object> pay(AuthUser u, long orderId, String method){ return payments.pay(u.id(), orderId, method); }
}
