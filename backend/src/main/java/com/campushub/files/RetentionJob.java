package com.campushub.files;
import com.campushub.common.Db;
import java.time.LocalDateTime;
import org.slf4j.*;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
/** Deletes documents once their retention window has passed (shortened to 24h after an order is collected or refunded). */
@Component
public class RetentionJob {
  private static final Logger log = LoggerFactory.getLogger(RetentionJob.class);
  private final Db db; private final StorageService storage;
  public RetentionJob(Db db, StorageService s){ this.db = db; storage = s; }
  @Scheduled(initialDelay = 120_000, fixedDelay = 3_600_000)
  public void purge(){
    for (var f : db.rows("SELECT id, storage_key FROM print_files WHERE deleted_at IS NULL AND delete_after < ? LIMIT 500", LocalDateTime.now())) {
      try { storage.delete((String) f.get("storage_key")); db.update("UPDATE print_files SET deleted_at=NOW(3) WHERE id=?", f.get("id")); }
      catch (Exception e) { log.warn("Retention delete failed for file {}", f.get("id"), e); }
    }
  }
}
