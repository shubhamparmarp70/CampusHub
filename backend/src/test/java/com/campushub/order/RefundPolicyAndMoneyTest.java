package com.campushub.order;
import static org.junit.jupiter.api.Assertions.*;
import com.campushub.pricing.Money;
import java.math.*;
import org.junit.jupiter.api.Test;
class RefundPolicyAndMoneyTest {
  @Test void partial_refund_math_rounds_half_up_to_paise(){
    BigDecimal paid = new BigDecimal("125.00"), amt = paid.multiply(BigDecimal.valueOf(50)).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
    assertEquals(new BigDecimal("62.50"), amt);
    assertEquals(new BigDecimal("0.03"), new BigDecimal("0.05").multiply(BigDecimal.valueOf(50)).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP));
  }
  @Test void advance_plus_remaining_always_equals_total(){
    for (String t : new String[]{"1.01", "99.99", "220.00", "0.05", "1234.57"}) { var s = Money.advanceSplit(new BigDecimal(t), 50); assertEquals(0, new BigDecimal(t).compareTo(s.advance().add(s.remaining())), t); }
  }
}
