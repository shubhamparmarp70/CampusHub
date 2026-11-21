package com.campushub.catalog;
import com.campushub.common.*;
import com.campushub.security.AuthUser;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/shop-owner/discounts")
public class OwnerDiscountController {
  public record Req(@NotBlank @Size(max=120) String name, @Size(max=255) String description, @Size(max=40) String code, @NotBlank @Pattern(regexp="PERCENT|FIXED") String type,
    @NotNull @DecimalMin("0.01") BigDecimal value, @DecimalMin("0.00") BigDecimal minOrderAmount, List<String> appliesTo, @NotNull LocalDateTime startsAt, @NotNull LocalDateTime endsAt, @Min(1) Integer maxUsage) {}
  public record ActiveReq(boolean active) {}
  private final Db db; private final ShopAccess access; private final AuditService audit; private final ObjectMapper om;
  public OwnerDiscountController(Db db, ShopAccess a, AuditService au, ObjectMapper om){ this.db = db; access = a; audit = au; this.om = om; }

  @GetMapping ApiResponse<List<Map<String,Object>>> list(@AuthenticationPrincipal AuthUser u){
    return ApiResponse.ok("OK", db.rows("SELECT id, name, description, code, type, value, min_order_amount, applies_to, starts_at, ends_at, max_usage, used_count, active FROM discounts WHERE shop_id=? ORDER BY id DESC", access.shopId(u))); }
  @PostMapping ResponseEntity<ApiResponse<Long>> create(@AuthenticationPrincipal AuthUser u, @Valid @RequestBody Req r) throws Exception {
    long shop = access.approvedShopId(u);
    if (!r.endsAt().isAfter(r.startsAt())) throw BusinessException.badRequest("BAD_DATES", "The end date must be after the start date.");
    if (r.type().equals("PERCENT") && r.value().compareTo(BigDecimal.valueOf(100)) > 0) throw BusinessException.badRequest("BAD_VALUE", "A percentage can't be more than 100.");
    String applies = null;
    if (r.appliesTo() != null && !r.appliesTo().isEmpty()) {
      for (String a : r.appliesTo()) { if (a.equals("PRINTING")) continue; try { access.requireProduct(shop, Long.parseLong(a)); } catch (NumberFormatException e) { throw BusinessException.badRequest("BAD_TARGET", "Offers can target product ids or PRINTING."); } }
      applies = om.writeValueAsString(r.appliesTo());
    }
    String code = r.code() == null || r.code().isBlank() ? Tokens.random(5).replaceAll("[^A-Za-z0-9]", "X").toUpperCase() : r.code().trim().toUpperCase();
    long id = db.insert("INSERT INTO discounts(shop_id, name, description, code, type, value, min_order_amount, applies_to, starts_at, ends_at, max_usage) VALUES (?,?,?,?,?,?,?,CAST(? AS JSON),?,?,?)",
      shop, r.name().trim(), r.description(), code, r.type(), r.value(), r.minOrderAmount() == null ? BigDecimal.ZERO : r.minOrderAmount(), applies, r.startsAt(), r.endsAt(), r.maxUsage());
    audit.log(u.id(), u.email(), "DISCOUNT_CREATED", "discount", String.valueOf(id));
    return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok("Offer created. Code: " + code, id));
  }
  @PatchMapping("/{id}/active") ApiResponse<Void> active(@AuthenticationPrincipal AuthUser u, @PathVariable long id, @RequestBody ActiveReq r){
    if (db.update("UPDATE discounts SET active=? WHERE id=? AND shop_id=?", r.active(), id, access.approvedShopId(u)) == 0) throw BusinessException.notFound("Offer not found.");
    return ApiResponse.ok(r.active() ? "Offer is on." : "Offer is off.", null); }
}
