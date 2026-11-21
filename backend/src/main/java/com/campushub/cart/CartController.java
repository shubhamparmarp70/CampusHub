package com.campushub.cart;
import com.campushub.common.ApiResponse;
import com.campushub.security.AuthUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/** Every response is a fresh server-side quote. The client never sends prices, discounts, totals or the advance amount. */
@RestController @RequestMapping("/api/student/cart")
public class CartController {
  public record AddProduct(@NotNull Long shopId, @NotNull Long productId, @Min(1) @Max(100) int quantity) {}
  public record Qty(@Min(0) @Max(100) int quantity) {}
  public record AddPrint(@NotNull Long shopId, @NotNull Long fileId, @Valid @NotNull PrintConfigInput config) {}
  public record EditPrint(@Valid @NotNull PrintConfigInput config) {}
  public record Code(@Size(max=40) String code) {}
  private final CartService cart; private final PricingEngine engine;
  public CartController(CartService c, PricingEngine e){ cart = c; engine = e; }
  @PostMapping("/reorder/{orderId}") ApiResponse<java.util.Map<String,Object>> reorder(@AuthenticationPrincipal AuthUser u, @PathVariable long orderId){ return ApiResponse.ok("Added to your cart.", cart.reorder(u.id(), orderId)); }
  @GetMapping ApiResponse<Quote> get(@AuthenticationPrincipal AuthUser u){ return ApiResponse.ok("OK", engine.quote(u.id(), false)); }
  @PostMapping("/items") ApiResponse<Quote> add(@AuthenticationPrincipal AuthUser u, @Valid @RequestBody AddProduct r){ return ApiResponse.ok("Added to cart.", cart.addProduct(u.id(), r.shopId(), r.productId(), r.quantity())); }
  @PatchMapping("/items/{id}") ApiResponse<Quote> qty(@AuthenticationPrincipal AuthUser u, @PathVariable long id, @Valid @RequestBody Qty r){ return ApiResponse.ok("Cart updated.", cart.setQuantity(u.id(), id, r.quantity())); }
  @PostMapping("/print-jobs") ApiResponse<Quote> addPrint(@AuthenticationPrincipal AuthUser u, @Valid @RequestBody AddPrint r) throws Exception { return ApiResponse.ok("Print job added.", cart.addPrintJob(u.id(), r.shopId(), r.fileId(), r.config())); }
  @PutMapping("/print-jobs/{id}") ApiResponse<Quote> editPrint(@AuthenticationPrincipal AuthUser u, @PathVariable long id, @Valid @RequestBody EditPrint r) throws Exception { return ApiResponse.ok("Print job updated.", cart.editPrintJob(u.id(), id, r.config())); }
  @DeleteMapping("/items/{id}") ApiResponse<Quote> remove(@AuthenticationPrincipal AuthUser u, @PathVariable long id){ return ApiResponse.ok("Removed.", cart.remove(u.id(), id)); }
  @DeleteMapping ApiResponse<Quote> clear(@AuthenticationPrincipal AuthUser u){ return ApiResponse.ok("Cart cleared.", cart.clear(u.id())); }
  @PutMapping("/discount") ApiResponse<Quote> discount(@AuthenticationPrincipal AuthUser u, @Valid @RequestBody Code r){ return ApiResponse.ok("Offer applied.", cart.setDiscount(u.id(), r.code())); }
}
