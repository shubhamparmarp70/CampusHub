package com.campushub.catalog;
import com.campushub.common.*;
import com.campushub.notification.NotificationService;
import java.util.*;
import java.util.function.IntUnaryOperator;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InventoryService {
  private final Db db; private final NotificationService notes;
  public InventoryService(Db db, NotificationService n){ this.db = db; notes = n; }

  /** Every stock change goes through here: row lock, no negative stock, a transaction row, low-stock and back-in-stock alerts. */
  @Transactional
  public int apply(long productId, String type, IntUnaryOperator f, Long orderItemId, String note, Long userId){
    if (orderItemId != null && db.count("SELECT COUNT(*) FROM inventory_transactions WHERE order_item_id=? AND txn_type=?", orderItemId, type) > 0) return -1;   // replay: already applied
    var row = db.one("SELECT i.quantity, i.min_level, p.name, p.shop_id FROM inventory i JOIN products p ON p.id=i.product_id WHERE i.product_id=? FOR UPDATE", productId)
      .orElseThrow(() -> BusinessException.notFound("This product doesn't track stock."));
    int old = Db.i(row.get("quantity")), min = Db.i(row.get("min_level")), now = f.applyAsInt(old);
    if (now < 0) throw new BusinessException(HttpStatus.CONFLICT, "INSUFFICIENT_STOCK", "Not enough stock of " + row.get("name") + ".");
    db.update("UPDATE inventory SET quantity=?, version=version+1 WHERE product_id=?", now, productId);
    db.insert("INSERT INTO inventory_transactions(product_id, order_item_id, txn_type, quantity_change, quantity_after, note, created_by) VALUES (?,?,?,?,?,?,?)", productId, orderItemId, type, now - old, now, note, userId);
    long shopId = Db.l(row.get("shop_id"));
    if (now <= min && old > min) notes.notifyShopOwner(shopId, null, "SYSTEM", "Low stock: " + row.get("name"), now + " left (minimum " + min + ").");
    if (old <= 0 && now > 0) for (var f2 : db.rows("SELECT student_id FROM shop_followers WHERE shop_id=? LIMIT 2000", shopId))
      notes.notify(Db.l(f2.get("student_id")), null, "SHOP_UPDATE", "Back in stock", row.get("name") + " is available again.");
    return now;
  }

  /** All-or-nothing sale deduction for an order. Returns false (and changes nothing) if any line is short. Safe to call twice. */
  @Transactional
  public boolean deductForOrder(long orderId){
    var lines = db.rows("SELECT oi.id item_id, oi.product_id, oi.quantity, i.quantity stock FROM order_items oi JOIN inventory i ON i.product_id=oi.product_id WHERE oi.order_id=? AND oi.product_id IS NOT NULL ORDER BY oi.product_id FOR UPDATE", orderId);
    for (var l : lines) if (Db.i(l.get("stock")) < Db.i(l.get("quantity")) && db.count("SELECT COUNT(*) FROM inventory_transactions WHERE order_item_id=? AND txn_type='SALE'", l.get("item_id")) == 0) return false;
    for (var l : lines) { int q = Db.i(l.get("quantity")); apply(Db.l(l.get("product_id")), "SALE", old -> old - q, Db.l(l.get("item_id")), "Order sale", null); }
    return true;
  }
  /** Puts sold stock back after a cancellation/refund. Only lines that were actually deducted are restored. */
  @Transactional
  public void restockForOrder(long orderId){
    for (var l : db.rows("SELECT oi.id item_id, oi.product_id, oi.quantity FROM order_items oi JOIN inventory_transactions t ON t.order_item_id=oi.id AND t.txn_type='SALE' WHERE oi.order_id=?", orderId)) {
      int q = Db.i(l.get("quantity")); apply(Db.l(l.get("product_id")), "RESTOCK_CANCEL", old -> old + q, Db.l(l.get("item_id")), "Order cancelled", null);
    }
  }
}
