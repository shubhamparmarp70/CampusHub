package com.campushub.common;
import org.springframework.stereotype.Service;
@Service
public class Settings {
  private final Db db;
  public Settings(Db db){ this.db = db; }
  public java.math.BigDecimal getDecimal(String key, java.math.BigDecimal def){ return db.scalar(String.class, "SELECT setting_value FROM system_settings WHERE setting_key=?", key).map(java.math.BigDecimal::new).orElse(def); }
  public int getInt(String key, int def){ return db.scalar(String.class, "SELECT setting_value FROM system_settings WHERE setting_key=?", key).map(Integer::parseInt).orElse(def); }
}
