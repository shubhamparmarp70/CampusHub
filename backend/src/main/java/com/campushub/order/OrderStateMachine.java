package com.campushub.order;
import org.springframework.stereotype.Component;
/** Single gate for every status change. Services call this, then write an order_status_history row. */
@Component
public class OrderStateMachine {
  public OrderStatus transition(OrderStatus from, OrderStatus to){
    if (!from.canMoveTo(to)) throw new InvalidTransitionException(from, to);
    return to;
  }
}
