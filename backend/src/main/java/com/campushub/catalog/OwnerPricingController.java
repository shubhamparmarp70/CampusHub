package com.campushub.catalog;
import com.campushub.common.*;
import com.campushub.pricing.*;
import com.campushub.pricing.Enums.*;
import com.campushub.security.AuthUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

/** The shop's printing rate card. Students never send prices; the server always reads these rows. */
@RestController @RequestMapping("/api/shop-owner/printing-prices")
public class OwnerPricingController {
  public record Rate(@NotNull PageSize size, @NotNull ColorMode color, @NotNull @DecimalMin("0.00") BigDecimal price) {}
  public record Req(@NotEmpty List<@Valid Rate> rates, @NotNull @DecimalMin("0.00") BigDecimal duplexDiscountPerPage, @NotNull @DecimalMin("0.00") BigDecimal spiralBinding,
    @NotNull @DecimalMin("0.00") BigDecimal tapeBinding, @NotNull @DecimalMin("0.00") BigDecimal laminationPerPage, @NotNull @DecimalMin("0.00") BigDecimal staplingPerCopy, @NotNull DuplexRule duplexRule) {}
  private static final String UP = "INSERT INTO print_pricing(shop_id, kind, page_size, color_mode, price) VALUES (?,?,?,?,?) AS n ON DUPLICATE KEY UPDATE price=n.price";
  private final Db db; private final ShopAccess access; private final PrintPricingLoader loader; private final AuditService audit;
  public OwnerPricingController(Db db, ShopAccess a, PrintPricingLoader l, AuditService au){ this.db = db; access = a; loader = l; audit = au; }

  @GetMapping ApiResponse<PrintPricing> get(@AuthenticationPrincipal AuthUser u){ return ApiResponse.ok("OK", loader.load(access.shopId(u))); }
  @Transactional @PutMapping ApiResponse<PrintPricing> put(@AuthenticationPrincipal AuthUser u, @Valid @RequestBody Req r){
    long shop = access.approvedShopId(u);
    for (Rate x : r.rates()) db.update(UP, shop, "PAGE_RATE", x.size().name(), x.color().name(), x.price());
    db.update(UP, shop, "DUPLEX_DISCOUNT", "ANY", "ANY", r.duplexDiscountPerPage());
    db.update(UP, shop, "BINDING_SPIRAL", "ANY", "ANY", r.spiralBinding());
    db.update(UP, shop, "BINDING_TAPE", "ANY", "ANY", r.tapeBinding());
    db.update(UP, shop, "LAMINATION", "ANY", "ANY", r.laminationPerPage());
    db.update(UP, shop, "STAPLING", "ANY", "ANY", r.staplingPerCopy());
    db.update("UPDATE shops SET duplex_rule=? WHERE id=?", r.duplexRule().name(), shop);
    audit.log(u.id(), u.email(), "PRINT_PRICES_CHANGED", "shop", String.valueOf(shop));
    return ApiResponse.ok("Printing prices saved.", loader.load(shop));
  }
}
