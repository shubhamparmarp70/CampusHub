package com.campushub.cart;
import com.campushub.common.ApiResponse;
import com.campushub.pricing.PriceBreakdown;
import com.campushub.security.AuthUser;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/** Prices a print configuration with the shop's real rate card without touching the cart, so the app can show the price while the student chooses options. */
@RestController @RequestMapping("/api/student")
public class PrintPreviewController {
  private final CartService cart;
  public PrintPreviewController(CartService cart){ this.cart = cart; }
  @PostMapping("/print-preview")
  ApiResponse<PriceBreakdown> preview(@AuthenticationPrincipal AuthUser u, @Valid @RequestBody CartController.AddPrint r){
    return ApiResponse.ok("OK", cart.preview(u.id(), r.shopId(), r.fileId(), r.config()));
  }
}
