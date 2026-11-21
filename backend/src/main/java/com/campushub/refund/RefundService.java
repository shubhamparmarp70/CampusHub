package com.campushub.refund;
import com.campushub.catalog.*;
import com.campushub.common.*;
import com.campushub.notification.NotificationService;
import com.campushub.order.*;
import com.campushub.payments.PaymentGateway;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.*;
import java.time.LocalDateTime;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import static com.campushub.order.OrderStatus.*;

@Service
public class RefundService {
  record Policy(int before, int during, int after, boolean readyAllowed) {}
  private final Db db; private final OrderTransitions tr; private final PaymentGateway gateway; private final InventoryService inventory;
  private final NotificationService notes; private final DiscountService discounts; private final ObjectMapper om; private final AuditService audit;
  public RefundService(Db db, OrderTransitions tr, PaymentGateway g, InventoryService inv, NotificationService n, DiscountService d, ObjectMapper om, AuditService a){
    this.db = db; this.tr = tr; gateway = g; inventory = inv; notes = n; discounts = d; this.om = om; audit = a; }

  private Policy policy(long shopId){
    try {
      var j = db.scalar(String.class, "SELECT cancellation_policy FROM shops WHERE id=?", shopId).orElse(null);
      if (j != null) { var m = om.readTree(j); return new Policy(m.path("beforeProcessingPercent").asInt(100), m.path("duringProcessingPercent").asInt(50), m.path("afterPrintingPercent").asInt(0), m.path("readyAllowed").asBoolean(false)); }
    } catch (Exception ignored) {}
    return new Policy(100, 50, 0, false);       // default policy until the owner sets one
  }
  private BigDecimal paid(long orderId){
    return db.scalar(BigDecimal.class, "SELECT COALESCE(SUM(amount),0) FROM payments WHERE order_id=? AND status='SUCCESS' AND payment_type IN ('ADVANCE','REMAINING')", orderId).orElse(BigDecimal.ZERO); }

  /** Student cancellation, using the shop's own policy for the current stage. */
  @Transactional
  public Map<String,Object> cancelByStudent(long studentId, long orderId, String reason){
    var o = db.one("SELECT order_code, status, shop_id FROM orders WHERE id=? AND student_id=? FOR UPDATE", orderId, studentId).orElseThrow(() -> BusinessException.notFound("Order not found."));
    OrderStatus st = OrderStatus.valueOf((String) o.get("status"));
    if (st == PAYMENT_PENDING) {      // nothing was charged
      tr.move(orderId, CANCELLED, studentId, reason == null ? "Cancelled by student" : reason);
      db.update("UPDATE payments SET status='FAILED' WHERE order_id=? AND status IN ('INITIATED','PENDING')", orderId);
      discounts.release(orderId);
      return Map.of("refundAmount", BigDecimal.ZERO, "message", "Order cancelled. You weren't charged.");
    }
    Policy p = policy(Db.l(o.get("shop_id")));
    int pct = switch (st) {
      case PLACED, ACCEPTED, QUEUED -> p.before();
      case PROCESSING -> p.during();
      case PRINTING, BINDING -> p.after();
      case READY -> { if (!p.readyAllowed()) throw BusinessException.conflict("READY_NOT_CANCELLABLE", "This order is ready. Please visit the shop to collect it or ask them about cancelling."); yield p.after(); }
      default -> throw BusinessException.conflict("CANNOT_CANCEL", "This order can't be cancelled.");
    };
    if (pct <= 0) throw BusinessException.conflict("NOT_REFUNDABLE", "This order has gone too far to cancel for a refund. Please talk to the shop.");
    BigDecimal amount = paid(orderId).multiply(BigDecimal.valueOf(pct)).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
    if (amount.signum() <= 0) throw BusinessException.conflict("NOT_REFUNDABLE", "There's nothing to refund.");
    startRefund(orderId, amount, reason == null ? "Cancelled by student" : reason, studentId);
    return Map.of("refundAmount", amount, "refundPercent", pct, "message", "Cancelled. " + Fmt.rs(amount) + " is on its way back to you.");
  }

  /** Shop declines an order after payment: always a full refund of everything the student paid. */
  @Transactional
  public void rejectByOwner(long shopId, long orderId, String reason, long ownerId){
    var o = db.one("SELECT order_code, student_id, status FROM orders WHERE id=? AND shop_id=? FOR UPDATE", orderId, shopId).orElseThrow(() -> BusinessException.notFound("Order not found."));
    if (Set.of("CREATED","PAYMENT_PENDING","FAILED","CANCELLED","COLLECTED","REFUND_PENDING","REFUNDED").contains(o.get("status"))) throw BusinessException.conflict("CANNOT_REJECT", "This order can't be rejected now.");
    notes.notify(Db.l(o.get("student_id")), orderId, "ORDER", "Order rejected", "The shop couldn't fulfil order " + o.get("order_code") + ": " + reason);
    startRefund(orderId, paid(orderId), "Rejected by shop: " + reason, ownerId);
  }

