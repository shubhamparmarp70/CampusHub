package com.campushub.catalog;
import com.campushub.common.*;
import com.campushub.security.AuthUser;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.*;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/shop-owner/shop")
public class OwnerShopController {
  public record CreateShop(@NotBlank @Pattern(regexp="FOOD|DESK") String vertical, @NotBlank @Size(max=140) String name, String description, @Size(max=255) String address, String phone) {}
  public record UpdateShop(@NotBlank @Size(max=140) String name, String description, @Size(max=255) String address, String phone, String openingHours, String logoKey) {}
  public record Status(@NotBlank @Pattern(regexp="OPEN|CLOSED") String openStatus) {}
  /** Percent of the paid amount refunded when a student cancels, by order stage. */
  public record Policy(@Min(0) @Max(100) int beforeProcessingPercent, @Min(0) @Max(100) int duringProcessingPercent, @Min(0) @Max(100) int afterPrintingPercent, boolean readyAllowed) {}
  public record Duplex(@NotBlank @Pattern(regexp="PER_PAGE|PER_SHEET") String duplexRule) {}
  private final Db db; private final ShopAccess access; private final AuditService audit; private final ObjectMapper om;
  public OwnerShopController(Db db, ShopAccess a, AuditService au, ObjectMapper om){ this.db = db; access = a; audit = au; this.om = om; }

  @PostMapping ResponseEntity<ApiResponse<Long>> create(@AuthenticationPrincipal AuthUser u, @Valid @RequestBody CreateShop r){
    if (db.count("SELECT COUNT(*) FROM shops WHERE owner_id=?", u.id()) > 0) throw BusinessException.conflict("SHOP_EXISTS", "You already have a shop.");
    long id = db.insert("INSERT INTO shops(owner_id, vertical, name, description, address, phone) VALUES (?,?,?,?,?,?)", u.id(), r.vertical(), r.name().trim(), r.description(), r.address(), r.phone());
    audit.log(u.id(), u.email(), "SHOP_CREATED", "shop", String.valueOf(id));
    return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok("Shop created. It goes live once the platform team approves it.", id));
  }
  @GetMapping ApiResponse<Map<String,Object>> mine(@AuthenticationPrincipal AuthUser u){
    return ApiResponse.ok("OK", db.one("SELECT id, vertical, name, description, address, phone, logo_key, opening_hours, approval, open_status, duplex_rule, cancellation_policy, avg_rating, rating_count FROM shops WHERE id=?", access.shopId(u)).orElseThrow()); }
  @PutMapping ApiResponse<Void> update(@AuthenticationPrincipal AuthUser u, @Valid @RequestBody UpdateShop r){
    db.update("UPDATE shops SET name=?, description=?, address=?, phone=?, opening_hours=CAST(? AS JSON), logo_key=? WHERE id=?", r.name().trim(), r.description(), r.address(), r.phone(), r.openingHours(), r.logoKey(), access.shopId(u));
    return ApiResponse.ok("Shop updated.", null); }
  @PatchMapping("/status") ApiResponse<Void> status(@AuthenticationPrincipal AuthUser u, @Valid @RequestBody Status r){
    long id = access.shopId(u);
    if (r.openStatus().equals("OPEN") && db.count("SELECT COUNT(*) FROM shops WHERE id=? AND approval='APPROVED'", id) == 0) throw BusinessException.conflict("NOT_APPROVED", "Your shop needs platform approval before it can open.");
    db.update("UPDATE shops SET open_status=? WHERE id=?", r.openStatus(), id); return ApiResponse.ok("Shop is now " + r.openStatus().toLowerCase() + ".", null); }
  @PutMapping("/cancellation-policy") ApiResponse<Void> policy(@AuthenticationPrincipal AuthUser u, @Valid @RequestBody Policy p) throws Exception {
    db.update("UPDATE shops SET cancellation_policy=CAST(? AS JSON) WHERE id=?", om.writeValueAsString(p), access.shopId(u));
    audit.log(u.id(), u.email(), "POLICY_CHANGED", "shop", String.valueOf(access.shopId(u))); return ApiResponse.ok("Cancellation policy saved.", null); }
  @PutMapping("/duplex-rule") ApiResponse<Void> duplex(@AuthenticationPrincipal AuthUser u, @Valid @RequestBody Duplex d){
    db.update("UPDATE shops SET duplex_rule=? WHERE id=?", d.duplexRule(), access.shopId(u)); return ApiResponse.ok("Double-sided pricing rule saved.", null); }
}
