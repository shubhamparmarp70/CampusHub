package com.campushub.order;
import com.campushub.common.*;
import java.time.LocalDateTime;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 6-digit pickup OTP, created only when an order becomes READY. Stored as an HMAC (keyed with a server secret), never in plain text. */
@Service
public class OtpService {
  private final Db db; private final Settings settings;
  @Value("${app.otp.secret}") private String secret;
  public OtpService(Db db, Settings s){ this.db = db; settings = s; }
  private String hash(long orderId, String otp){ return Tokens.hmac(secret, orderId + ":" + otp); }

  /** Creates (or replaces) the OTP and returns the plain code once, so it can be sent to the student. */
  @Transactional
  public String generate(long orderId){
    String otp = String.format("%06d", Tokens.randomInt(1_000_000));
    db.update("INSERT INTO pickup_otps(order_id, otp_hash, expires_at) VALUES (?,?,?) AS n ON DUPLICATE KEY UPDATE otp_hash=n.otp_hash, expires_at=n.expires_at, attempts=0, used_at=NULL, created_at=NOW(3)",
      orderId, hash(orderId, otp), LocalDateTime.now().plusMinutes(settings.getInt("otp_ttl_minutes", 15)));
    return otp;
  }
  /** Wrong guesses are counted even though the call fails (noRollbackFor), so the attempt limit can't be bypassed. */
  @Transactional(noRollbackFor = BusinessException.class)
  public void verify(long orderId, String code){
    var r = db.one("SELECT otp_hash, attempts, max_attempts, used_at IS NOT NULL AS used, expires_at > ? AS valid FROM pickup_otps WHERE order_id=? FOR UPDATE", LocalDateTime.now(), orderId)
      .orElseThrow(() -> new BusinessException(HttpStatus.BAD_REQUEST, "INVALID_OTP", "No OTP has been issued for this order."));
    if (Db.bool(r.get("used"))) throw new BusinessException(HttpStatus.CONFLICT, "OTP_USED", "This OTP has already been used.");
    if (!Db.bool(r.get("valid"))) throw new BusinessException(HttpStatus.GONE, "OTP_EXPIRED", "This OTP has expired. Ask the student to request a new one.");
    int attempts = Db.i(r.get("attempts")), max = Db.i(r.get("max_attempts"));
    if (attempts >= max) throw new BusinessException(HttpStatus.TOO_MANY_REQUESTS, "OTP_LOCKED", "Too many wrong attempts. Ask the student to request a new OTP.");
    if (code == null || !Tokens.safeEquals(hash(orderId, code.trim()), (String) r.get("otp_hash"))) {
      db.update("UPDATE pickup_otps SET attempts=attempts+1 WHERE order_id=?", orderId);
      throw new BusinessException(HttpStatus.BAD_REQUEST, "INVALID_OTP", "That OTP is wrong. " + (max - attempts - 1) + " attempt(s) left.");
    }
    db.update("UPDATE pickup_otps SET used_at=NOW(3) WHERE order_id=?", orderId);
  }
}
