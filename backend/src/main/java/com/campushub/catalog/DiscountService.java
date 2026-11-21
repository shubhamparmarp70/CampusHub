package com.campushub.catalog;
import com.campushub.common.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.*;
import java.time.LocalDateTime;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DiscountService {
  public record Line(Long productId, boolean print, BigDecimal total) {}
  public record Applied(Long discountId, BigDecimal amount, String name) {}
  private final Db db; private final ObjectMapper om;
  public DiscountService(Db db, ObjectMapper om){ this.db = db; this.om = om; }
  private static BusinessException invalid(String m){ return new BusinessException(HttpStatus.BAD_REQUEST, "INVALID_DISCOUNT", m); }

  /** Checks every rule server-side. The result never exceeds the eligible amount, so totals can't go negative. */
  public Applied evaluate(long shopId, String code, List<Line> lines, BigDecimal subtotal){
    if (code == null || code.isBlank()) return new Applied(null, BigDecimal.ZERO, null);
    var d = db.one("SELECT * FROM discounts WHERE shop_id=? AND code=? AND active=TRUE", shopId, code.trim().toUpperCase()).orElseThrow(() -> invalid("That offer code isn't valid."));
    LocalDateTime now = LocalDateTime.now();
    if (now.isBefore(Db.ts(d.get("starts_at"))) || now.isAfter(Db.ts(d.get("ends_at")))) throw invalid("This offer isn't active right now.");
    if (d.get("max_usage") != null && Db.i(d.get("used_count")) >= Db.i(d.get("max_usage"))) throw invalid("This offer has been fully redeemed.");
    BigDecimal min = Db.bd(d.get("min_order_amount"));
    if (subtotal.compareTo(min) < 0) throw invalid("Add ₹" + min.subtract(subtotal).setScale(2, RoundingMode.UP).stripTrailingZeros().toPlainString() + " more to use this offer.");
    BigDecimal eligible = subtotal;
    if (d.get("applies_to") != null) {
      Set<String> set = new HashSet<>();
      try { for (Object o : om.readValue(d.get("applies_to").toString(), List.class)) set.add(String.valueOf(o)); } catch (Exception e) { throw invalid("This offer is misconfigured."); }
      eligible = BigDecimal.ZERO;
      for (Line l : lines) if ((l.productId() != null && set.contains(String.valueOf(l.productId()))) || (l.print() && set.contains("PRINTING"))) eligible = eligible.add(l.total());
      if (eligible.signum() == 0) throw invalid("This offer doesn't apply to the items in your cart.");
    }
    BigDecimal v = Db.bd(d.get("value")), amt = d.get("type").equals("PERCENT") ? eligible.multiply(v).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP) : v;
    return new Applied(Db.l(d.get("id")), amt.min(eligible).setScale(2, RoundingMode.HALF_UP), (String) d.get("name"));
  }
  /** Atomically takes one use. Fails if the last use was just taken by someone else. */
  @Transactional public void redeem(long orderId, long discountId, long studentId){
    if (db.update("UPDATE discounts SET used_count=used_count+1 WHERE id=? AND (max_usage IS NULL OR used_count<max_usage)", discountId) == 0) throw invalid("This offer has just been fully redeemed.");
    db.update("INSERT INTO discount_usages(discount_id, order_id, student_id) VALUES (?,?,?)", discountId, orderId, studentId);
  }
  /** Gives the use back when an unpaid order expires or is cancelled before payment. */
  @Transactional public void release(long orderId){
    db.one("SELECT discount_id FROM discount_usages WHERE order_id=?", orderId).ifPresent(r -> {
      db.update("DELETE FROM discount_usages WHERE order_id=?", orderId); db.update("UPDATE discounts SET used_count=GREATEST(used_count-1,0) WHERE id=?", r.get("discount_id")); });
  }
}
