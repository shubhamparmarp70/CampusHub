package com.campushub.common;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
@Service
public class AuditService {
  private final JdbcTemplate jdbc;
  public AuditService(JdbcTemplate jdbc){ this.jdbc = jdbc; }
  public void log(Long actorId, String email, String action, String entity, String entityId){
    jdbc.update("INSERT INTO audit_logs(actor_id,actor_email,action,entity,entity_id) VALUES (?,?,?,?,?)", actorId, email, action, entity, entityId);
  }
}
