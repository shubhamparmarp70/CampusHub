package com.campushub.notification;
import com.campushub.common.Db;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
public class NotificationService {
  private static final Map<String,String> PREF = Map.of("ORDER","order_updates","PAYMENT","payment_updates","SHOP_UPDATE","shop_updates",
    "DISCOUNT","new_discounts","NEW_PRODUCT","new_products","PROMO","promotional","LOW_PRICE","low_price_deals");
  private static final Set<String> DEFAULT_ON = Set.of("order_updates","payment_updates","shop_updates","new_discounts");
  private final Db db; private final SseHub hub;
  public NotificationService(Db db, SseHub hub){ this.db = db; this.hub = hub; }

  /** Stores and pushes a notification, unless the user switched that category off. SYSTEM and SECURITY can't be switched off. */
  public void notify(long userId, Long orderId, String category, String title, String body){
    String col = PREF.get(category);
    if (col != null && !allowed(userId, col)) return;
    long id = db.insert("INSERT INTO notifications(user_id, order_id, category, title, body) VALUES (?,?,?,?,?)", userId, orderId, category, title, body);
    Map<String,Object> payload = Map.of("id", id, "category", category, "title", title, "body", body == null ? "" : body, "orderId", orderId == null ? 0 : orderId);
    if (TransactionSynchronizationManager.isSynchronizationActive())
      TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization(){ @Override public void afterCommit(){ hub.push(userId, payload); } });
    else hub.push(userId, payload);
  }
  private boolean allowed(long userId, String col){
    var row = db.one("SELECT " + col + " AS v FROM notification_preferences WHERE user_id=?", userId);   // col comes from the fixed PREF map above
    return row.map(r -> Db.bool(r.get("v"))).orElse(DEFAULT_ON.contains(col));
  }
  public void notifyAdmins(String title, String body){
    for (var r : db.rows("SELECT ur.user_id FROM user_roles ur JOIN roles r ON r.id=ur.role_id WHERE r.name='SUPER_ADMIN'")) notify(Db.l(r.get("user_id")), null, "SYSTEM", title, body);
  }
  public void notifyShopOwner(long shopId, Long orderId, String category, String title, String body){
    db.scalar(Long.class, "SELECT owner_id FROM shops WHERE id=?", shopId).ifPresent(o -> notify(o, orderId, category, title, body));
  }
}
