package com.campushub.catalog;
import com.campushub.common.*;
import com.campushub.security.AuthUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.util.*;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/shop-owner")
public class OwnerProductController {
  public record ProductReq(@NotNull Long categoryId, @NotBlank @Size(max=140) String name, String description, @Pattern(regexp="ITEM|SERVICE") String kind, @Size(max=60) String sku,
    @NotNull @DecimalMin("0.00") BigDecimal sellingPrice, @DecimalMin("0.00") BigDecimal costPrice, @Min(0) Integer initialStock, @Min(0) Integer minStock, String imageKey) {}
  public record ActiveReq(boolean active) {}
  public record AdjustReq(@NotBlank @Pattern(regexp="ADD|REDUCE|ADJUST|DAMAGED") String type, @Min(0) int quantity, @Size(max=255) String note) {}
  public record MinReq(@Min(0) int minLevel) {}
  private final Db db; private final ShopAccess access; private final AuditService audit; private final InventoryService inv;
  public OwnerProductController(Db db, ShopAccess a, AuditService au, InventoryService inv){ this.db = db; access = a; audit = au; this.inv = inv; }

  private void checkCategory(long shopId, long categoryId){
    if (db.count("SELECT COUNT(*) FROM categories c JOIN shops s ON s.vertical=c.vertical WHERE c.id=? AND c.active=TRUE AND s.id=?", categoryId, shopId) == 0) throw BusinessException.badRequest("BAD_CATEGORY", "Choose a category that fits your shop type."); }

  @GetMapping("/products") ApiResponse<List<Map<String,Object>>> list(@AuthenticationPrincipal AuthUser u){
    return ApiResponse.ok("OK", db.rows("SELECT p.id, p.name, p.kind, p.description, p.sku, p.selling_price, p.cost_price, p.active, p.category_id, p.updated_at, i.quantity, i.min_level FROM products p LEFT JOIN inventory i ON i.product_id=p.id WHERE p.shop_id=? ORDER BY p.name", access.shopId(u))); }

  @Transactional @PostMapping("/products") ResponseEntity<ApiResponse<Long>> create(@AuthenticationPrincipal AuthUser u, @Valid @RequestBody ProductReq r){
    long shop = access.approvedShopId(u); checkCategory(shop, r.categoryId());
    String kind = r.kind() == null ? "ITEM" : r.kind();
    long id = db.insert("INSERT INTO products(shop_id, category_id, kind, name, description, image_key, sku, selling_price, cost_price) VALUES (?,?,?,?,?,?,?,?,?)", shop, r.categoryId(), kind, r.name().trim(), r.description(), r.imageKey(), r.sku(), r.sellingPrice(), r.costPrice());
    if (kind.equals("ITEM")) {
      int stock = r.initialStock() == null ? 0 : r.initialStock();
      db.update("INSERT INTO inventory(product_id, quantity, min_level) VALUES (?,?,?)", id, 0, r.minStock() == null ? 5 : r.minStock());
      if (stock > 0) inv.apply(id, "ADD", old -> old + stock, null, "Opening stock", u.id());
    }
    audit.log(u.id(), u.email(), "PRODUCT_CREATED", "product", String.valueOf(id));
    return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok("Product added.", id));
  }
  @Transactional @PutMapping("/products/{id}") ApiResponse<Void> update(@AuthenticationPrincipal AuthUser u, @PathVariable long id, @Valid @RequestBody ProductReq r){
    long shop = access.approvedShopId(u); access.requireProduct(shop, id); checkCategory(shop, r.categoryId());
    BigDecimal old = db.scalar(BigDecimal.class, "SELECT selling_price FROM products WHERE id=?", id).orElseThrow();
    db.update("UPDATE products SET category_id=?, name=?, description=?, image_key=?, sku=?, selling_price=?, cost_price=? WHERE id=?", r.categoryId(), r.name().trim(), r.description(), r.imageKey(), r.sku(), r.sellingPrice(), r.costPrice(), id);
    if (old.compareTo(r.sellingPrice()) != 0) audit.log(u.id(), u.email(), "PRICE_CHANGED", "product", id + ": " + old + " -> " + r.sellingPrice());
    return ApiResponse.ok("Product updated.", null); }
  @PatchMapping("/products/{id}/active") ApiResponse<Void> active(@AuthenticationPrincipal AuthUser u, @PathVariable long id, @RequestBody ActiveReq r){
    access.requireProduct(access.approvedShopId(u), id); db.update("UPDATE products SET active=? WHERE id=?", r.active(), id); return ApiResponse.ok(r.active() ? "Product is live." : "Product hidden.", null); }

  @GetMapping("/inventory") ApiResponse<List<Map<String,Object>>> stock(@AuthenticationPrincipal AuthUser u, @RequestParam(defaultValue="false") boolean lowOnly){
    return ApiResponse.ok("OK", db.rows("SELECT p.id productId, p.name, p.sku, i.quantity, i.min_level, (i.quantity<=i.min_level) AS low FROM inventory i JOIN products p ON p.id=i.product_id WHERE p.shop_id=?" + (lowOnly ? " AND i.quantity<=i.min_level" : "") + " ORDER BY low DESC, p.name", access.shopId(u))); }
  @Transactional @PostMapping("/inventory/{productId}/adjust") ApiResponse<Integer> adjust(@AuthenticationPrincipal AuthUser u, @PathVariable long productId, @Valid @RequestBody AdjustReq r){
    access.requireProduct(access.approvedShopId(u), productId); int q = r.quantity();
    int after = switch (r.type()) { case "ADD" -> inv.apply(productId, "ADD", o -> o + q, null, r.note(), u.id());
      case "REDUCE" -> inv.apply(productId, "REDUCE", o -> o - q, null, r.note(), u.id());
      case "DAMAGED" -> inv.apply(productId, "DAMAGED", o -> o - q, null, r.note(), u.id());
      default -> inv.apply(productId, "ADJUST", o -> q, null, r.note(), u.id()); };
    audit.log(u.id(), u.email(), "STOCK_" + r.type(), "product", productId + " -> " + after);
    return ApiResponse.ok("Stock updated.", after); }
  @PutMapping("/inventory/{productId}/min") ApiResponse<Void> min(@AuthenticationPrincipal AuthUser u, @PathVariable long productId, @Valid @RequestBody MinReq r){
    access.requireProduct(access.approvedShopId(u), productId); db.update("UPDATE inventory SET min_level=? WHERE product_id=?", r.minLevel(), productId); return ApiResponse.ok("Minimum stock saved.", null); }
  @GetMapping("/inventory/{productId}/history") ApiResponse<List<Map<String,Object>>> history(@AuthenticationPrincipal AuthUser u, @PathVariable long productId){
    access.requireProduct(access.shopId(u), productId);
    return ApiResponse.ok("OK", db.rows("SELECT txn_type, quantity_change, quantity_after, note, created_at FROM inventory_transactions WHERE product_id=? ORDER BY id DESC LIMIT 100", productId)); }
}
