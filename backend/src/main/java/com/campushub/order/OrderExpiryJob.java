package com.campushub.order;
import org.slf4j.*;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
@Component
public class OrderExpiryJob {
  private static final Logger log = LoggerFactory.getLogger(OrderExpiryJob.class);
  private final OrderService orders;
  public OrderExpiryJob(OrderService o){ orders = o; }
  @Scheduled(initialDelay = 60_000, fixedDelay = 300_000)
  public void run(){ for (long id : orders.expiredUnpaid()) { try { orders.expireOne(id); } catch (Exception e) { log.warn("Could not expire order {}", id, e); } } }
}
