package com.campushub.auth;
import com.campushub.common.ApiResponse;
import com.campushub.security.AuthUser;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
/** Tiny endpoints that prove role separation end to end. Delete once real controllers exist. */
@RestController
public class RoleProbeController {
  @GetMapping("/api/student/ping") ApiResponse<String> s(@AuthenticationPrincipal AuthUser u){ return ApiResponse.ok("student ok", u.email()); }
  @GetMapping("/api/shop-owner/ping") ApiResponse<String> o(@AuthenticationPrincipal AuthUser u){ return ApiResponse.ok("owner ok", u.email()); }
  @GetMapping("/api/admin/ping") ApiResponse<String> a(@AuthenticationPrincipal AuthUser u){ return ApiResponse.ok("admin ok", u.email()); }
}
