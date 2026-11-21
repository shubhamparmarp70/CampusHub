package com.campushub.refund;
import com.campushub.catalog.ShopAccess;
import com.campushub.common.*;
import com.campushub.security.AuthUser;
import java.util.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
@RestController
public class RefundController {
  private final Db db; private final ShopAccess access;
  public RefundController(Db db, ShopAccess a){ this.db = db; access = a; }
  @GetMapping("/api/student/refunds") ApiResponse<List<Map<String,Object>>> student(@AuthenticationPrincipal AuthUser u){
    return ApiResponse.ok("OK", db.rows("SELECT r.id, o.order_code, r.amount, r.reason, r.status, r.created_at, r.completed_at FROM refunds r JOIN orders o ON o.id=r.order_id WHERE o.student_id=? ORDER BY r.id DESC LIMIT 100", u.id())); }
  @GetMapping("/api/shop-owner/refunds") ApiResponse<List<Map<String,Object>>> owner(@AuthenticationPrincipal AuthUser u){
    return ApiResponse.ok("OK", db.rows("SELECT r.id, o.order_code, r.amount, r.reason, r.status, r.created_at, r.completed_at FROM refunds r JOIN orders o ON o.id=r.order_id WHERE o.shop_id=? ORDER BY r.id DESC LIMIT 100", access.shopId(u))); }
}
