package com.campushub.common;
/** Turns what a person types into E.164 (+919876543210). A bare 10-digit number is treated as Indian; other countries need a leading +. */
public final class Phones {
  private Phones(){}
  private static BusinessException bad(){ return BusinessException.badRequest("BAD_PHONE", "Enter a valid mobile number, for example 98765 43210."); }
  public static String normalize(String raw){
    if (raw == null) throw bad();
    String s = raw.trim().replaceAll("[\\s-]", "");
    if (s.startsWith("+")) { if (!s.matches("\\+[1-9]\\d{7,14}")) throw bad(); return s; }
    if (s.matches("[6-9]\\d{9}")) return "+91" + s;
    if (s.matches("0[6-9]\\d{9}")) return "+91" + s.substring(1);
    if (s.matches("91[6-9]\\d{9}")) return "+" + s;
    throw bad();
  }
  public static String mask(String e164){ return e164.length() <= 6 ? "******" : e164.substring(0, 3) + "*".repeat(e164.length() - 5) + e164.substring(e164.length() - 2); }
}
