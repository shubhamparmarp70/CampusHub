package com.campushub.security;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** Fixed-window limiter (per minute). In-memory: fine for one instance; move to Redis/Bucket4j when you scale out. */
@Component
public class RateLimitFilter extends OncePerRequestFilter {
  private record Win(long start, AtomicInteger count) {}
  private final ConcurrentHashMap<String, Win> wins = new ConcurrentHashMap<>();
  private final boolean enabled;
  public RateLimitFilter(@Value("${app.rate-limit.enabled:true}") boolean enabled){ this.enabled = enabled; }

  @Override protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain) throws ServletException, IOException {
    if (!enabled) { chain.doFilter(req, res); return; }
    String path = req.getRequestURI(), ip = req.getRemoteAddr(), key; int limit;
    Authentication a = SecurityContextHolder.getContext().getAuthentication();
    String who = a != null && a.getPrincipal() instanceof AuthUser u ? "u" + u.id() : "ip" + ip;
    if (path.startsWith("/api/auth/")) { key = "auth:" + ip + path; limit = 10; }
    else if (path.startsWith("/api/payments/webhook/")) { key = "hook:" + ip; limit = 600; }
    else if (path.endsWith("/verify-pickup")) { key = "verify:" + who; limit = 10; }
    else if (path.endsWith("/otp")) { key = "otp:" + who; limit = 5; }
    else if (path.endsWith("/files") && "POST".equals(req.getMethod())) { key = "upload:" + who; limit = 20; }
    else { key = "api:" + who; limit = 180; }
    long now = System.currentTimeMillis();
    Win w = wins.compute(key, (k, old) -> { if (old == null || now - old.start() >= 60_000) return new Win(now, new AtomicInteger(1)); old.count().incrementAndGet(); return old; });
    if (wins.size() > 20_000) wins.values().removeIf(x -> now - x.start() >= 60_000);
    if (w.count().get() > limit) {
      res.setStatus(429); res.setHeader("Retry-After", "60"); res.setContentType("application/json");
      res.getWriter().write("{\"success\":false,\"message\":\"Too many requests. Please slow down and try again in a minute.\",\"data\":null}");
      return;
    }
    chain.doFilter(req, res);
  }
}
