package com.campushub.security;
import io.jsonwebtoken.*;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.List;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class JwtAuthFilter extends OncePerRequestFilter {
  private final JwtService jwt;
  public JwtAuthFilter(JwtService jwt){ this.jwt = jwt; }
  @Override protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain) throws ServletException, IOException {
    String h = req.getHeader("Authorization");
    if (h != null && h.startsWith("Bearer ")) {
      try {
        Claims c = jwt.parse(h.substring(7));
        @SuppressWarnings("unchecked") List<String> roles = c.get("roles", List.class);
        var auth = new UsernamePasswordAuthenticationToken(new AuthUser(Long.valueOf(c.getSubject()), c.get("email", String.class)), null,
          roles.stream().map(r -> new SimpleGrantedAuthority("ROLE_" + r)).toList());
        SecurityContextHolder.getContext().setAuthentication(auth);
      } catch (JwtException | IllegalArgumentException e) { SecurityContextHolder.clearContext(); }  // invalid/expired -> anonymous -> 401
    }
    chain.doFilter(req, res);
  }
}
