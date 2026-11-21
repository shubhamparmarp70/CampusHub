package com.campushub.pricing;
import static org.junit.jupiter.api.Assertions.*;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
class PlatformFeeTest {
  static BigDecimal d(String s){ return new BigDecimal(s); }
  @Test void disabled_when_percent_is_zero(){ assertEquals(d("0.00"), PlatformFee.compute(d("200"), d("0"), d("0"), d("0"))); }
  @Test void percent_of_discounted_total(){ assertEquals(d("4.00"), PlatformFee.compute(d("200"), d("2"), d("0"), d("0"))); }
  @Test void minimum_applies(){ assertEquals(d("3.00"), PlatformFee.compute(d("20"), d("2"), d("3"), d("0"))); }
  @Test void maximum_caps(){ assertEquals(d("10.00"), PlatformFee.compute(d("2000"), d("2"), d("0"), d("10"))); }
  @Test void rounds_half_up_to_paise(){ assertEquals(d("0.03"), PlatformFee.compute(d("1.25"), d("2"), d("0"), d("0"))); }
  @Test void no_fee_on_zero_total(){ assertEquals(d("0.00"), PlatformFee.compute(d("0"), d("5"), d("3"), d("0"))); }
  @Test void total_with_fee_still_splits_exactly(){
    BigDecimal total = d("200").add(PlatformFee.compute(d("200"), d("2.5"), d("0"), d("0")));      // 205.00
    var s = Money.advanceSplit(total, 50);
    assertEquals(0, total.compareTo(s.advance().add(s.remaining())));
  }
}
