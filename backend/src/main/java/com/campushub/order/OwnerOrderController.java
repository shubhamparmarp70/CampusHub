package com.campushub.order;
import com.campushub.catalog.ShopAccess;
import com.campushub.common.ApiResponse;
import com.campushub.payments.PaymentService;
import com.campushub.security.AuthUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/shop-owner")
public class OwnerOrderController {
  public record StatusReq(@NotNull OrderStatus status, @Size(max=255) String remarks) {}
  public record Reject(@NotBlank @Size(max=255) String reason) {}
  public record Otp(@NotBlank @Pattern(regexp="\\d{6}", message="The OTP has 6 digits.") String otp) {}
  private final OrderService orders; private final PaymentService payments; private final ShopAccess access;
  public OwnerOrderController(OrderService o, PaymentService p, ShopAccess a){ orders = o; payments = p; access = a; }
  @GetMapping("/orders") ApiResponse<List<Map<String,Object>>> list(@AuthenticationPrincipal AuthUser u, @RequestParam(required=false) String status){ return ApiResponse.ok("OK", orders.ownerOrders(u, status)); }
  @GetMapping("/orders/{id}") ApiResponse<Map<String,Object>> get(@AuthenticationPrincipal AuthUser u, @PathVariable long id){ return ApiResponse.ok("OK", orders.ownerOrder(u, id, null)); }
  @GetMapping("/orders/by-code/{code}") ApiResponse<Map<String,Object>> byCode(@AuthenticationPrincipal AuthUser u, @PathVariable String code){ return ApiResponse.ok("OK", orders.ownerOrder(u, -1L, code)); }
  @PostMapping("/orders/{id}/status") ApiResponse<Void> status(@AuthenticationPrincipal AuthUser u, @PathVariable long id, @Valid @RequestBody StatusReq r){ orders.setStatus(u, id, r.status(), r.remarks()); return ApiResponse.ok("Order updated.", null); }
  @PostMapping("/orders/{id}/reject") ApiResponse<Void> reject(@AuthenticationPrincipal AuthUser u, @PathVariable long id, @Valid @RequestBody Reject r){ orders.reject(u, id, r.reason()); return ApiResponse.ok("Order rejected. The student's money is being refunded.", null); }
  @PostMapping("/orders/{id}/verify-pickup") ApiResponse<Map<String,Object>> verify(@AuthenticationPrincipal AuthUser u, @PathVariable long id, @Valid @RequestBody Otp r){ return ApiResponse.ok("Order handed over.", orders.verifyPickup(u, id, r.otp())); }
  @GetMapping("/payments") ApiResponse<List<Map<String,Object>>> pay(@AuthenticationPrincipal AuthUser u){ return ApiResponse.ok("OK", payments.forShop(access.shopId(u))); }
}
