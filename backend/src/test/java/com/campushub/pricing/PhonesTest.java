package com.campushub.pricing;
import static org.junit.jupiter.api.Assertions.*;
import com.campushub.common.*;
import org.junit.jupiter.api.Test;
class PhonesTest {
  @Test void indian_numbers_are_normalised(){
    for (String in : new String[]{"98765 43210", "9876543210", "+91 98765-43210", "09876543210", "919876543210"}) assertEquals("+919876543210", Phones.normalize(in), in);
  }
  @Test void international_numbers_need_a_plus(){ assertEquals("+14155552671", Phones.normalize("+1 415 555 2671")); }
  @Test void rubbish_is_rejected(){
    for (String bad : new String[]{"12345", "5876543210", "abc", "", "+0123456789", "98765"}) assertThrows(BusinessException.class, () -> Phones.normalize(bad), bad);
    assertThrows(BusinessException.class, () -> Phones.normalize(null));
  }
  @Test void masking_hides_the_middle(){ assertEquals("+91********10", Phones.mask("+919876543210")); }
}