  @Transactional
  public void startRefund(long orderId, BigDecimal amount, String reason, Long by){
    var o = db.one("SELECT order_code, student_id, shop_id FROM orders WHERE id=?", orderId).orElseThrow();
    long payId = db.scalar(Long.class, "SELECT id FROM payments WHERE order_id=? AND payment_type='ADVANCE' AND status='SUCCESS' ORDER BY id LIMIT 1", orderId).orElseThrow(() -> BusinessException.conflict("NO_PAYMENT", "There's no payment to refund."));
    tr.move(orderId, REFUND_PENDING, by, reason);
    db.update("UPDATE orders SET cancel_reason=? WHERE id=?", reason, orderId);
    long refundId = db.insert("INSERT INTO refunds(order_id, payment_id, amount, reason, status) VALUES (?,?,?,?,'REQUESTED')", orderId, payId, amount, reason);
    String code = (String) o.get("order_code");
    notes.notifyShopOwner(Db.l(o.get("shop_id")), orderId, "ORDER", "Order " + code + " cancelled", reason);
    notes.notifyShopOwner(Db.l(o.get("shop_id")), orderId, "PAYMENT", "Refund issued", Fmt.rs(amount) + " for order " + code + ".");
    notes.notify(Db.l(o.get("student_id")), orderId, "PAYMENT", "Refund started", Fmt.rs(amount) + " for order " + code + " is being refunded.");
    audit.log(by, null, "REFUND_STARTED", "refund", String.valueOf(refundId));
    process(refundId);
  }

  /** Talks to the gateway. A PENDING answer waits for the refund webhook; a FAILED one stays visible to admins for retry. */
  @Transactional
  public void process(long refundId){
    var r = db.one("SELECT r.order_id, r.amount, r.status, p.gateway_reference, p.payment_method FROM refunds r JOIN payments p ON p.id=r.payment_id WHERE r.id=? FOR UPDATE", refundId).orElseThrow(() -> BusinessException.notFound("Refund not found."));
    if ("COMPLETED".equals(r.get("status"))) return;
    long orderId = Db.l(r.get("order_id")); BigDecimal amount = Db.bd(r.get("amount"));
    db.update("UPDATE payments SET status='FAILED' WHERE order_id=? AND payment_type='REFUND' AND status IN ('INITIATED','PENDING')", orderId);
    String txn = "RF" + UUID.randomUUID().toString().replace("-", "").toUpperCase();
    long pid = db.insert("INSERT INTO payments(order_id, payment_type, amount, payment_method, transaction_id, status) VALUES (?,'REFUND',?,?,?,'INITIATED')", orderId, amount, r.get("payment_method"), txn);
    var res = gateway.refund((String) r.get("gateway_reference"), txn, amount);
    switch (res.status()) {
      case "SUCCESS" -> { db.update("UPDATE payments SET status='SUCCESS', gateway_reference=?, completed_at=NOW(3) WHERE id=?", res.reference(), pid); complete(refundId, orderId, amount); }
      case "PENDING" -> { db.update("UPDATE payments SET status='PENDING', gateway_reference=? WHERE id=?", res.reference(), pid); db.update("UPDATE refunds SET status='APPROVED' WHERE id=?", refundId); }
      default -> { db.update("UPDATE payments SET status='FAILED' WHERE id=?", pid); notes.notifyAdmins("Refund failed", "Refund " + refundId + " for order " + orderId + " didn't go through. Retry it from the admin refunds list."); }
    }
  }
  @Transactional
  public void completeByPaymentTxn(String txn){
    var p = db.one("SELECT order_id FROM payments WHERE transaction_id=? AND payment_type='REFUND'", txn).orElseThrow();
    long orderId = Db.l(p.get("order_id"));
    db.update("UPDATE payments SET status='SUCCESS', completed_at=NOW(3) WHERE transaction_id=?", txn);
    db.one("SELECT id, amount FROM refunds WHERE order_id=? AND status IN ('REQUESTED','APPROVED') ORDER BY id DESC LIMIT 1", orderId).ifPresent(r -> complete(Db.l(r.get("id")), orderId, Db.bd(r.get("amount"))));
  }
  private void complete(long refundId, long orderId, BigDecimal amount){
    db.update("UPDATE refunds SET status='COMPLETED', completed_at=NOW(3) WHERE id=?", refundId);
    var o = db.one("SELECT order_code, student_id, status, stock_deducted FROM orders WHERE id=?", orderId).orElseThrow();
    if ("REFUND_PENDING".equals(o.get("status"))) tr.move(orderId, REFUNDED, null, "Refund completed");
    db.update("UPDATE orders SET payment_status='REFUNDED' WHERE id=?", orderId);
    if (Db.bool(o.get("stock_deducted"))) inventory.restockForOrder(orderId);
    db.update("UPDATE print_files f JOIN print_jobs pj ON pj.file_id=f.id SET f.delete_after=LEAST(f.delete_after, ?) WHERE pj.order_id=?", LocalDateTime.now().plusHours(24), orderId);
    notes.notify(Db.l(o.get("student_id")), orderId, "PAYMENT", "Refund successful", Fmt.rs(amount) + " refunded for order " + o.get("order_code") + ".");
    audit.log(null, null, "REFUND_COMPLETED", "refund", String.valueOf(refundId));
  }
  @Transactional public void retry(long refundId){ process(refundId); }
}
