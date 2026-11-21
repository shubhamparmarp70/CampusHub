package com.campushub.payments;
import com.campushub.catalog.InventoryService;
import com.campushub.common.*;
import com.campushub.notification.NotificationService;
import com.campushub.order.*;
import com.campushub.refund.RefundService;
import java.math.BigDecimal;
import java.util.*;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import static com.campushub.order.OrderStatus.*;

@Service
public class PaymentService {
  private final Db db; private final PaymentGateway gateway; private final OrderTransitions tr; private final InventoryService inventory;
  private final NotificationService notes; private final AuditService audit; private final RefundService refunds;
  public PaymentService(Db db, PaymentGateway g, OrderTransitions tr, InventoryService inv, NotificationService n, AuditService a, RefundService r){ this.db = db; gateway = g; this.tr = tr; inventory = inv; notes = n; audit = a; refunds = r; }

  @Transactional
  public Map<String,Object> start(long orderId, String type, BigDecimal amount, PaymentMethod m){
    String txn = "TXN" + UUID.randomUUID().toString().replace("-", "").toUpperCase();
    long id = db.insert("INSERT INTO payments(order_id, payment_type, amount, payment_method, transaction_id, status) VALUES (?,?,?,?,?,'INITIATED')", orderId, type, amount, m.name(), txn);
    var link = gateway.createPayment(txn, amount, m, orderId);
    db.update("UPDATE payments SET status='PENDING', gateway_reference=? WHERE id=?", link.reference(), id);
    return Map.of("paymentId", id, "transactionId", txn, "type", type, "amount", amount, "method", m.name(), "paymentUrl", link.url());
  }

  /** One endpoint for both payments: the order's status decides which one is due (advance while PAYMENT_PENDING, remaining while READY). */
  @Transactional
  public Map<String,Object> pay(long studentId, long orderId, String methodStr){
    PaymentMethod m = PaymentMethod.of(methodStr);
    var o = db.one("SELECT id, status, advance_amount, remaining_amount FROM orders WHERE id=? AND student_id=? FOR UPDATE", orderId, studentId).orElseThrow(() -> BusinessException.notFound("Order not found."));
    String type; BigDecimal amount;
    switch ((String) o.get("status")) {
      case "PAYMENT_PENDING" -> { type = "ADVANCE"; amount = Db.bd(o.get("advance_amount")); }
      case "READY" -> {
        if (db.count("SELECT COUNT(*) FROM payments WHERE order_id=? AND payment_type='REMAINING' AND status='SUCCESS'", orderId) > 0) throw BusinessException.conflict("ALREADY_PAID", "The remaining amount is already paid.");
        type = "REMAINING"; amount = Db.bd(o.get("remaining_amount"));
        if (amount.signum() <= 0) throw BusinessException.conflict("NOTHING_TO_PAY", "There's nothing left to pay.");
      }
      default -> throw BusinessException.conflict("NOTHING_TO_PAY", "There's nothing to pay for this order right now.");
    }
    var ex = db.one("SELECT id, transaction_id FROM payments WHERE order_id=? AND payment_type=? AND status IN ('INITIATED','PENDING') ORDER BY id DESC LIMIT 1", orderId, type).orElse(null);
    if (ex == null) return start(orderId, type, amount, m);
    String txn = (String) ex.get("transaction_id");
    db.update("UPDATE payments SET payment_method=? WHERE id=?", m.name(), ex.get("id"));
    return Map.of("paymentId", ex.get("id"), "transactionId", txn, "type", type, "amount", amount, "method", m.name(), "paymentUrl", gateway.createPayment(txn, amount, m, orderId).url());
  }

