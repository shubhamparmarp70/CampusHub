package com.campushub.pricing;
import static org.junit.jupiter.api.Assertions.*;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class MoneyTest {
  @Test void advance_is_exactly_half_for_even_totals(){
    var s = Money.advanceSplit(new BigDecimal("500.00"), 50);
    assertEquals(new BigDecimal("250.00"), s.advance()); assertEquals(new BigDecimal("250.00"), s.remaining());
  }
  @Test void odd_paise_totals_still_add_up_exactly(){
    var s = Money.advanceSplit(new BigDecimal("0.05"), 50);   // 0.025 rounds half-up to 0.03
    assertEquals(new BigDecimal("0.03"), s.advance()); assertEquals(new BigDecimal("0.02"), s.remaining());
    assertEquals(new BigDecimal("0.05"), s.advance().add(s.remaining()));
  }
  @Test void spec_mixed_cart_example(){
    BigDecimal sub = new BigDecimal("220"), total = Money.applyDiscount(sub, new BigDecimal("20"));
    assertEquals(0, new BigDecimal("200").compareTo(total));
  }
  @Test void discount_never_makes_total_negative(){
    assertEquals(0, BigDecimal.ZERO.compareTo(Money.applyDiscount(new BigDecimal("30"), new BigDecimal("100"))));
  }
}
