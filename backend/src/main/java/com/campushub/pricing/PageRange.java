package com.campushub.pricing;
import java.util.BitSet;
/** Parses "1-5, 8, 10-12". Blank means every page. Overlaps are counted once. */
public final class PageRange {
  private PageRange(){}
  public static int count(String range, int totalPages){
    if (totalPages < 1) throw new IllegalArgumentException("Document has no pages.");
    if (range == null || range.isBlank()) return totalPages;
    BitSet set = new BitSet(totalPages + 1);
    for (String part : range.split(",")) {
      String p = part.trim();
      if (!p.matches("\\d+(-\\d+)?")) throw new IllegalArgumentException("Page range must look like 1-5, 8.");
      String[] ab = p.split("-");
      int a = Integer.parseInt(ab[0]), b = ab.length > 1 ? Integer.parseInt(ab[1]) : a;
      if (a < 1 || b < a || b > totalPages) throw new IllegalArgumentException("Page range is outside the document (1–" + totalPages + ").");
      set.set(a, b + 1);
    }
    return set.cardinality();
  }
}
