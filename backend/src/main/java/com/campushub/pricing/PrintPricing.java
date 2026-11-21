package com.campushub.pricing;
import com.campushub.pricing.Enums.*;
import java.math.BigDecimal;
import java.util.Map;
/** A shop's rate card, loaded from print_pricing. Nothing is hard-coded. */
public record PrintPricing(Map<PageSize, Map<ColorMode, BigDecimal>> pageRates, DuplexRule duplexRule, BigDecimal duplexDiscountPerPage,
                           BigDecimal spiral, BigDecimal tape, BigDecimal laminationPerPage, BigDecimal staplingPerCopy) {
  public BigDecimal rate(PageSize s, ColorMode c){
    BigDecimal r = pageRates.getOrDefault(s, Map.of()).get(c);
    if (r == null) throw new IllegalArgumentException("This shop doesn't offer " + s + " " + c + " printing.");
    return r;
  }
  public BigDecimal bindingPrice(Binding b){ return switch (b) { case NONE -> BigDecimal.ZERO; case SPIRAL -> spiral; case TAPE -> tape; }; }
}