  /** Idempotent: the event id is stored first (unique), the payment row is locked, and a payment already SUCCESS is never processed twice. */
  @Transactional
  public void handleWebhook(String provider, WebhookEvent e, String raw){
    try { db.update("INSERT INTO payment_webhook_events(event_id, provider, payload_hash) VALUES (?,?,?)", e.eventId(), provider, Tokens.sha256(raw)); }
    catch (DuplicateKeyException dup) { return; }                       // replay
    var p = db.one("SELECT id, order_id, payment_type, amount, status FROM payments WHERE transaction_id=? FOR UPDATE", e.transactionId()).orElse(null);
    if (p == null) { audit.log(null, null, "WEBHOOK_UNKNOWN_TXN", "payment", e.transactionId()); return; }
    long paymentId = Db.l(p.get("id")), orderId = Db.l(p.get("order_id")); String type = (String) p.get("payment_type"), status = (String) p.get("status");
    boolean ok = "SUCCESS".equalsIgnoreCase(e.status());
    if ("REFUND".equals(type)) {
      if (ok) refunds.completeByPaymentTxn(e.transactionId());
      else if (!"SUCCESS".equals(status)) db.update("UPDATE payments SET status='FAILED' WHERE id=?", paymentId);
      return;
    }
    var o = db.one("SELECT order_code, student_id, shop_id FROM orders WHERE id=?", orderId).orElseThrow();
    long student = Db.l(o.get("student_id")); String code = (String) o.get("order_code");
    if (!ok) {
      if (!"SUCCESS".equals(status)) { db.update("UPDATE payments SET status='FAILED' WHERE id=?", paymentId); notes.notify(student, orderId, "PAYMENT", "Payment failed", "Your payment for order " + code + " didn't go through. You can try again."); }
      return;
    }
    if ("SUCCESS".equals(status)) return;                               // already processed
    BigDecimal amount = Db.bd(p.get("amount"));
    if (e.amount() != null && e.amount().compareTo(amount) != 0) {      // never trust a mismatched amount
      audit.log(null, null, "PAYMENT_AMOUNT_MISMATCH", "payment", e.transactionId()); notes.notifyAdmins("Payment amount mismatch", "Transaction " + e.transactionId() + " reported " + e.amount() + " but expected " + amount + "."); return; }
    db.update("UPDATE payments SET status='SUCCESS', gateway_reference=COALESCE(?, gateway_reference), completed_at=NOW(3) WHERE id=?", e.gatewayReference(), paymentId);
    audit.log(null, null, "PAYMENT_SUCCESS", "payment", e.transactionId());
    if ("ADVANCE".equals(type)) onAdvancePaid(orderId, code, student, Db.l(o.get("shop_id")), amount); else onRemainingPaid(orderId, code, student, Db.l(o.get("shop_id")), amount);
  }

  private void onAdvancePaid(long orderId, String code, long student, long shop, BigDecimal amount){
    OrderStatus st = OrderStatus.valueOf(db.scalar(String.class, "SELECT status FROM orders WHERE id=? FOR UPDATE", orderId).orElseThrow());
    if (st != PAYMENT_PENDING) {          // order expired/cancelled before the money arrived: needs a human look + refund
      audit.log(null, null, "LATE_PAYMENT", "order", String.valueOf(orderId));
      notes.notifyAdmins("Payment received for a closed order", "Order " + code + " was " + st + " when its advance payment arrived. Refund it from the admin refunds list.");
      return; }
    tr.move(orderId, PARTIALLY_PAID, null, "Advance payment verified");
    db.update("UPDATE orders SET payment_status='PARTIALLY_PAID' WHERE id=?", orderId);
    if (!inventory.deductForOrder(orderId)) {
      notes.notify(student, orderId, "ORDER", "We couldn't place your order", "An item sold out just before your payment cleared. Your advance is being refunded.");
      refunds.startRefund(orderId, amount, "Item went out of stock", null); return; }
    db.update("UPDATE orders SET stock_deducted=TRUE WHERE id=?", orderId);
    tr.move(orderId, PLACED, null, "Order placed");
    notes.notify(student, orderId, "PAYMENT", "Advance payment successful", Fmt.rs(amount) + " received for order " + code + ".");
    notes.notify(student, orderId, "ORDER", "Order placed", "Order " + code + " is with the shop. You'll be notified when it's ready.");
    notes.notifyShopOwner(shop, orderId, "ORDER", "New order " + code, "A new order is waiting to be accepted.");
    notes.notifyShopOwner(shop, orderId, "PAYMENT", "Advance payment received", Fmt.rs(amount) + " for order " + code + ".");
  }
  private void onRemainingPaid(long orderId, String code, long student, long shop, BigDecimal amount){
    db.update("UPDATE orders SET payment_status='PAID' WHERE id=?", orderId);
    notes.notify(student, orderId, "PAYMENT", "Remaining payment successful", "Thanks! Show your pickup OTP at the counter to collect order " + code + ".");
    notes.notifyShopOwner(shop, orderId, "PAYMENT", "Remaining payment received", Fmt.rs(amount) + " for order " + code + ". It can now be handed over.");
  }

  public List<Map<String,Object>> forStudent(long studentId){
    return db.rows("SELECT p.id, o.order_code, p.payment_type, p.amount, p.payment_method, p.status, p.created_at, p.completed_at FROM payments p JOIN orders o ON o.id=p.order_id WHERE o.student_id=? ORDER BY p.id DESC LIMIT 100", studentId); }
  public List<Map<String,Object>> forShop(long shopId){
    return db.rows("SELECT p.id, o.order_code, p.payment_type, p.amount, p.payment_method, p.status, p.completed_at FROM payments p JOIN orders o ON o.id=p.order_id WHERE o.shop_id=? ORDER BY p.id DESC LIMIT 200", shopId); }
}
