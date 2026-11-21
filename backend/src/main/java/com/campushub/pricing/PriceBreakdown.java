package com.campushub.pricing;
import java.math.BigDecimal;
public record PriceBreakdown(int billablePages, int sheets, BigDecimal perCopy, BigDecimal total) {}
