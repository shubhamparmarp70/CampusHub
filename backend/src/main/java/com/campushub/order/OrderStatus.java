package com.campushub.order;
import java.util.*;
import static com.campushub.order.OrderStatus.*;

public enum OrderStatus {
  CREATED, PAYMENT_PENDING, PARTIALLY_PAID, PLACED, ACCEPTED, QUEUED, PROCESSING, PRINTING, BINDING, READY, COLLECTED, CANCELLED, REFUND_PENDING, REFUNDED, FAILED;

  private static final Map<OrderStatus, Set<OrderStatus>> NEXT = new EnumMap<>(OrderStatus.class);
  static {
    NEXT.put(CREATED,         EnumSet.of(PAYMENT_PENDING, CANCELLED, FAILED));
    NEXT.put(PAYMENT_PENDING, EnumSet.of(PARTIALLY_PAID, CANCELLED, FAILED));
    NEXT.put(PARTIALLY_PAID,  EnumSet.of(PLACED, REFUND_PENDING));        // advance verified -> placed
    NEXT.put(PLACED,          EnumSet.of(ACCEPTED, REFUND_PENDING));      // owner rejects/student cancels -> refund
    NEXT.put(ACCEPTED,        EnumSet.of(QUEUED, PROCESSING, REFUND_PENDING));
    NEXT.put(QUEUED,          EnumSet.of(PROCESSING, REFUND_PENDING));
    NEXT.put(PROCESSING,      EnumSet.of(PRINTING, BINDING, READY, REFUND_PENDING)); // food goes PROCESSING -> READY
    NEXT.put(PRINTING,        EnumSet.of(BINDING, READY));
    NEXT.put(BINDING,         EnumSet.of(READY));
    NEXT.put(READY,           EnumSet.of(COLLECTED, REFUND_PENDING));     // cancelling at READY only if the owner allows it
    NEXT.put(REFUND_PENDING,  EnumSet.of(REFUNDED));
    for (OrderStatus s : List.of(COLLECTED, CANCELLED, REFUNDED, FAILED)) NEXT.put(s, EnumSet.noneOf(OrderStatus.class));
  }
  public boolean canMoveTo(OrderStatus to){ return NEXT.get(this).contains(to); }
  public Set<OrderStatus> allowedNext(){ return Collections.unmodifiableSet(NEXT.get(this)); }
  public boolean isTerminal(){ return NEXT.get(this).isEmpty(); }
}
