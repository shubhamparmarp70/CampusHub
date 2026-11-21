package com.campushub.payments;
import com.campushub.common.Tokens;
import java.math.BigDecimal;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.env.*;
import org.springframework.stereotype.Component;

/** Development gateway. Refuses to start under the "prod" profile. Replace with real PhonePe / Google Pay adapters implementing PaymentGateway. */
@Component
@ConditionalOnProperty(name = "app.payments.gateway", havingValue = "sandbox", matchIfMissing = true)
public class SandboxGateway implements PaymentGateway {
  private final String secret, baseUrl;
  public SandboxGateway(Environment env, @Value("${app.payments.webhook-secret}") String secret, @Value("${app.base-url}") String baseUrl){
    if (env.acceptsProfiles(Profiles.of("prod"))) throw new IllegalStateException("The sandbox payment gateway must not run in production.");
    this.secret = secret; this.baseUrl = baseUrl;
  }
  @Override public Link createPayment(String txn, BigDecimal amount, PaymentMethod m, long orderId){ return new Link(baseUrl + "/sandbox-pay?txn=" + txn, "SBX-" + txn); }
  @Override public RefundResult refund(String ref, String refundTxn, BigDecimal amount){ return new RefundResult("SUCCESS", "SBXR-" + refundTxn); }
  @Override public boolean verifySignature(String raw, String sig){ return sig != null && Tokens.safeEquals(Tokens.hmac(secret, raw), sig); }
}
