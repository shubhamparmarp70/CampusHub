package com.campushub.common;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.*;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Component;

/** Thin helper over JdbcClient. Participates in the surrounding @Transactional. Parameters are always bound, never concatenated. */
@Component
public class Db {
  private final JdbcClient jc;
  public Db(JdbcClient jc){ this.jc = jc; }
  private static List<Object> p(Object[] a){ return Arrays.asList(a); }
  public List<Map<String,Object>> rows(String sql, Object... a){ return jc.sql(sql).params(p(a)).query().listOfRows(); }
  public Optional<Map<String,Object>> one(String sql, Object... a){ return rows(sql, a).stream().findFirst(); }
  public <T> Optional<T> scalar(Class<T> t, String sql, Object... a){ return jc.sql(sql).params(p(a)).query(t).optional(); }
  public long count(String sql, Object... a){ return scalar(Long.class, sql, a).orElse(0L); }
  public int update(String sql, Object... a){ return jc.sql(sql).params(p(a)).update(); }
  public long insert(String sql, Object... a){ KeyHolder kh = new GeneratedKeyHolder(); jc.sql(sql).params(p(a)).update(kh); return kh.getKey() == null ? 0L : kh.getKey().longValue(); }
  public static long l(Object o){ return ((Number) o).longValue(); }
  public static int i(Object o){ return ((Number) o).intValue(); }
  public static BigDecimal bd(Object o){ return o instanceof BigDecimal b ? b : new BigDecimal(o.toString()); }
  public static boolean bool(Object o){ return o instanceof Boolean b ? b : o instanceof Number n && n.intValue() != 0; }
  public static LocalDateTime ts(Object o){ return o instanceof Timestamp t ? t.toLocalDateTime() : (LocalDateTime) o; }
}
