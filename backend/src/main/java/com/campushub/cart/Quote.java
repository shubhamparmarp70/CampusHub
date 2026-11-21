package com.campushub.cart;
import com.campushub.pricing.PriceBreakdown;
import java.math.BigDecimal;
import java.util.List;
/** Everything the student sees and everything checkout writes comes from here, computed on the server from current DB prices. */
public record Quote(Long shopId, String vertical, List<Line> lines, BigDecimal subtotal, Long discountId, String discountName, BigDecimal discount, String discountError,
                    BigDecimal platformFee, BigDecimal total, BigDecimal advance, BigDecimal remaining, Integer estimatedMinMinutes, Integer estimatedMaxMinutes, List<String> issues) {
  public record Line(Long cartItemId, Long productId, Long fileId, String description, BigDecimal unitPrice, int quantity, BigDecimal lineTotal, PrintConfigInput config, PriceBreakdown breakdown) {}
}
