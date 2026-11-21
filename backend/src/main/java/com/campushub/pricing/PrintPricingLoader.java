package com.campushub.pricing;
import com.campushub.common.*;
import com.campushub.pricing.Enums.*;
import java.math.BigDecimal;
import java.util.*;
import org.springframework.stereotype.Component;
/** Loads a shop's rate card from print_pricing. Missing add-on prices default to zero; a missing page rate means that size/colour isn't offered. */
@Component
public class PrintPricingLoader {
  private final Db db;
  public PrintPricingLoader(Db db){ this.db = db; }
  public PrintPricing load(long shopId){
    Map<PageSize, Map<ColorMode, BigDecimal>> rates = new EnumMap<>(PageSize.class);
    Map<String, BigDecimal> add = new HashMap<>();
    for (var r : db.rows("SELECT kind, page_size, color_mode, price FROM print_pricing WHERE shop_id=?", shopId)) {
      String kind = (String) r.get("kind"); BigDecimal price = Db.bd(r.get("price"));
      if (kind.equals("PAGE_RATE")) rates.computeIfAbsent(PageSize.valueOf((String) r.get("page_size")), k -> new EnumMap<>(ColorMode.class)).put(ColorMode.valueOf((String) r.get("color_mode")), price);
      else add.put(kind, price);
    }
    DuplexRule rule = DuplexRule.valueOf(db.scalar(String.class, "SELECT duplex_rule FROM shops WHERE id=?", shopId).orElse("PER_PAGE"));
    BigDecimal z = BigDecimal.ZERO;
    return new PrintPricing(rates, rule, add.getOrDefault("DUPLEX_DISCOUNT", z), add.getOrDefault("BINDING_SPIRAL", z), add.getOrDefault("BINDING_TAPE", z), add.getOrDefault("LAMINATION", z), add.getOrDefault("STAPLING", z));
  }
}
