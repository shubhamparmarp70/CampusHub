package com.campushub.notification;
import java.util.*;
import java.util.concurrent.*;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
/** Real-time delivery for connected clients. Offline users still get the stored notification (and push, once FCM is wired). */
@Component
public class SseHub {
  private final Map<Long, List<SseEmitter>> emitters = new ConcurrentHashMap<>();
  public SseEmitter subscribe(long userId){
    SseEmitter e = new SseEmitter(30 * 60_000L);
    emitters.computeIfAbsent(userId, k -> new CopyOnWriteArrayList<>()).add(e);
    Runnable drop = () -> { var l = emitters.get(userId); if (l != null) l.remove(e); };
    e.onCompletion(drop); e.onTimeout(drop); e.onError(x -> drop.run());
    return e;
  }
  public void push(long userId, Object payload){
    for (SseEmitter e : emitters.getOrDefault(userId, List.of())) {
      try { e.send(SseEmitter.event().name("notification").data(payload)); } catch (Exception ex) { e.complete(); }
    }
  }
}
