package com.campushub.notification;
import com.campushub.common.*;
import com.campushub.security.AuthUser;
import java.util.*;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController @RequestMapping("/api/notifications")
public class NotificationController {
  private final Db db; private final SseHub hub;
  public NotificationController(Db db, SseHub hub){ this.db = db; this.hub = hub; }
  @GetMapping ApiResponse<List<Map<String,Object>>> list(@AuthenticationPrincipal AuthUser u, @RequestParam(defaultValue="false") boolean unreadOnly){
    return ApiResponse.ok("OK", db.rows("SELECT id, order_id, category, title, body, read_at, created_at FROM notifications WHERE user_id=?" + (unreadOnly ? " AND read_at IS NULL" : "") + " ORDER BY id DESC LIMIT 50", u.id())); }
  @GetMapping("/unread-count") ApiResponse<Long> unread(@AuthenticationPrincipal AuthUser u){ return ApiResponse.ok("OK", db.count("SELECT COUNT(*) FROM notifications WHERE user_id=? AND read_at IS NULL", u.id())); }
  @PostMapping("/{id}/read") ApiResponse<Void> read(@AuthenticationPrincipal AuthUser u, @PathVariable long id){ db.update("UPDATE notifications SET read_at=NOW(3) WHERE id=? AND user_id=? AND read_at IS NULL", id, u.id()); return ApiResponse.ok("Marked as read.", null); }
  @PostMapping("/read-all") ApiResponse<Void> readAll(@AuthenticationPrincipal AuthUser u){ db.update("UPDATE notifications SET read_at=NOW(3) WHERE user_id=? AND read_at IS NULL", u.id()); return ApiResponse.ok("All marked as read.", null); }
  /** Server-Sent Events. Browsers' EventSource can't set headers, so web/mobile clients should use fetch-event-source with the Bearer token. */
  @GetMapping(value="/stream", produces=MediaType.TEXT_EVENT_STREAM_VALUE) SseEmitter stream(@AuthenticationPrincipal AuthUser u){ return hub.subscribe(u.id()); }
}
