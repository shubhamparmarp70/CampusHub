package com.campushub.pricing;
import java.math.*;
/** fee = percent of the discounted item total, raised to a minimum and capped at a maximum (max 0 = no cap). No fee on an empty/zero total. */
public final class PlatformFee {
  private PlatformFee(){}
  public static BigDecimal compute(BigDecimal base, BigDecimal percent, BigDecimal min, BigDecimal max){
    BigDecimal zero = BigDecimal.ZERO.setScale(2);
    if (base.signum() <= 0 || percent.signum() <= 0) return zero;
    BigDecimal fee = base.multiply(percent).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
    if (fee.compareTo(min) < 0) fee = min;
    if (max.signum() > 0 && fee.compareTo(max) > 0) fee = max;
    return fee.setScale(2, RoundingMode.HALF_UP);
  }
}
