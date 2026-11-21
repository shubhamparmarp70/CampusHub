package com.campushub.payments;
import com.campushub.common.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Set;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
public class PaymentWebhookController {
  private static final Set<String> PROVIDERS = Set.of("phonepe", "googlepay", "sandbox");
  private final PaymentGateway gateway; private final PaymentService payments; private final AuditService audit; private final ObjectMapper om;
  public PaymentWebhookController(PaymentGateway g, PaymentService p, AuditService a, ObjectMapper om){ gateway = g; payments = p; audit = a; this.om = om; }

  /** Public URL, protected by signature verification over the raw body. A payment is only ever marked SUCCESS from here (or the sandbox helper), never from the app. */
  @PostMapping("/api/payments/webhook/{provider}")
  ResponseEntity<ApiResponse<Void>> hook(@PathVariable String provider, @RequestHeader("X-Signature") String signature, @RequestBody String raw) throws Exception {
    if (!PROVIDERS.contains(provider)) return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.fail("Unknown provider.", null));
    if (!gateway.verifySignature(raw, signature)) { audit.log(null, null, "WEBHOOK_BAD_SIGNATURE", "webhook", provider); return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiResponse.fail("Bad signature.", null)); }
    WebhookEvent ev = om.readValue(raw, WebhookEvent.class);
    if (ev.eventId() == null || ev.eventId().isBlank() || ev.transactionId() == null || ev.status() == null) throw BusinessException.badRequest("BAD_EVENT", "Malformed event.");
    payments.handleWebhook(provider, ev, raw);
    return ResponseEntity.ok(ApiResponse.ok("OK", null));
  }
}
