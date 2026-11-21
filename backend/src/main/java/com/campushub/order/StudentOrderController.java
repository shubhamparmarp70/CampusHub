package com.campushub.order;
import com.campushub.common.ApiResponse;
import com.campushub.payments.PaymentService;
import com.campushub.security.AuthUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.*;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/student")
public class StudentOrderController {
  public record Pay(@NotBlank @Pattern(regexp="PHONEPE|GOOGLE_PAY", message="Pay with PhonePe or Google Pay.") String method) {}
  public record Cancel(@Size(max=255) String reason) {}
  private final OrderService orders; private final PaymentService payments;
  public StudentOrderController(OrderService o, PaymentService p){ orders = o; payments = p; }
  @PostMapping("/orders/checkout") ResponseEntity<ApiResponse<Map<String,Object>>> checkout(@AuthenticationPrincipal AuthUser u, @Valid @RequestBody Pay r){ return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok("Order created. Pay the 50% advance to place it.", orders.checkout(u, r.method()))); }
  @GetMapping("/orders") ApiResponse<List<Map<String,Object>>> list(@AuthenticationPrincipal AuthUser u){ return ApiResponse.ok("OK", orders.studentOrders(u.id())); }
  @GetMapping("/orders/{id}") ApiResponse<Map<String,Object>> get(@AuthenticationPrincipal AuthUser u, @PathVariable long id){ return ApiResponse.ok("OK", orders.studentOrder(u.id(), id)); }
  @PostMapping("/orders/{id}/pay") ApiResponse<Map<String,Object>> pay(@AuthenticationPrincipal AuthUser u, @PathVariable long id, @Valid @RequestBody Pay r){ return ApiResponse.ok("Continue in your payment app.", orders.pay(u, id, r.method())); }
  @PostMapping("/orders/{id}/cancel") ApiResponse<Map<String,Object>> cancel(@AuthenticationPrincipal AuthUser u, @PathVariable long id, @Valid @RequestBody(required=false) Cancel r){ return ApiResponse.ok("Cancellation processed.", orders.cancel(u, id, r == null ? null : r.reason())); }
  @PostMapping("/orders/{id}/otp") ApiResponse<Void> otp(@AuthenticationPrincipal AuthUser u, @PathVariable long id){ orders.resendOtp(u.id(), id); return ApiResponse.ok("A new OTP was sent to your notifications.", null); }
  @GetMapping("/payments") ApiResponse<List<Map<String,Object>>> payments(@AuthenticationPrincipal AuthUser u){ return ApiResponse.ok("OK", payments.forStudent(u.id())); }
}
