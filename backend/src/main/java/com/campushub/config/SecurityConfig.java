package com.campushub.config;
import com.campushub.security.*;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.*;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy;
import org.springframework.web.cors.*;

@Configuration @EnableMethodSecurity
public class SecurityConfig {
  @Bean PasswordEncoder passwordEncoder(){ return new BCryptPasswordEncoder(12); }

  // These filters live inside the security chain only; stop Boot from also registering them as plain servlet filters.
  @Bean FilterRegistrationBean<JwtAuthFilter> jwtReg(JwtAuthFilter f){ var r = new FilterRegistrationBean<>(f); r.setEnabled(false); return r; }
  @Bean FilterRegistrationBean<RateLimitFilter> rateReg(RateLimitFilter f){ var r = new FilterRegistrationBean<>(f); r.setEnabled(false); return r; }

  @Bean SecurityFilterChain chain(HttpSecurity http, JwtAuthFilter jwt, RateLimitFilter rate) throws Exception {
    http.csrf(AbstractHttpConfigurer::disable).cors(c -> {})
      .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
      .headers(h -> h.frameOptions(f -> f.deny()).httpStrictTransportSecurity(s -> s.includeSubDomains(true).maxAgeInSeconds(31536000))
        .referrerPolicy(r -> r.policy(ReferrerPolicy.NO_REFERRER)))
      .authorizeHttpRequests(a -> a
        .requestMatchers("/api/auth/**", "/api/dev/**", "/api/payments/webhook/**", "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
        .requestMatchers(HttpMethod.GET, "/api/shops/**", "/api/categories/**").hasAnyRole("STUDENT", "SHOP_OWNER", "SUPER_ADMIN")
        .requestMatchers("/api/student/**").hasRole("STUDENT")
        .requestMatchers("/api/shop-owner/**").hasRole("SHOP_OWNER")
        .requestMatchers("/api/admin/**").hasRole("SUPER_ADMIN")
        .anyRequest().authenticated())
      .exceptionHandling(e -> e
        .authenticationEntryPoint((req, res, ex) -> json(res, 401, "Please sign in to continue."))
        .accessDeniedHandler((req, res, ex) -> json(res, 403, "You don't have access to this.")))
      .addFilterBefore(jwt, UsernamePasswordAuthenticationFilter.class)
      .addFilterAfter(rate, JwtAuthFilter.class);
    return http.build();
  }
  private static void json(jakarta.servlet.http.HttpServletResponse r, int code, String msg) throws java.io.IOException {
    r.setStatus(code); r.setContentType("application/json");
    r.getWriter().write("{\"success\":false,\"message\":\"" + msg + "\",\"data\":null}");
  }
  @Bean CorsConfigurationSource cors(@Value("${app.cors.allowed-origins}") List<String> origins){
    CorsConfiguration c = new CorsConfiguration();
    c.setAllowedOrigins(origins); c.setAllowedMethods(List.of("GET","POST","PUT","PATCH","DELETE","OPTIONS"));
    c.setAllowedHeaders(List.of("Authorization","Content-Type","X-Signature")); c.setMaxAge(3600L);
    UrlBasedCorsConfigurationSource s = new UrlBasedCorsConfigurationSource(); s.registerCorsConfiguration("/api/**", c); return s;
  }
}
