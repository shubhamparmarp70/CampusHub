package com.campushub.sms;
import com.campushub.common.*;
import java.time.LocalDateTime;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** SMS code for confirming a mobile number: 6 digits, 10 minutes, 5 wrong tries, one new code a minute, 5 codes an hour. Stored only as a keyed hash. */
@Service
public class PhoneOtpService {
  private static final int TTL_MIN = 10, MAX_ATTEMPTS = 5, COOLDOWN_SEC = 60, MAX_PER_HOUR = 5;
  private final Db db; private final SmsSender sms;
  @Value("${app.otp.secret}") private String secret;
  public PhoneOtpService(Db db, SmsSender sms){ this.db = db; this.sms = sms; }
  private String hash(long userId, String phone, String otp){ return Tokens.hmac(secret, userId + ":" + phone + ":" + otp); }
  private static BusinessException tooMany(String code, String msg){ return new BusinessException(HttpStatus.TOO_MANY_REQUESTS, code, msg); }

  /** Creates a fresh code and texts it. If the SMS fails, the surrounding transaction rolls back. Returns the seconds before another code may be requested. */
  @Transactional
  public int send(long userId, String phone){
    LocalDateTime now = LocalDateTime.now(), windowStart = now; int count = 1;
    var row = db.one("SELECT last_sent_at, sent_count, window_start FROM phone_verifications WHERE user_id=? FOR UPDATE", userId).orElse(null);
    if (row != null) {
      if (now.isBefore(Db.ts(row.get("last_sent_at")).plusSeconds(COOLDOWN_SEC))) throw tooMany("OTP_TOO_SOON", "Please wait a minute before asking for another code.");
      windowStart = Db.ts(row.get("window_start")); count = Db.i(row.get("sent_count"));
      if (now.isAfter(windowStart.plusHours(1))) { windowStart = now; count = 0; }
      if (count >= MAX_PER_HOUR) throw tooMany("OTP_LIMIT", "Too many codes requested. Please try again in an hour.");
      count++;
    }
    String otp = String.format("%06d", Tokens.randomInt(1_000_000));
    db.update("INSERT INTO phone_verifications(user_id, phone, otp_hash, expires_at, attempts, sent_count, window_start, last_sent_at) VALUES (?,?,?,?,0,?,?,?) AS n ON DUPLICATE KEY UPDATE phone=n.phone, otp_hash=n.otp_hash, expires_at=n.expires_at, attempts=0, sent_count=n.sent_count, window_start=n.window_start, last_sent_at=n.last_sent_at",
      userId, phone, hash(userId, phone, otp), now.plusMinutes(TTL_MIN), count, windowStart, now);
    sms.send(phone, "Your Campus Hub verification code is " + otp + ". It expires in " + TTL_MIN + " minutes. Never share it with anyone.");
    return COOLDOWN_SEC;
  }

  /** Wrong guesses are counted even though the call fails (noRollbackFor), so the attempt limit can't be bypassed. The code works once. */
  @Transactional(noRollbackFor = BusinessException.class)
  public void verify(long userId, String code){
    var r = db.one("SELECT otp_hash, attempts, phone, expires_at > ? AS valid FROM phone_verifications WHERE user_id=? FOR UPDATE", LocalDateTime.now(), userId)
      .orElseThrow(() -> BusinessException.badRequest("INVALID_CODE", "That code is wrong or has expired. Request a new one."));
    if (!Db.bool(r.get("valid"))) throw new BusinessException(HttpStatus.GONE, "OTP_EXPIRED", "This code has expired. Request a new one.");
    int attempts = Db.i(r.get("attempts"));
    if (attempts >= MAX_ATTEMPTS) throw tooMany("OTP_LOCKED", "Too many wrong codes. Request a new one.");
    if (code == null || !Tokens.safeEquals(hash(userId, (String) r.get("phone"), code.trim()), (String) r.get("otp_hash"))) {
      db.update("UPDATE phone_verifications SET attempts=attempts+1 WHERE user_id=?", userId);
      throw BusinessException.badRequest("INVALID_CODE", "That code is wrong. " + (MAX_ATTEMPTS - attempts - 1) + " attempt(s) left.");
    }
    db.update("DELETE FROM phone_verifications WHERE user_id=?", userId);
  }
}
