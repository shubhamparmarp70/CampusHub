package com.campushub.pricing;
import static org.junit.jupiter.api.Assertions.*;
import com.campushub.common.Tokens;
import org.junit.jupiter.api.Test;
class TokensAndOtpHashTest {
  @Test void hmac_is_deterministic_and_key_dependent(){
    assertEquals(Tokens.hmac("k1", "42:123456"), Tokens.hmac("k1", "42:123456"));
    assertNotEquals(Tokens.hmac("k1", "42:123456"), Tokens.hmac("k2", "42:123456"));
    assertNotEquals(Tokens.hmac("k1", "42:123456"), Tokens.hmac("k1", "43:123456"));   // same OTP, different order -> different hash
  }
  @Test void safe_equals(){ assertTrue(Tokens.safeEquals("abc", "abc")); assertFalse(Tokens.safeEquals("abc", "abd")); assertFalse(Tokens.safeEquals("abc", "abcd")); }
  @Test void random_tokens_are_unique_and_long_enough(){ assertNotEquals(Tokens.random(32), Tokens.random(32)); assertTrue(Tokens.random(32).length() >= 43); }
  @Test void otp_range(){ for (int i = 0; i < 1000; i++) { int n = Tokens.randomInt(1_000_000); assertTrue(n >= 0 && n < 1_000_000); assertEquals(6, String.format("%06d", n).length()); } }
}
