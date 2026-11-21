package com.campushub.review;
import com.campushub.common.*;
import com.campushub.security.AuthUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.*;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
public class ReviewController {
  public record Req(@Min(1) @Max(5) int rating, @Size(max=1000) String comment) {}
  private final Db db; private final Ratings ratings;
  public ReviewController(Db db, Ratings r){ this.db = db; ratings = r; }

  /** Only the student, only after COLLECTED, one review per order (editable via PUT). */
  @Transactional @PostMapping("/api/student/orders/{orderId}/review")
  ResponseEntity<ApiResponse<Void>> create(@AuthenticationPrincipal AuthUser u, @PathVariable long orderId, @Valid @RequestBody Req r){
    var o = db.one("SELECT shop_id, status FROM orders WHERE id=? AND student_id=?", orderId, u.id()).orElseThrow(() -> BusinessException.notFound("Order not found."));
    if (!"COLLECTED".equals(o.get("status"))) throw BusinessException.conflict("NOT_COLLECTED", "You can review a shop after you've collected your order.");
    if (db.count("SELECT COUNT(*) FROM reviews WHERE order_id=?", orderId) > 0) throw BusinessException.conflict("ALREADY_REVIEWED", "You've already reviewed this order. You can edit that review.");
    db.insert("INSERT INTO reviews(order_id, shop_id, student_id, rating, comment) VALUES (?,?,?,?,?)", orderId, o.get("shop_id"), u.id(), r.rating(), r.comment());
    ratings.refresh(Db.l(o.get("shop_id")));
    return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok("Thanks for your review!", null));
  }
  @Transactional @PutMapping("/api/student/orders/{orderId}/review")
  ApiResponse<Void> edit(@AuthenticationPrincipal AuthUser u, @PathVariable long orderId, @Valid @RequestBody Req r){
    var rv = db.one("SELECT shop_id FROM reviews WHERE order_id=? AND student_id=?", orderId, u.id()).orElseThrow(() -> BusinessException.notFound("Review not found."));
    db.update("UPDATE reviews SET rating=?, comment=? WHERE order_id=? AND student_id=?", r.rating(), r.comment(), orderId, u.id());
    ratings.refresh(Db.l(rv.get("shop_id"))); return ApiResponse.ok("Review updated.", null); }
  @GetMapping("/api/student/reviews") ApiResponse<List<Map<String,Object>>> mine(@AuthenticationPrincipal AuthUser u){
    return ApiResponse.ok("OK", db.rows("SELECT r.id, r.order_id, s.name shop_name, r.rating, r.comment, r.created_at FROM reviews r JOIN shops s ON s.id=r.shop_id WHERE r.student_id=? ORDER BY r.id DESC", u.id())); }
  @GetMapping("/api/shops/{id}/reviews") ApiResponse<List<Map<String,Object>>> forShop(@PathVariable long id){
    return ApiResponse.ok("OK", db.rows("SELECT r.rating, r.comment, r.created_at, SUBSTRING_INDEX(u.full_name,' ',1) reviewer FROM reviews r JOIN users u ON u.id=r.student_id WHERE r.shop_id=? AND r.hidden=FALSE ORDER BY r.id DESC LIMIT 50", id)); }
}
