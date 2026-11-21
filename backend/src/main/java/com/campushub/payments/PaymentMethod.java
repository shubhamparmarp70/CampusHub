package com.campushub.payments;
import com.campushub.common.BusinessException;
/** MVP: PhonePe and Google Pay only. Adding a method later = new enum value + gateway support; no card data is ever handled here. */
public enum PaymentMethod {
  PHONEPE, GOOGLE_PAY;
  public static PaymentMethod of(String s){
    try { return valueOf(s.trim().toUpperCase()); } catch (Exception e) { throw BusinessException.badRequest("BAD_PAYMENT_METHOD", "Pay with PhonePe or Google Pay."); }
  }
}
