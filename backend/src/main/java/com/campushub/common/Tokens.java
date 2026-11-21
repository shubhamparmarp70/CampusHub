package com.campushub.common;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
public final class Tokens {
  private static final SecureRandom RNG = new SecureRandom();
  private Tokens(){}
  public static String random(int bytes){ byte[] b = new byte[bytes]; RNG.nextBytes(b); return Base64.getUrlEncoder().withoutPadding().encodeToString(b); }
  public static int randomInt(int bound){ return RNG.nextInt(bound); }
  public static String sha256(String s){ try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8))); } catch (Exception e){ throw new IllegalStateException(e); } }
  public static String hmac(String key, String msg){
    try { Mac m = Mac.getInstance("HmacSHA256"); m.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256")); return HexFormat.of().formatHex(m.doFinal(msg.getBytes(StandardCharsets.UTF_8))); }
    catch (Exception e){ throw new IllegalStateException(e); } }
  public static boolean safeEquals(String a, String b){ return MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8)); }
}
