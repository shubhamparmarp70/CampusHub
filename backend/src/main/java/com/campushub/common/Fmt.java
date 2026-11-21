package com.campushub.common;
import java.math.BigDecimal;
public final class Fmt { private Fmt(){} public static String rs(BigDecimal b){ return "₹" + b.stripTrailingZeros().toPlainString(); } }
