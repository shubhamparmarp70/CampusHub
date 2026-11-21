package com.campushub.pricing;
import static org.junit.jupiter.api.Assertions.*;
import com.campushub.pricing.Enums.*;
import java.math.BigDecimal;
import java.util.Map;
import org.junit.jupiter.api.Test;

class PrintPriceCalculatorTest {
  static BigDecimal d(String s){ return new BigDecimal(s); }
  PrintPricing pricing(DuplexRule rule){
    return new PrintPricing(Map.of(
      PageSize.A4, Map.of(ColorMode.BLACK_WHITE, d("2"), ColorMode.COLOR, d("10")),
      PageSize.A3, Map.of(ColorMode.BLACK_WHITE, d("5"), ColorMode.COLOR, d("15"))),
      rule, d("0.50"), d("40"), d("25"), d("15"), d("5"));
  }
  PrintConfig cfg(int pages, String range, Sides sides, int copies, Binding b, boolean lam, boolean stap){
    return new PrintConfig(pages, range, PageSize.A4, ColorMode.BLACK_WHITE, sides, copies, b, lam, stap);
  }
  final PrintPriceCalculator calc = new PrintPriceCalculator();

  @Test void spec_example_30_pages_2_copies_at_rs2_is_120(){
    var r = calc.calculate(pricing(DuplexRule.PER_PAGE), cfg(30, null, Sides.SINGLE, 2, Binding.NONE, false, false));
    assertEquals(d("120.00"), r.total());
  }
  @Test void colour_costs_more(){
    var c = new PrintConfig(10, "", PageSize.A4, ColorMode.COLOR, Sides.SINGLE, 1, Binding.NONE, false, false);
    assertEquals(d("100.00"), calc.calculate(pricing(DuplexRule.PER_PAGE), c).total());
  }
  @Test void page_range_limits_billable_pages_and_counts_overlaps_once(){
    var r = calc.calculate(pricing(DuplexRule.PER_PAGE), cfg(30, "1-5, 4-6, 10", Sides.SINGLE, 1, Binding.NONE, false, false));
    assertEquals(7, r.billablePages());
    assertEquals(d("14.00"), r.total());
  }
  @Test void double_sided_per_page_rule_uses_discount_and_reports_sheets(){
    var r = calc.calculate(pricing(DuplexRule.PER_PAGE), cfg(31, null, Sides.DOUBLE, 1, Binding.NONE, false, false));
    assertEquals(16, r.sheets());                       // 31 pages -> 16 physical sheets
    assertEquals(d("46.50"), r.total());                // 31 x (2 - 0.50)
  }
  @Test void double_sided_per_sheet_rule_bills_sheets(){
    var r = calc.calculate(pricing(DuplexRule.PER_SHEET), cfg(30, null, Sides.DOUBLE, 2, Binding.NONE, false, false));
    assertEquals(d("60.00"), r.total());                // 15 sheets x 2 x 2 copies
  }
  @Test void extras_are_added_per_copy(){
    var r = calc.calculate(pricing(DuplexRule.PER_PAGE), cfg(10, null, Sides.SINGLE, 2, Binding.SPIRAL, true, true));
    // per copy: 20 print + 40 spiral + 150 lamination + 5 staple = 215
    assertEquals(d("215.00"), r.perCopy());
    assertEquals(d("430.00"), r.total());
  }
  @Test void bad_ranges_and_copies_are_rejected(){
    var p = pricing(DuplexRule.PER_PAGE);
    assertThrows(IllegalArgumentException.class, () -> calc.calculate(p, cfg(10, "5-20", Sides.SINGLE, 1, Binding.NONE, false, false)));
    assertThrows(IllegalArgumentException.class, () -> calc.calculate(p, cfg(10, "abc", Sides.SINGLE, 1, Binding.NONE, false, false)));
    assertThrows(IllegalArgumentException.class, () -> calc.calculate(p, cfg(10, null, Sides.SINGLE, 0, Binding.NONE, false, false)));
  }
  @Test void shop_without_a_rate_for_the_size_is_rejected(){
    var c = new PrintConfig(5, null, PageSize.LEGAL, ColorMode.COLOR, Sides.SINGLE, 1, Binding.NONE, false, false);
    assertThrows(IllegalArgumentException.class, () -> calc.calculate(pricing(DuplexRule.PER_PAGE), c));
  }
}
