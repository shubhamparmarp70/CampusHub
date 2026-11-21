package com.campushub.cart;
import com.campushub.catalog.DiscountService;
import com.campushub.common.*;
import com.campushub.order.PrepTimeEstimator;
import com.campushub.pricing.*;
import com.campushub.pricing.Enums.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class PricingEngine {
  private final Settings settings; private final Db db; private final PrintPriceCalculator calc; private final PrintPricingLoader loader; private final DiscountService discounts; private final PrepTimeEstimator est; private final ObjectMapper om;
  @Value("${app.money.advance-percent}") private int advancePercent;
  public PricingEngine(Db db, PrintPriceCalculator c, PrintPricingLoader l, DiscountService d, PrepTimeEstimator e, ObjectMapper om, Settings settings){ this.settings = settings; this.db = db; calc = c; loader = l; discounts = d; est = e; this.om = om; }

  /** Prices one print configuration; turns calculator errors into friendly 400s. */
  public PriceBreakdown priceConfig(PrintPricing pricing, int pages, PrintConfigInput c){
    try { return calc.calculate(pricing, new PrintConfig(pages, c.pageRange(), c.size(), c.color(), c.sides(), c.copies(), c.binding(), c.lamination(), c.stapling())); }
    catch (IllegalArgumentException e) { throw BusinessException.badRequest("INVALID_PRINT_CONFIG", e.getMessage()); }
  }
  public static String describe(String file, PrintConfigInput c, PriceBreakdown b){
    return "Print: " + file + ", " + c.size() + " " + (c.color() == ColorMode.COLOR ? "colour" : "B&W") + ", " + (c.sides() == Sides.DOUBLE ? "double-sided" : "single-sided") + ", "
      + b.billablePages() + " pages × " + c.copies() + (c.copies() > 1 ? " copies" : " copy") + (c.binding() != Binding.NONE ? ", " + c.binding().name().toLowerCase() + " binding" : "") + (c.lamination() ? ", laminated" : "") + (c.stapling() ? ", stapled" : "");
  }
  private static void problem(boolean strict, List<String> issues, String code, String msg){ if (strict) throw BusinessException.conflict(code, msg); issues.add(msg); }

  public Quote quote(long studentId, boolean strict){
    var cart = db.one("SELECT id, shop_id, discount_code FROM carts WHERE student_id=?", studentId).orElse(null);
    List<String> issues = new ArrayList<>(); List<Quote.Line> lines = new ArrayList<>(); List<DiscountService.Line> dl = new ArrayList<>();
    if (cart == null) return new Quote(null, null, lines, BigDecimal.ZERO, null, null, BigDecimal.ZERO, null, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, null, null, issues);
    long shopId = Db.l(cart.get("shop_id"));
    String vertical = db.scalar(String.class, "SELECT vertical FROM shops WHERE id=?", shopId).orElseThrow();
    PrintPricing pricing = null; BigDecimal sub = BigDecimal.ZERO; int sheets = 0; boolean binding = false;
    for (var it : db.rows("SELECT id, product_id, quantity, file_id, print_config FROM cart_items WHERE cart_id=? ORDER BY id", cart.get("id"))) {
      long itemId = Db.l(it.get("id"));
      if (it.get("product_id") != null) {
        long pid = Db.l(it.get("product_id")); int qty = Db.i(it.get("quantity"));
        var p = db.one("SELECT name, kind, selling_price FROM products WHERE id=? AND shop_id=? AND active=TRUE", pid, shopId).orElse(null);
        if (p == null) { problem(strict, issues, "PRODUCT_UNAVAILABLE", "An item in your cart isn't available any more. Remove it to continue."); continue; }
        if (p.get("kind").equals("ITEM")) {
          int stock = db.scalar(Integer.class, "SELECT quantity FROM inventory WHERE product_id=?", pid).orElse(0);
          if (stock < qty) { problem(strict, issues, "INSUFFICIENT_STOCK", stock == 0 ? p.get("name") + " is out of stock." : "Only " + stock + " left of " + p.get("name") + "."); }
        }
        BigDecimal unit = Db.bd(p.get("selling_price")), total = unit.multiply(BigDecimal.valueOf(qty)).setScale(2, RoundingMode.HALF_UP);
        lines.add(new Quote.Line(itemId, pid, null, (String) p.get("name"), unit, qty, total, null, null)); dl.add(new DiscountService.Line(pid, false, total)); sub = sub.add(total);
      } else {
        var f = db.one("SELECT original_name, page_count FROM print_files WHERE id=? AND owner_id=? AND deleted_at IS NULL", it.get("file_id"), studentId).orElse(null);
        if (f == null) { problem(strict, issues, "FILE_UNAVAILABLE", "A document in your cart has expired. Upload it again."); continue; }
        try {
          PrintConfigInput cfg = om.readValue(it.get("print_config").toString(), PrintConfigInput.class);
          if (pricing == null) pricing = loader.load(shopId);
          PriceBreakdown b = priceConfig(pricing, Db.i(f.get("page_count")), cfg);
          lines.add(new Quote.Line(itemId, null, Db.l(it.get("file_id")), describe((String) f.get("original_name"), cfg, b), b.total(), 1, b.total(), cfg, b)); dl.add(new DiscountService.Line(null, true, b.total()));
          sub = sub.add(b.total()); sheets += b.sheets() * cfg.copies(); binding |= cfg.binding() != Binding.NONE;
        } catch (BusinessException e) { problem(strict, issues, e.code(), e.getMessage()); }
        catch (Exception e) { problem(strict, issues, "INVALID_PRINT_CONFIG", "A print job in your cart is invalid. Edit or remove it."); }
      }
    }
    DiscountService.Applied ap = new DiscountService.Applied(null, BigDecimal.ZERO, null); String derr = null;
    try { ap = discounts.evaluate(shopId, (String) cart.get("discount_code"), dl, sub); }
    catch (BusinessException e) { if (strict) throw e; derr = e.getMessage(); }
    BigDecimal afterDiscount = Money.applyDiscount(sub, ap.amount());
    BigDecimal fee = PlatformFee.compute(afterDiscount, settings.getDecimal("platform_fee_percent", BigDecimal.ZERO), settings.getDecimal("platform_fee_min", BigDecimal.ZERO), settings.getDecimal("platform_fee_max", BigDecimal.ZERO));
    BigDecimal total = afterDiscount.add(fee), adv = BigDecimal.ZERO, rem = BigDecimal.ZERO;      // the 50% advance is taken on the full payable amount, fee included
    if (total.signum() > 0) { var s = Money.advanceSplit(total, advancePercent); adv = s.advance(); rem = s.remaining(); }
    var e = est.estimate(shopId, vertical, lines.size(), sheets, binding);
    return new Quote(shopId, vertical, lines, sub, ap.discountId(), ap.name(), ap.amount(), derr, fee, total, adv, rem, e.min(), e.max(), issues);
  }
}
