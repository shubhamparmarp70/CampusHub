package com.campushub.admin;
import com.campushub.common.*;
import com.campushub.notification.NotificationService;
import com.campushub.refund.RefundService;
import com.campushub.review.Ratings;
import com.campushub.security.AuthUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.LocalDate;
import java.util.*;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

/** Platform oversight only. There is deliberately no endpoint here that runs a shop's orders, stock, prices or payments for its owner. */
@RestController @RequestMapping("/api/admin")
public class AdminController {
  public record UserStatus(@NotBlank @Pattern(regexp="ACTIVE|SUSPENDED") String status) {}
  public record ShopApproval(@NotBlank @Pattern(regexp="APPROVED|DISABLED") String approval) {}
  public record CategoryReq(@NotBlank @Pattern(regexp="FOOD|DESK") String vertical, @NotBlank @Size(max=80) String name) {}
  public record ActiveReq(boolean active) {}
  public record SettingReq(@NotBlank String value) {}
  private static final Map<String,int[]> SETTINGS = Map.of("otp_ttl_minutes", new int[]{5, 60}, "payment_timeout_minutes", new int[]{10, 120}, "file_retention_days", new int[]{1, 30});
  private final Db db; private final AuditService audit; private final NotificationService notes; private final RefundService refunds; private final Ratings ratings;
  public AdminController(Db db, AuditService a, NotificationService n, RefundService r, Ratings ra){ this.db = db; audit = a; notes = n; refunds = r; ratings = ra; }

  // ---- users ----
  @GetMapping("/users") ApiResponse<List<Map<String,Object>>> users(@RequestParam(required=false) String role, @RequestParam(required=false) String status, @RequestParam(required=false) String q){
    return ApiResponse.ok("OK", db.rows("SELECT u.id, u.email, u.full_name, u.status, u.created_at, GROUP_CONCAT(r.name) roles FROM users u JOIN user_roles ur ON ur.user_id=u.id JOIN roles r ON r.id=ur.role_id WHERE (? IS NULL OR r.name=?) AND (? IS NULL OR u.status=?) AND (? IS NULL OR u.email LIKE CONCAT('%',?,'%')) GROUP BY u.id ORDER BY u.id DESC LIMIT 100", role, role, status, status, q, q)); }
  @Transactional @PatchMapping("/users/{id}/status") ApiResponse<Void> userStatus(@AuthenticationPrincipal AuthUser a, @PathVariable long id, @Valid @RequestBody UserStatus r){
    if (id == a.id()) throw BusinessException.conflict("SELF", "You can't change your own account here.");
    if (db.count("SELECT COUNT(*) FROM user_roles ur JOIN roles r ON r.id=ur.role_id WHERE ur.user_id=? AND r.name='SUPER_ADMIN'", id) > 0) throw BusinessException.conflict("ADMIN_PROTECTED", "Admin accounts can't be suspended here.");
    if (db.update("UPDATE users SET status=? WHERE id=?", r.status(), id) == 0) throw BusinessException.notFound("User not found.");
    if (r.status().equals("SUSPENDED")) db.update("UPDATE refresh_tokens SET revoked=TRUE WHERE user_id=?", id);    // kicks them out at next refresh
    audit.log(a.id(), a.email(), "USER_" + r.status(), "user", String.valueOf(id)); return ApiResponse.ok("User " + r.status().toLowerCase() + ".", null); }

