package com.campushub.notification;
import com.campushub.catalog.ShopAccess;
import com.campushub.common.*;
import com.campushub.security.AuthUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/** Owner broadcasts to followers. Capped at 3 per shop per day, and each follower's preferences still apply. */
@RestController @RequestMapping("/api/shop-owner/announcements")
public class AnnouncementController {
  public record Req(@NotBlank @Pattern(regexp="DISCOUNT|NEW_PRODUCT|SHOP_UPDATE|PROMO") String category, @NotBlank @Size(max=160) String title, @Size(max=500) String body) {}
  private static final int DAILY_CAP = 3;
  private final Db db; private final ShopAccess access; private final NotificationService notes; private final AuditService audit;
  public AnnouncementController(Db db, ShopAccess a, NotificationService n, AuditService au){ this.db = db; access = a; notes = n; audit = au; }

  @PostMapping ApiResponse<Integer> send(@AuthenticationPrincipal AuthUser u, @Valid @RequestBody Req r){
    long shop = access.approvedShopId(u);
    if (db.count("SELECT COUNT(*) FROM audit_logs WHERE action='ANNOUNCEMENT' AND entity_id=? AND created_at>=CURDATE()", String.valueOf(shop)) >= DAILY_CAP)
      throw new BusinessException(HttpStatus.TOO_MANY_REQUESTS, "ANNOUNCEMENT_CAP", "You can send up to " + DAILY_CAP + " announcements a day.");
    var followers = db.rows("SELECT student_id FROM shop_followers WHERE shop_id=? LIMIT 5000", shop);
    for (var f : followers) notes.notify(Db.l(f.get("student_id")), null, r.category(), r.title(), r.body());
    audit.log(u.id(), u.email(), "ANNOUNCEMENT", "shop", String.valueOf(shop));
    return ApiResponse.ok("Sent to your followers (those who allow this kind of update).", followers.size());
  }
}
