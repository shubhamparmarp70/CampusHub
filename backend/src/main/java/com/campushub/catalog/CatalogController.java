package com.campushub.catalog;
import com.campushub.common.*;
import com.campushub.order.PrepTimeEstimator;
import com.campushub.pricing.PrintPricingLoader;
import java.time.LocalDateTime;
import java.util.*;
import org.springframework.web.bind.annotation.*;

/** Read-only browsing for any signed-in user. Cost prices and owner details are never exposed. */
@RestController
public class CatalogController {
  private final Db db; private final PrepTimeEstimator est; private final PrintPricingLoader loader;
  public CatalogController(Db db, PrepTimeEstimator est, PrintPricingLoader loader){ this.db = db; this.est = est; this.loader = loader; }

  @GetMapping("/api/shops") ApiResponse<List<Map<String,Object>>> shops(@RequestParam(required=false) String vertical, @RequestParam(required=false) String q){
    var rows = db.rows("SELECT id, name, vertical, description, address, logo_key, open_status, avg_rating, rating_count FROM shops WHERE approval='APPROVED' AND (? IS NULL OR vertical=?) AND (? IS NULL OR name LIKE CONCAT('%',?,'%')) ORDER BY open_status='OPEN' DESC, avg_rating DESC LIMIT 100",
      vertical, vertical, q, q);
    List<Map<String,Object>> out = new ArrayList<>();
    for (var r : rows) { var m = new LinkedHashMap<>(r); var e = est.estimate(Db.l(r.get("id")), (String) r.get("vertical"), 0, 0, false); m.put("estimatedPrepMinutes", e.min() + "–" + e.max()); out.add(m); }
    return ApiResponse.ok("OK", out);
  }
  @GetMapping("/api/shops/{id}") ApiResponse<Map<String,Object>> shop(@PathVariable long id){
    var s = db.one("SELECT id, name, vertical, description, address, phone, logo_key, opening_hours, open_status, avg_rating, rating_count FROM shops WHERE id=? AND approval='APPROVED'", id).orElseThrow(() -> BusinessException.notFound("Shop not found."));
    var m = new LinkedHashMap<>(s); var e = est.estimate(id, (String) s.get("vertical"), 0, 0, false);
    m.put("estimatedPrepMinutes", e.min() + "–" + e.max());
    m.put("offers", db.rows("SELECT id, name, description, code, type, value, min_order_amount, ends_at FROM discounts WHERE shop_id=? AND active=TRUE AND starts_at<=? AND ends_at>=? AND (max_usage IS NULL OR used_count<max_usage)", id, LocalDateTime.now(), LocalDateTime.now()));
    return ApiResponse.ok("OK", m);
  }
  @GetMapping("/api/shops/{id}/products") ApiResponse<List<Map<String,Object>>> products(@PathVariable long id, @RequestParam(required=false) Long categoryId){
    return ApiResponse.ok("OK", db.rows("SELECT p.id, p.name, p.kind, p.description, p.image_key, p.selling_price, c.id categoryId, c.name category, (p.kind='SERVICE' OR COALESCE(i.quantity,0)>0) AS in_stock FROM products p JOIN categories c ON c.id=p.category_id LEFT JOIN inventory i ON i.product_id=p.id WHERE p.shop_id=? AND p.active=TRUE AND (? IS NULL OR p.category_id=?) ORDER BY c.name, p.name", id, categoryId, categoryId)); }
  @GetMapping("/api/shops/{id}/printing-prices") ApiResponse<Object> printing(@PathVariable long id){ return ApiResponse.ok("OK", loader.load(id)); }
  @GetMapping("/api/categories") ApiResponse<List<Map<String,Object>>> categories(@RequestParam(required=false) String vertical){
    return ApiResponse.ok("OK", db.rows("SELECT id, vertical, name FROM categories WHERE active=TRUE AND (? IS NULL OR vertical=?) ORDER BY name", vertical, vertical)); }
}
