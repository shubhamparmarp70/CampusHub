package com.campushub.pricing;
import java.math.*;
/** Advance / remaining split. The remaining amount is derived by subtraction so the two always add up exactly. */
public final class Money {
  private Money(){}
  public record Split(BigDecimal advance, BigDecimal remaining) {}
  public static Split advanceSplit(BigDecimal total, int advancePercent){
    if (total.signum() < 0) throw new IllegalArgumentException("Total can't be negative.");
    BigDecimal adv = total.multiply(BigDecimal.valueOf(advancePercent)).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
    return new Split(adv, total.subtract(adv).setScale(2, RoundingMode.UNNECESSARY));
  }
  /** Discount can never push the total below zero. */
  public static BigDecimal applyDiscount(BigDecimal subtotal, BigDecimal discount){ return subtotal.subtract(discount.min(subtotal)).max(BigDecimal.ZERO); }
}