  // ---- shops ----
  @GetMapping("/shops") ApiResponse<List<Map<String,Object>>> shops(@RequestParam(required=false) String approval){
    return ApiResponse.ok("OK", db.rows("SELECT s.id, s.name, s.vertical, s.approval, s.open_status, s.avg_rating, s.created_at, u.email owner_email, (SELECT COUNT(*) FROM orders o WHERE o.shop_id=s.id) orders FROM shops s JOIN users u ON u.id=s.owner_id WHERE (? IS NULL OR s.approval=?) ORDER BY s.id DESC LIMIT 100", approval, approval)); }
  @Transactional @PatchMapping("/shops/{id}/approval") ApiResponse<Void> approval(@AuthenticationPrincipal AuthUser a, @PathVariable long id, @Valid @RequestBody ShopApproval r){
    var s = db.one("SELECT owner_id, name FROM shops WHERE id=?", id).orElseThrow(() -> BusinessException.notFound("Shop not found."));
    db.update("UPDATE shops SET approval=?, open_status=IF(?='DISABLED','CLOSED',open_status) WHERE id=?", r.approval(), r.approval(), id);
    if (r.approval().equals("APPROVED")) db.update("UPDATE users SET status='ACTIVE' WHERE id=? AND status='PENDING'", s.get("owner_id"));
    notes.notify(Db.l(s.get("owner_id")), null, "SYSTEM", r.approval().equals("APPROVED") ? "Your shop is verified" : "Your shop was disabled", r.approval().equals("APPROVED") ? "" + s.get("name") + " is verified. You can now add products, set prices and open your shop." : "Contact support for details.");
    audit.log(a.id(), a.email(), "SHOP_" + r.approval(), "shop", String.valueOf(id)); return ApiResponse.ok("Shop " + r.approval().toLowerCase() + ".", null); }

  // ---- orders / payments / refunds monitoring ----
  @GetMapping("/orders") ApiResponse<List<Map<String,Object>>> orders(@RequestParam(required=false) String status, @RequestParam(required=false) Long shopId){
    return ApiResponse.ok("OK", db.rows("SELECT o.id, o.order_code, o.status, o.payment_status, o.total_amount, o.created_at, s.name shop_name FROM orders o JOIN shops s ON s.id=o.shop_id WHERE (? IS NULL OR o.status=?) AND (? IS NULL OR o.shop_id=?) ORDER BY o.id DESC LIMIT 100", status, status, shopId, shopId)); }
  @GetMapping("/orders/problems") ApiResponse<List<Map<String,Object>>> problems(){
    return ApiResponse.ok("OK", db.rows("SELECT o.id, o.order_code, o.status, o.total_amount, o.cancel_reason, o.updated_at, s.name shop_name FROM orders o JOIN shops s ON s.id=o.shop_id WHERE o.status IN ('FAILED','REFUND_PENDING') ORDER BY o.updated_at DESC LIMIT 100")); }
  @GetMapping("/payments") ApiResponse<List<Map<String,Object>>> payments(@RequestParam(required=false) String status, @RequestParam(required=false) Integer stuckMinutes){
    return ApiResponse.ok("OK", db.rows("SELECT p.id, o.order_code, p.payment_type, p.amount, p.payment_method, p.transaction_id, p.status, p.created_at, p.completed_at FROM payments p JOIN orders o ON o.id=p.order_id WHERE (? IS NULL OR p.status=?) AND (? IS NULL OR (p.status IN ('INITIATED','PENDING') AND p.created_at < NOW(3) - INTERVAL ? MINUTE)) ORDER BY p.id DESC LIMIT 100", status, status, stuckMinutes, stuckMinutes)); }
  @GetMapping("/refunds") ApiResponse<List<Map<String,Object>>> refundList(@RequestParam(required=false) String status){
    return ApiResponse.ok("OK", db.rows("SELECT r.id, o.order_code, r.amount, r.reason, r.status, r.created_at, r.completed_at FROM refunds r JOIN orders o ON o.id=r.order_id WHERE (? IS NULL OR r.status=?) ORDER BY r.id DESC LIMIT 100", status, status)); }
  @PostMapping("/refunds/{id}/retry") ApiResponse<Void> retry(@AuthenticationPrincipal AuthUser a, @PathVariable long id){ refunds.retry(id); audit.log(a.id(), a.email(), "REFUND_RETRY", "refund", String.valueOf(id)); return ApiResponse.ok("Retried.", null); }

