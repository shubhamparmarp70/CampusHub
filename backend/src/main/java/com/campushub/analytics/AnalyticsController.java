package com.campushub.analytics;
import com.campushub.catalog.ShopAccess;
import com.campushub.common.*;
import com.campushub.security.AuthUser;
import java.time.*;
import java.util.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/** Aggregates only: no student names, phone numbers or documents. "Today" follows the shop's time zone (default Asia/Kolkata). */
@RestController @RequestMapping("/api/shop-owner")
public class AnalyticsController {
  private static final String LIVE = "('PLACED','ACCEPTED','QUEUED','PROCESSING','PRINTING','BINDING','READY')";
  private final Db db; private final ShopAccess access;
  public AnalyticsController(Db db, ShopAccess a){ this.db = db; access = a; }

  private static LocalDateTime dayStartUtc(ZoneId z, int daysBack){ return LocalDate.now(z).minusDays(daysBack).atStartOfDay(z).withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime(); }

  @GetMapping("/dashboard") ApiResponse<Map<String,Object>> dashboard(@AuthenticationPrincipal AuthUser u, @RequestParam(defaultValue="Asia/Kolkata") String tz){
    long shop = access.shopId(u); LocalDateTime today = dayStartUtc(ZoneId.of(tz), 0);
    Map<String,Object> m = new LinkedHashMap<>();
    Map<String,Long> counts = new LinkedHashMap<>(); for (String s : List.of("PLACED","ACCEPTED","QUEUED","PROCESSING","PRINTING","BINDING","READY")) counts.put(s, 0L);
    for (var r : db.rows("SELECT status, COUNT(*) c FROM orders WHERE shop_id=? AND status IN " + LIVE + " GROUP BY status", shop)) counts.put((String) r.get("status"), Db.l(r.get("c")));
    m.put("liveOrdersByStatus", counts);
    m.put("ordersToday", db.count("SELECT COUNT(*) FROM orders WHERE shop_id=? AND created_at>=? AND status NOT IN ('CREATED','PAYMENT_PENDING','FAILED')", shop, today));
    m.put("collectedToday", db.count("SELECT COUNT(*) FROM orders WHERE shop_id=? AND collected_at>=?", shop, today));
    m.put("revenueToday", revenue(shop, today));
    m.put("lowStock", db.rows("SELECT p.name, i.quantity, i.min_level FROM inventory i JOIN products p ON p.id=i.product_id WHERE p.shop_id=? AND i.quantity<=i.min_level ORDER BY i.quantity LIMIT 10", shop));
    m.put("activeOffers", db.count("SELECT COUNT(*) FROM discounts WHERE shop_id=? AND active=TRUE AND starts_at<=? AND ends_at>=?", shop, LocalDateTime.now(), LocalDateTime.now()));
    m.put("workload", db.count("SELECT COUNT(*) FROM orders WHERE shop_id=? AND status IN ('PLACED','ACCEPTED','QUEUED','PROCESSING','PRINTING','BINDING')", shop));
    m.put("avgPrepMinutes30d", avgPrep(shop));
    return ApiResponse.ok("OK", m);
  }
  private Object revenue(long shop, LocalDateTime since){
    return db.scalar(java.math.BigDecimal.class, "SELECT COALESCE(ROUND(SUM(CASE WHEN p.payment_type='REFUND' THEN -1 ELSE 1 END * p.amount * (o.total_amount - o.platform_fee) / o.total_amount),2),0) FROM payments p JOIN orders o ON o.id=p.order_id WHERE o.shop_id=? AND p.status='SUCCESS' AND p.completed_at>=?", shop, since).orElse(java.math.BigDecimal.ZERO); }
  private Object avgPrep(long shop){
    return db.scalar(Double.class, "SELECT ROUND(AVG(TIMESTAMPDIFF(MINUTE, created_at, ready_at))) FROM orders WHERE shop_id=? AND ready_at IS NOT NULL AND created_at>=?", shop, LocalDateTime.now().minusDays(30)).orElse(null); }

  @GetMapping("/analytics") ApiResponse<Map<String,Object>> analytics(@AuthenticationPrincipal AuthUser u, @RequestParam(defaultValue="Asia/Kolkata") String tz){
    long shop = access.shopId(u); ZoneId z = ZoneId.of(tz); LocalDateTime today = dayStartUtc(z, 0), week = dayStartUtc(z, 6), month = dayStartUtc(z, 29);
    int offsetMin = z.getRules().getOffset(Instant.now()).getTotalSeconds() / 60;
    Map<String,Object> m = new LinkedHashMap<>();
    m.put("revenue", Map.of("today", revenue(shop, today), "last7Days", revenue(shop, week), "last30Days", revenue(shop, month)));
    m.put("ordersToday", db.count("SELECT COUNT(*) FROM orders WHERE shop_id=? AND created_at>=? AND status NOT IN ('CREATED','PAYMENT_PENDING','FAILED')", shop, today));
    m.put("completedToday", db.count("SELECT COUNT(*) FROM orders WHERE shop_id=? AND collected_at>=?", shop, today));
    m.put("cancelledToday", db.count("SELECT COUNT(*) FROM orders WHERE shop_id=? AND status IN ('CANCELLED','REFUND_PENDING','REFUNDED') AND updated_at>=?", shop, today));
    m.put("bestSelling", db.rows("SELECT oi.description name, SUM(oi.quantity) quantity, SUM(oi.line_total) revenue FROM order_items oi JOIN orders o ON o.id=oi.order_id WHERE o.shop_id=? AND oi.product_id IS NOT NULL AND o.created_at>=? AND o.status IN ('PLACED','ACCEPTED','QUEUED','PROCESSING','PRINTING','BINDING','READY','COLLECTED') GROUP BY oi.product_id, oi.description ORDER BY quantity DESC LIMIT 5", shop, month));
    m.put("popularPrintTypes", db.rows("SELECT pc.page_size, pc.color_mode, pc.sides, COUNT(*) jobs, SUM(pc.billable_pages*pc.copies) pages FROM print_configurations pc JOIN print_jobs pj ON pj.id=pc.print_job_id JOIN orders o ON o.id=pj.order_id WHERE o.shop_id=? AND o.created_at>=? AND o.status IN ('PLACED','ACCEPTED','QUEUED','PROCESSING','PRINTING','BINDING','READY','COLLECTED') GROUP BY pc.page_size, pc.color_mode, pc.sides ORDER BY jobs DESC LIMIT 5", shop, month));
    m.put("lowStock", db.rows("SELECT p.name, i.quantity, i.min_level FROM inventory i JOIN products p ON p.id=i.product_id WHERE p.shop_id=? AND i.quantity<=i.min_level ORDER BY i.quantity LIMIT 20", shop));
    m.put("avgPrepMinutes30d", avgPrep(shop));
    m.put("peakHours", db.rows("SELECT HOUR(DATE_ADD(created_at, INTERVAL ? MINUTE)) hour, COUNT(*) order_count FROM orders WHERE shop_id=? AND created_at>=? AND status NOT IN ('CREATED','PAYMENT_PENDING','FAILED') GROUP BY hour ORDER BY order_count DESC LIMIT 3", offsetMin, shop, month));
    return ApiResponse.ok("OK", m);
  }
}
