package com.campushub.order;
import com.campushub.common.BusinessException;
public class InvalidTransitionException extends BusinessException {
  public InvalidTransitionException(OrderStatus from, OrderStatus to){
    super(org.springframework.http.HttpStatus.CONFLICT, "INVALID_ORDER_STATE", "This order can't move from " + from + " to " + to + ".");
  }
}
