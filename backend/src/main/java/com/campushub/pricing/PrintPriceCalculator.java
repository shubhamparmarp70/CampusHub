package com.campushub.pricing;
import com.campushub.pricing.Enums.*;
import java.math.*;
import org.springframework.stereotype.Component;

/** The backend is the only source of truth for printing prices. All money is BigDecimal. */
@Component
public class PrintPriceCalculator {
  public PriceBreakdown calculate(PrintPricing p, PrintConfig c){
    if (c.copies() < 1 || c.copies() > 500) throw new IllegalArgumentException("Copies must be between 1 and 500.");
    int pages = PageRange.count(c.pageRange(), c.totalPages());
    int sheets = c.sides() == Sides.DOUBLE ? (pages + 1) / 2 : pages;
    BigDecimal rate = p.rate(c.size(), c.color());

    BigDecimal printing = switch (p.duplexRule()) {
      case PER_PAGE -> {
        BigDecimal r = c.sides() == Sides.DOUBLE ? rate.subtract(p.duplexDiscountPerPage()).max(BigDecimal.ZERO) : rate;
        yield r.multiply(BigDecimal.valueOf(pages));
      }
      case PER_SHEET -> rate.multiply(BigDecimal.valueOf(sheets));
    };
    BigDecimal extras = p.bindingPrice(c.binding())
      .add(c.lamination() ? p.laminationPerPage().multiply(BigDecimal.valueOf(pages)) : BigDecimal.ZERO)
      .add(c.stapling() ? p.staplingPerCopy() : BigDecimal.ZERO);

    BigDecimal perCopy = printing.add(extras).setScale(2, RoundingMode.HALF_UP);
    return new PriceBreakdown(pages, sheets, perCopy, perCopy.multiply(BigDecimal.valueOf(c.copies())).setScale(2, RoundingMode.HALF_UP));
  }
}
