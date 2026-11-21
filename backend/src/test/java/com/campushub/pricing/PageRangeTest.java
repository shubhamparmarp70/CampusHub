package com.campushub.pricing;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
class PageRangeTest {
  @Test void blank_means_all_pages(){ assertEquals(30, PageRange.count(null, 30)); assertEquals(30, PageRange.count("  ", 30)); }
  @Test void single_pages_and_ranges(){ assertEquals(1, PageRange.count("7", 30)); assertEquals(5, PageRange.count("1-5", 30)); assertEquals(9, PageRange.count("1-5, 8, 10-12", 30)); }
  @Test void overlaps_count_once(){ assertEquals(6, PageRange.count("1-5, 3-6", 30)); }
  @Test void rejects_garbage(){
    for (String bad : new String[]{"0", "5-3", "1-31", "a", "1,,2", "1-", "-5", "1;2"}) assertThrows(IllegalArgumentException.class, () -> PageRange.count(bad, 30), bad);
  }
}
