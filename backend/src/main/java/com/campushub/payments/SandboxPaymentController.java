package com.campushub.payments;
import com.campushub.common.*;
import com.campushub.security.AuthUser;
import java.math.BigDecimal;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/** Dev only (exists only with the sandbox gateway): lets a student "complete" their own pending payment so you can test the whole flow without PhonePe / Google Pay. */
@RestController
@ConditionalOnProperty(name = "app.payments.gateway", havingValue = "sandbox", matchIfMissing = true)
public class SandboxPaymentController {
  private final Db db; private final PaymentService payments;
  public SandboxPaymentController(Db db, PaymentService p){ this.db = db; payments = p; }
  @PostMapping("/api/payments/sandbox/{txn}/{outcome}")
  ApiResponse<Void> settle(@AuthenticationPrincipal AuthUser u, @PathVariable String txn, @PathVariable String outcome){
    if (!outcome.equals("succeed") && !outcome.equals("fail")) throw BusinessException.badRequest("BAD_OUTCOME", "Use succeed or fail.");
    BigDecimal amount = db.scalar(BigDecimal.class, "SELECT p.amount FROM payments p JOIN orders o ON o.id=p.order_id WHERE p.transaction_id=? AND o.student_id=?", txn, u.id()).orElseThrow(() -> BusinessException.notFound("Payment not found."));
    String id = "SBX-" + txn + "-" + outcome;
    payments.handleWebhook("sandbox", new WebhookEvent(id, txn, "SBX-" + txn, outcome.equals("succeed") ? "SUCCESS" : "FAILED", amount, "PAYMENT"), id);
    return ApiResponse.ok(outcome.equals("succeed") ? "Payment confirmed (sandbox)." : "Payment marked failed (sandbox).", null);
  }
}
