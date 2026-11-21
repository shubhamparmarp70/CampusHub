package com.campushub.order;
import com.campushub.common.*;
import org.springframework.stereotype.Component;
/** The only place an order's status is written. Locks the row, checks the state machine, updates, and records history. */
@Component
public class OrderTransitions {
  private final Db db; private final OrderStateMachine sm;
  public OrderTransitions(Db db, OrderStateMachine sm){ this.db = db; this.sm = sm; }
  public void recordCreated(long orderId, Long by){
    db.update("INSERT INTO order_status_history(order_id, previous_status, new_status, changed_by, remarks) VALUES (?,NULL,'CREATED',?,'Order created')", orderId, by); }
  public OrderStatus move(long orderId, OrderStatus to, Long by, String remarks){
    OrderStatus from = OrderStatus.valueOf(db.scalar(String.class, "SELECT status FROM orders WHERE id=? FOR UPDATE", orderId).orElseThrow(() -> BusinessException.notFound("Order not found.")));
    sm.transition(from, to);
    db.update("UPDATE orders SET status=? WHERE id=?", to.name(), orderId);
    db.update("INSERT INTO order_status_history(order_id, previous_status, new_status, changed_by, remarks) VALUES (?,?,?,?,?)", orderId, from.name(), to.name(), by, remarks);
    return from;
  }
}
