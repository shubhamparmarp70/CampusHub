package com.campushub.auth;
import com.campushub.auth.AuthDtos.*;
import com.campushub.common.*;
import com.campushub.security.JwtService;
import com.campushub.sms.PhoneOtpService;
import com.campushub.user.*;
import java.time.LocalDateTime;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {
  private static final int MAX_FAILED = 5;
  private final UserRepository users; private final RoleRepository roles; private final PasswordEncoder enc; private final JwtService jwt;
  private final AuditService audit; private final Db db; private final Mailer mail; private final PhoneOtpService phoneOtp;
  @Value("${app.jwt.refresh-ttl-days}") private long refreshDays;
  @Value("${app.base-url}") private String baseUrl;
  @Value("${app.require-email-verification:false}") private boolean requireVerify;

  public AuthService(UserRepository u, RoleRepository r, PasswordEncoder e, JwtService j, AuditService a, Db db, Mailer m, PhoneOtpService po){ users=u; roles=r; enc=e; jwt=j; audit=a; this.db=db; mail=m; phoneOtp=po; }

  private static BusinessException expired(){ return new BusinessException(HttpStatus.UNAUTHORIZED, "INVALID_REFRESH", "Your session expired. Please sign in again."); }

  @Transactional
  public PendingVerification register(RegisterRequest r){
    if (r.role() == RoleName.SUPER_ADMIN) throw BusinessException.badRequest("ROLE_NOT_ALLOWED", "That account type can't be created here.");
    if (users.existsByEmailIgnoreCase(r.email())) throw BusinessException.conflict("EMAIL_TAKEN", "An account with this email already exists.");
    String phone = Phones.normalize(r.phone());
    if (db.count("SELECT COUNT(*) FROM users WHERE phone=? AND phone_verified=TRUE", phone) > 0) throw BusinessException.conflict("PHONE_TAKEN", "This mobile number is already registered. Try signing in.");
    User u = new User();
    u.setEmail(r.email().trim().toLowerCase()); u.setFullName(r.fullName().trim()); u.setPhone(phone); u.setPhoneVerified(false);
    u.setPasswordHash(enc.encode(r.password()));
    u.setStatus(r.role() == RoleName.SHOP_OWNER ? UserStatus.PENDING : UserStatus.ACTIVE);
    u.getRoles().add(roles.findByName(r.role()).orElseThrow());
    users.save(u);
    db.update("INSERT IGNORE INTO notification_preferences(user_id) VALUES (?)", u.getId());
    if (r.role() == RoleName.STUDENT) db.update("INSERT IGNORE INTO student_profiles(user_id) VALUES (?)", u.getId());
    sendVerification(u);
    audit.log(u.getId(), u.getEmail(), "REGISTER", "user", String.valueOf(u.getId()));
    int wait = phoneOtp.send(u.getId(), phone);      // if the SMS can't be sent, the whole sign-up rolls back so the person can simply try again
    return new PendingVerification(true, u.getEmail(), Phones.mask(phone), wait);
  }

  @Transactional(noRollbackFor = {BadCredentialsException.class, BusinessException.class})
  public AuthResponse login(LoginRequest r){
    User u = users.findByEmailIgnoreCase(r.email().trim()).orElse(null);
    if (u == null) { audit.log(null, r.email(), "LOGIN_FAILED", "user", null); throw new BadCredentialsException("bad"); }
    if (u.getStatus() == UserStatus.SUSPENDED) throw new BusinessException(HttpStatus.FORBIDDEN, "SUSPENDED", "This account is suspended. Contact support.");
    if (u.getFailedLogins() >= MAX_FAILED) throw new BusinessException(HttpStatus.TOO_MANY_REQUESTS, "LOCKED", "Too many failed attempts. Reset your password to continue.");
    if (!enc.matches(r.password(), u.getPasswordHash())) {
      u.setFailedLogins(u.getFailedLogins() + 1);
      audit.log(u.getId(), u.getEmail(), "LOGIN_FAILED", "user", String.valueOf(u.getId()));
      throw new BadCredentialsException("bad");
    }
    if (!u.isPhoneVerified()) { u.setFailedLogins(0); throw new BusinessException(HttpStatus.FORBIDDEN, "PHONE_NOT_VERIFIED", "Please verify your mobile number to continue."); }
    if (requireVerify && !u.isEmailVerified()) throw new BusinessException(HttpStatus.FORBIDDEN, "EMAIL_NOT_VERIFIED", "Please verify your email first. Check your inbox.");
    u.setFailedLogins(0); audit.log(u.getId(), u.getEmail(), "LOGIN", "user", String.valueOf(u.getId()));
    return respond(u);
  }

  /** Rotating refresh tokens. Reusing an already-rotated token revokes every session for that user (token theft signal). */
  @Transactional(noRollbackFor = BusinessException.class)
  public AuthResponse refresh(String raw){
    var row = db.one("SELECT id, user_id, revoked, expires_at > ? AS valid FROM refresh_tokens WHERE token_hash=? FOR UPDATE", LocalDateTime.now(), Tokens.sha256(raw)).orElseThrow(AuthService::expired);
    long userId = Db.l(row.get("user_id"));
    if (Db.bool(row.get("revoked"))) { db.update("UPDATE refresh_tokens SET revoked=TRUE WHERE user_id=?", userId); audit.log(userId, null, "REFRESH_REUSE_DETECTED", "user", String.valueOf(userId)); throw expired(); }
    if (!Db.bool(row.get("valid"))) throw expired();
    User u = users.findById(userId).orElseThrow(AuthService::expired);
    if (u.getStatus() == UserStatus.SUSPENDED) throw expired();
    db.update("UPDATE refresh_tokens SET revoked=TRUE WHERE id=?", row.get("id"));
    return respond(u);
  }

  @Transactional public void logout(String raw){ if (raw != null) db.update("UPDATE refresh_tokens SET revoked=TRUE WHERE token_hash=?", Tokens.sha256(raw)); }

  @Transactional public void forgotPassword(String email){   // same response whether or not the account exists
    users.findByEmailIgnoreCase(email.trim()).ifPresent(u -> {
      db.update("DELETE FROM password_reset_tokens WHERE user_id=? AND used_at IS NULL", u.getId());
      String t = Tokens.random(32);
      db.update("INSERT INTO password_reset_tokens(user_id, token_hash, expires_at) VALUES (?,?,?)", u.getId(), Tokens.sha256(t), LocalDateTime.now().plusHours(1));
      mail.send(u.getEmail(), "Reset your Campus Hub password", "Use this link within 1 hour:\n" + baseUrl + "/reset-password?token=" + t + "\n\nIf you didn't ask for this, ignore this email.");
      audit.log(u.getId(), u.getEmail(), "PASSWORD_RESET_REQUESTED", "user", String.valueOf(u.getId()));
    });
  }

  @Transactional public void resetPassword(ResetRequest r){
    var row = db.one("SELECT id, user_id FROM password_reset_tokens WHERE token_hash=? AND used_at IS NULL AND expires_at > ? FOR UPDATE", Tokens.sha256(r.token()), LocalDateTime.now())
      .orElseThrow(() -> BusinessException.badRequest("INVALID_TOKEN", "This reset link is invalid or has expired."));
    long uid = Db.l(row.get("user_id"));
    db.update("UPDATE users SET password_hash=?, failed_logins=0 WHERE id=?", enc.encode(r.newPassword()), uid);
    db.update("UPDATE password_reset_tokens SET used_at=NOW(3) WHERE id=?", row.get("id"));
    db.update("UPDATE refresh_tokens SET revoked=TRUE WHERE user_id=?", uid);     // sign out everywhere
    audit.log(uid, null, "PASSWORD_RESET", "user", String.valueOf(uid));
  }

  @Transactional public void verifyEmail(String token){
    var row = db.one("SELECT id, user_id FROM email_verification_tokens WHERE token_hash=? AND used_at IS NULL AND expires_at > ? FOR UPDATE", Tokens.sha256(token), LocalDateTime.now())
      .orElseThrow(() -> BusinessException.badRequest("INVALID_TOKEN", "This verification link is invalid or has expired."));
    db.update("UPDATE users SET email_verified=TRUE WHERE id=?", row.get("user_id"));
    db.update("UPDATE email_verification_tokens SET used_at=NOW(3) WHERE id=?", row.get("id"));
  }

  /** Confirms the SMS code. This is the moment a new account first gets tokens. Shop owners still wait for the platform to approve their shop. */
  @Transactional(noRollbackFor = BusinessException.class)
  public AuthResponse verifyPhone(String email, String code){
    User u = users.findByEmailIgnoreCase(email.trim()).orElseThrow(() -> BusinessException.badRequest("INVALID_CODE", "That code is wrong or has expired. Request a new one."));
    if (u.getStatus() == UserStatus.SUSPENDED) throw new BusinessException(HttpStatus.FORBIDDEN, "SUSPENDED", "This account is suspended. Contact support.");
    if (u.isPhoneVerified()) throw BusinessException.conflict("ALREADY_VERIFIED", "This number is already verified. Please sign in.");
    phoneOtp.verify(u.getId(), code);
    if (db.count("SELECT COUNT(*) FROM users WHERE phone=? AND phone_verified=TRUE AND id<>?", u.getPhone(), u.getId()) > 0) throw BusinessException.conflict("PHONE_TAKEN", "This mobile number was just registered by another account.");
    u.setPhoneVerified(true);
    audit.log(u.getId(), u.getEmail(), "PHONE_VERIFIED", "user", String.valueOf(u.getId()));
    return respond(u);
  }

  /** Same answer whether or not the account exists, so this can't be used to find out who has signed up. */
  @Transactional
  public ResendInfo resendPhoneOtp(String email){
    User u = users.findByEmailIgnoreCase(email.trim()).orElse(null);
    if (u == null || u.isPhoneVerified() || u.getStatus() == UserStatus.SUSPENDED) return new ResendInfo(null, 60);
    return new ResendInfo(Phones.mask(u.getPhone()), phoneOtp.send(u.getId(), u.getPhone()));
  }

  private void sendVerification(User u){
    String t = Tokens.random(32);
    db.update("INSERT INTO email_verification_tokens(user_id, token_hash, expires_at) VALUES (?,?,?)", u.getId(), Tokens.sha256(t), LocalDateTime.now().plusHours(24));
    mail.send(u.getEmail(), "Verify your Campus Hub email", "Confirm your email:\n" + baseUrl + "/verify-email?token=" + t);
  }
  private AuthResponse respond(User u){
    String refresh = Tokens.random(48);
    db.update("INSERT INTO refresh_tokens(user_id, token_hash, expires_at) VALUES (?,?,?)", u.getId(), Tokens.sha256(refresh), LocalDateTime.now().plusDays(refreshDays));
    return new AuthResponse(jwt.issue(u), refresh, jwt.ttlSeconds(), u.getId(), u.getFullName(), Set.copyOf(u.roleNames()));
  }
}
