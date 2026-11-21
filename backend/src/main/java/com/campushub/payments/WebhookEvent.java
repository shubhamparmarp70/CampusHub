package com.campushub.payments;
import java.math.BigDecimal;
/** Normalised gateway callback. status: SUCCESS | FAILED. type: PAYMENT (default) | REFUND. */
public record WebhookEvent(String eventId, String transactionId, String gatewayReference, String status, BigDecimal amount, String type) {}
