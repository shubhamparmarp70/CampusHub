package com.campushub.auth;
import com.campushub.auth.AuthDtos.*;
import com.campushub.common.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/auth")
public class AuthController {
  private final AuthService auth;
  public AuthController(AuthService a){ auth = a; }
  @PostMapping("/register") ResponseEntity<ApiResponse<PendingVerification>> register(@Valid @RequestBody RegisterRequest r){ return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok("We sent a code to your mobile number.", auth.register(r))); }
  @PostMapping("/verify-phone") ApiResponse<AuthResponse> verifyPhone(@Valid @RequestBody VerifyPhoneRequest r){ return ApiResponse.ok("Mobile number verified.", auth.verifyPhone(r.email(), r.otp())); }
  @PostMapping("/resend-phone-otp") ApiResponse<ResendInfo> resendPhone(@Valid @RequestBody ResendRequest r){ return ApiResponse.ok("If the number is registered, a new code is on its way.", auth.resendPhoneOtp(r.email())); }
  @PostMapping("/login") ApiResponse<AuthResponse> login(@Valid @RequestBody LoginRequest r){ return ApiResponse.ok("Signed in.", auth.login(r)); }
  @PostMapping("/refresh") ApiResponse<AuthResponse> refresh(@Valid @RequestBody RefreshRequest r){ return ApiResponse.ok("Session renewed.", auth.refresh(r.refreshToken())); }
  @PostMapping("/logout") ApiResponse<Void> logout(@RequestBody(required = false) RefreshRequest r){ auth.logout(r == null ? null : r.refreshToken()); return ApiResponse.ok("Signed out.", null); }
  @PostMapping("/forgot-password") ApiResponse<Void> forgot(@Valid @RequestBody ForgotRequest r){ auth.forgotPassword(r.email()); return ApiResponse.ok("If that email has an account, a reset link is on its way.", null); }
  @PostMapping("/reset-password") ApiResponse<Void> reset(@Valid @RequestBody ResetRequest r){ auth.resetPassword(r); return ApiResponse.ok("Password updated. Please sign in.", null); }
  @PostMapping("/verify-email") ApiResponse<Void> verify(@Valid @RequestBody TokenRequest r){ auth.verifyEmail(r.token()); return ApiResponse.ok("Email verified.", null); }
}
