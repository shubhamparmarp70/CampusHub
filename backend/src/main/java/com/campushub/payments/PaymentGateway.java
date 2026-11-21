package com.campushub.payments;
import java.math.BigDecimal;
public interface PaymentGateway {
  record Link(String url, String reference) {}
  /** status: SUCCESS, PENDING (a webhook will confirm), or FAILED */
  record RefundResult(String status, String reference) {}
  Link createPayment(String transactionId, BigDecimal amount, PaymentMethod method, long orderId);
  RefundResult refund(String originalReference, String refundTransactionId, BigDecimal amount);
  boolean verifySignature(String rawBody, String signature);
}
