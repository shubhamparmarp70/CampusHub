package com.campushub.order;
import static com.campushub.order.OrderStatus.*;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class OrderStatusTest {
  final OrderStateMachine sm = new OrderStateMachine();
  @Test void print_order_happy_path(){
    OrderStatus s = CREATED;
    for (OrderStatus n : new OrderStatus[]{PAYMENT_PENDING, PARTIALLY_PAID, PLACED, ACCEPTED, QUEUED, PROCESSING, PRINTING, BINDING, READY, COLLECTED}) s = sm.transition(s, n);
    assertEquals(COLLECTED, s);
  }
  @Test void food_order_can_skip_printing_and_binding(){
    assertTrue(PROCESSING.canMoveTo(READY));
  }
  @Test void order_cannot_be_collected_without_being_ready(){
    for (OrderStatus s : new OrderStatus[]{PLACED, ACCEPTED, PROCESSING, PRINTING, BINDING})
      assertThrows(InvalidTransitionException.class, () -> sm.transition(s, COLLECTED));
  }
  @Test void order_cannot_skip_advance_payment(){
    assertThrows(InvalidTransitionException.class, () -> sm.transition(PAYMENT_PENDING, PLACED));
    assertThrows(InvalidTransitionException.class, () -> sm.transition(CREATED, ACCEPTED));
  }
  @Test void terminal_states_are_final(){
    for (OrderStatus s : new OrderStatus[]{COLLECTED, CANCELLED, REFUNDED, FAILED}) { assertTrue(s.isTerminal()); assertThrows(InvalidTransitionException.class, () -> sm.transition(s, PLACED)); }
  }
  @Test void no_backwards_moves(){
    assertThrows(InvalidTransitionException.class, () -> sm.transition(READY, PROCESSING));
    assertThrows(InvalidTransitionException.class, () -> sm.transition(BINDING, PRINTING));
  }
  @Test void refund_path(){
    assertEquals(REFUNDED, sm.transition(sm.transition(PLACED, REFUND_PENDING), REFUNDED));
  }
}
