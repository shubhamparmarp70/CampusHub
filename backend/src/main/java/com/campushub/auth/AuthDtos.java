package com.campushub.auth;
import com.campushub.user.RoleName;
import jakarta.validation.constraints.*;
import java.util.Set;
public final class AuthDtos {
  private AuthDtos(){}
  public record RegisterRequest(
    @NotBlank @Size(max=120) String fullName, @NotBlank @Email @Size(max=190) String email,
    @NotBlank @Size(min=8, max=72, message="Password must be 8–72 characters") String password,
    @NotBlank(message="Enter your mobile number") @Pattern(regexp="^[0-9+\\- ]{8,20}$", message="Enter a valid mobile number") String phone, @NotNull RoleName role) {}
  public record PendingVerification(boolean phoneVerificationRequired, String email, String maskedPhone, int resendInSeconds) {}
  public record VerifyPhoneRequest(@NotBlank @Email String email, @NotBlank @Pattern(regexp="\\d{6}", message="The code has 6 digits") String otp) {}
  public record ResendRequest(@NotBlank @Email String email) {}
  public record ResendInfo(String maskedPhone, int resendInSeconds) {}
  public record LoginRequest(@NotBlank @Email String email, @NotBlank String password) {}
  public record RefreshRequest(@NotBlank String refreshToken) {}
  public record ForgotRequest(@NotBlank @Email String email) {}
  public record ResetRequest(@NotBlank String token, @NotBlank @Size(min=8, max=72, message="Password must be 8–72 characters") String newPassword) {}
  public record TokenRequest(@NotBlank String token) {}
  public record AuthResponse(String accessToken, String refreshToken, long expiresInSeconds, Long userId, String fullName, Set<RoleName> roles) {}
}
