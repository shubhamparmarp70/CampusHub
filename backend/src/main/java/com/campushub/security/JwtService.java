package com.campushub.security;
import com.campushub.user.*;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class JwtService {
  private final SecretKey key; private final long ttlMinutes;
  public JwtService(@Value("${app.jwt.secret}") String secret, @Value("${app.jwt.access-ttl-minutes}") long ttl){
    byte[] b = secret.getBytes(StandardCharsets.UTF_8);
    if (b.length < 32) throw new IllegalStateException("JWT_SECRET must be at least 32 bytes");
    this.key = Keys.hmacShaKeyFor(b); this.ttlMinutes = ttl;
  }
  public String issue(User u){
    Instant now = Instant.now();
    return Jwts.builder().subject(String.valueOf(u.getId())).claim("email", u.getEmail())
      .claim("roles", u.roleNames().stream().map(Enum::name).toList())
      .issuedAt(Date.from(now)).expiration(Date.from(now.plusSeconds(ttlMinutes*60))).signWith(key).compact();
  }
  public Claims parse(String token){ return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload(); }
  public long ttlSeconds(){ return ttlMinutes*60; }
}