  // ---- categories ----
  @PostMapping("/categories") ResponseEntity<ApiResponse<Long>> addCategory(@AuthenticationPrincipal AuthUser a, @Valid @RequestBody CategoryReq r){
    long id = db.insert("INSERT INTO categories(vertical, name) VALUES (?,?)", r.vertical(), r.name().trim()); audit.log(a.id(), a.email(), "CATEGORY_CREATED", "category", String.valueOf(id));
    return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok("Category added.", id)); }
  @PutMapping("/categories/{id}") ApiResponse<Void> renameCategory(@PathVariable long id, @Valid @RequestBody CategoryReq r){
    if (db.update("UPDATE categories SET name=?, vertical=? WHERE id=?", r.name().trim(), r.vertical(), id) == 0) throw BusinessException.notFound("Category not found."); return ApiResponse.ok("Category updated.", null); }
  @PatchMapping("/categories/{id}/active") ApiResponse<Void> categoryActive(@PathVariable long id, @RequestBody ActiveReq r){ db.update("UPDATE categories SET active=? WHERE id=?", r.active(), id); return ApiResponse.ok("Category updated.", null); }

  // ---- settings (the 50% advance rule is intentionally not editable here) ----
  @GetMapping("/settings") ApiResponse<List<Map<String,Object>>> settings(){ return ApiResponse.ok("OK", db.rows("SELECT setting_key, setting_value, updated_at FROM system_settings ORDER BY setting_key")); }
  @PutMapping("/settings/{key}") ApiResponse<Void> setting(@AuthenticationPrincipal AuthUser a, @PathVariable String key, @Valid @RequestBody SettingReq r){
    if (key.startsWith("platform_fee_")) return decimalSetting(a, key, r.value());
    int[] range = SETTINGS.get(key); if (range == null) throw BusinessException.notFound("Unknown setting.");
    int v; try { v = Integer.parseInt(r.value().trim()); } catch (NumberFormatException e) { throw BusinessException.badRequest("BAD_VALUE", "Enter a whole number."); }
    if (v < range[0] || v > range[1]) throw BusinessException.badRequest("BAD_VALUE", "Allowed range: " + range[0] + "–" + range[1] + ".");
    db.update("INSERT INTO system_settings(setting_key, setting_value, updated_by) VALUES (?,?,?) AS n ON DUPLICATE KEY UPDATE setting_value=n.setting_value, updated_by=n.updated_by", key, String.valueOf(v), a.id());
    audit.log(a.id(), a.email(), "SETTING_CHANGED", "setting", key + "=" + v); return ApiResponse.ok("Saved.", null); }

  private ApiResponse<Void> decimalSetting(AuthUser a, String key, String raw){
    Map<String,double[]> lim = Map.of("platform_fee_percent", new double[]{0, 20}, "platform_fee_min", new double[]{0, 100}, "platform_fee_max", new double[]{0, 500});
    double[] range = lim.get(key); if (range == null) throw BusinessException.notFound("Unknown setting.");
    java.math.BigDecimal v; try { v = new java.math.BigDecimal(raw.trim()); } catch (NumberFormatException e) { throw BusinessException.badRequest("BAD_VALUE", "Enter a number."); }
    if (v.scale() > 2 || v.doubleValue() < range[0] || v.doubleValue() > range[1]) throw BusinessException.badRequest("BAD_VALUE", "Allowed range: " + range[0] + "–" + range[1] + ", up to 2 decimals.");
    java.math.BigDecimal min = key.equals("platform_fee_min") ? v : db.scalar(String.class, "SELECT setting_value FROM system_settings WHERE setting_key='platform_fee_min'").map(java.math.BigDecimal::new).orElse(java.math.BigDecimal.ZERO);
    java.math.BigDecimal max = key.equals("platform_fee_max") ? v : db.scalar(String.class, "SELECT setting_value FROM system_settings WHERE setting_key='platform_fee_max'").map(java.math.BigDecimal::new).orElse(java.math.BigDecimal.ZERO);
    if (max.signum() > 0 && max.compareTo(min) < 0) throw BusinessException.badRequest("BAD_VALUE", "The maximum fee can't be lower than the minimum (use 0 for no maximum).");
    db.update("INSERT INTO system_settings(setting_key, setting_value, updated_by) VALUES (?,?,?) AS n ON DUPLICATE KEY UPDATE setting_value=n.setting_value, updated_by=n.updated_by", key, v.toPlainString(), a.id());
    audit.log(a.id(), a.email(), "PLATFORM_FEE_CHANGED", "setting", key + "=" + v.toPlainString());
    return ApiResponse.ok("Saved. New orders use this from now on; existing orders keep the fee they were created with.", null); }

  /** Per-shop split of completed orders: gross paid, platform fees, and what each shop is owed. (Calculation only; money transfer to shops isn't automated.) */
  @GetMapping("/settlements") ApiResponse<List<Map<String,Object>>> settlements(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from, @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to){
    return ApiResponse.ok("OK", db.rows("SELECT s.id shop_id, s.name shop_name, COUNT(*) orders, SUM(o.total_amount) gross, SUM(o.platform_fee) platform_fees, SUM(o.total_amount - o.platform_fee) shop_payout FROM orders o JOIN shops s ON s.id=o.shop_id WHERE o.status='COLLECTED' AND o.collected_at >= ? AND o.collected_at < ? GROUP BY s.id, s.name ORDER BY gross DESC", from.atStartOfDay(), to.plusDays(1).atStartOfDay())); }

  // ---- audit, moderation, analytics ----
  @GetMapping("/audit-logs") ApiResponse<List<Map<String,Object>>> audit(@RequestParam(required=false) String action, @RequestParam(required=false) Long beforeId){
    return ApiResponse.ok("OK", db.rows("SELECT id, actor_id, actor_email, action, entity, entity_id, ip, created_at FROM audit_logs WHERE (? IS NULL OR action=?) AND (? IS NULL OR id<?) ORDER BY id DESC LIMIT 100", action, action, beforeId, beforeId)); }
  @Transactional @DeleteMapping("/reviews/{id}") ApiResponse<Void> hideReview(@AuthenticationPrincipal AuthUser a, @PathVariable long id){
    var r = db.one("SELECT shop_id FROM reviews WHERE id=?", id).orElseThrow(() -> BusinessException.notFound("Review not found."));
    db.update("UPDATE reviews SET hidden=TRUE WHERE id=?", id); ratings.refresh(Db.l(r.get("shop_id"))); audit.log(a.id(), a.email(), "REVIEW_HIDDEN", "review", String.valueOf(id)); return ApiResponse.ok("Review hidden.", null); }
  @GetMapping("/analytics") ApiResponse<Map<String,Object>> analytics(){
    Map<String,Object> m = new LinkedHashMap<>();
    m.put("users", db.rows("SELECT r.name role, COUNT(*) c FROM user_roles ur JOIN roles r ON r.id=ur.role_id GROUP BY r.name"));
    m.put("shops", db.rows("SELECT vertical, approval, COUNT(*) c FROM shops GROUP BY vertical, approval"));
    m.put("ordersByStatus", db.rows("SELECT status, COUNT(*) c FROM orders GROUP BY status"));
    m.put("grossPayments30d", db.scalar(java.math.BigDecimal.class, "SELECT COALESCE(SUM(CASE WHEN payment_type='REFUND' THEN -amount ELSE amount END),0) FROM payments WHERE status='SUCCESS' AND completed_at >= ?", java.time.LocalDateTime.now().minusDays(30)).orElse(java.math.BigDecimal.ZERO));
    m.put("platformFeesEarned30d", db.scalar(java.math.BigDecimal.class, "SELECT COALESCE(SUM(platform_fee),0) FROM orders WHERE status='COLLECTED' AND collected_at >= ?", java.time.LocalDateTime.now().minusDays(30)).orElse(java.math.BigDecimal.ZERO));
    m.put("platformFeesInProgress", db.scalar(java.math.BigDecimal.class, "SELECT COALESCE(SUM(platform_fee),0) FROM orders WHERE status IN ('PLACED','ACCEPTED','QUEUED','PROCESSING','PRINTING','BINDING','READY')").orElse(java.math.BigDecimal.ZERO));
    m.put("stuckPayments", db.count("SELECT COUNT(*) FROM payments WHERE status IN ('INITIATED','PENDING') AND created_at < NOW(3) - INTERVAL 60 MINUTE"));
    m.put("failedOrders7d", db.count("SELECT COUNT(*) FROM orders WHERE status='FAILED' AND updated_at >= ?", java.time.LocalDateTime.now().minusDays(7)));
    return ApiResponse.ok("OK", m); }
}
