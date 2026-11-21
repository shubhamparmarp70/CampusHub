package com.campushub.notification;
import com.campushub.common.*;
import com.campushub.security.AuthUser;
import java.util.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/** Notification preferences and following shops. */
@RestController @RequestMapping("/api/student")
public class StudentSocialController {
  public record Prefs(boolean orderUpdates, boolean paymentUpdates, boolean importantShopUpdates, boolean newDiscounts, boolean newProducts, boolean promotionalOffers, boolean lowPriceDeals) {}
  private final Db db;
  public StudentSocialController(Db db){ this.db = db; }

  @GetMapping("/notification-preferences") ApiResponse<Map<String,Object>> get(@AuthenticationPrincipal AuthUser u){
    db.update("INSERT IGNORE INTO notification_preferences(user_id) VALUES (?)", u.id());
    return ApiResponse.ok("OK", db.one("SELECT order_updates orderUpdates, payment_updates paymentUpdates, shop_updates importantShopUpdates, new_discounts newDiscounts, new_products newProducts, promotional promotionalOffers, low_price_deals lowPriceDeals FROM notification_preferences WHERE user_id=?", u.id()).orElseThrow()); }
  @PutMapping("/notification-preferences") ApiResponse<Void> put(@AuthenticationPrincipal AuthUser u, @RequestBody Prefs p){
    db.update("INSERT INTO notification_preferences(user_id,order_updates,payment_updates,shop_updates,new_discounts,new_products,promotional,low_price_deals) VALUES (?,?,?,?,?,?,?,?) AS n ON DUPLICATE KEY UPDATE order_updates=n.order_updates, payment_updates=n.payment_updates, shop_updates=n.shop_updates, new_discounts=n.new_discounts, new_products=n.new_products, promotional=n.promotional, low_price_deals=n.low_price_deals",
      u.id(), p.orderUpdates(), p.paymentUpdates(), p.importantShopUpdates(), p.newDiscounts(), p.newProducts(), p.promotionalOffers(), p.lowPriceDeals());
    return ApiResponse.ok("Preferences saved.", null); }

  @PostMapping("/shops/{shopId}/follow") ApiResponse<Void> follow(@AuthenticationPrincipal AuthUser u, @PathVariable long shopId){
    if (db.count("SELECT COUNT(*) FROM shops WHERE id=? AND approval='APPROVED'", shopId) == 0) throw BusinessException.notFound("Shop not found.");
    db.update("INSERT IGNORE INTO shop_followers(shop_id, student_id) VALUES (?,?)", shopId, u.id()); return ApiResponse.ok("Following.", null); }
  @DeleteMapping("/shops/{shopId}/follow") ApiResponse<Void> unfollow(@AuthenticationPrincipal AuthUser u, @PathVariable long shopId){
    db.update("DELETE FROM shop_followers WHERE shop_id=? AND student_id=?", shopId, u.id()); return ApiResponse.ok("Unfollowed.", null); }
  @GetMapping("/following") ApiResponse<List<Map<String,Object>>> following(@AuthenticationPrincipal AuthUser u){
    return ApiResponse.ok("OK", db.rows("SELECT s.id, s.name, s.vertical, s.open_status, s.avg_rating FROM shop_followers f JOIN shops s ON s.id=f.shop_id WHERE f.student_id=? ORDER BY f.created_at DESC", u.id())); }
}
