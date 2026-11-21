package com.campushub.order;
import com.campushub.common.Db;
import java.time.LocalDateTime;
import org.springframework.stereotype.Component;
/** Informational only. Uses live queue length, the size of the order, and this shop's own recent preparation times. Never a promised pickup time. */
@Component
public class PrepTimeEstimator {
  public record Estimate(int min, int max) {}
  private final Db db;
  public PrepTimeEstimator(Db db){ this.db = db; }
  public Estimate estimate(long shopId, String vertical, int items, int sheets, boolean binding){
    long queue = db.count("SELECT COUNT(*) FROM orders WHERE shop_id=? AND status IN ('PLACED','ACCEPTED','QUEUED','PROCESSING','PRINTING','BINDING')", shopId);
    double lo = vertical.equals("FOOD") ? 8 + 3.0 * queue + 2.0 * items : 5 + 4.0 * queue + Math.ceil(sheets * 0.15) + (binding ? 5 : 0);
    Double avg = db.scalar(Double.class, "SELECT AVG(TIMESTAMPDIFF(MINUTE, created_at, ready_at)) FROM orders WHERE shop_id=? AND ready_at IS NOT NULL AND created_at>=?", shopId, LocalDateTime.now().minusDays(30)).orElse(null);
    if (avg != null && avg > 0) lo = (lo + avg * 0.8) / 2;      // blend model with history
    int min = (int) Math.max(5, Math.round(lo));
    return new Estimate(min, min + Math.max(5, min / 3));
  }
}
