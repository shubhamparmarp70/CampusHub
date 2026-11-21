package com.campushub.sms;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.env.*;
import org.springframework.stereotype.Component;

/** Development only: nothing is sent. The text, including the code, is printed in the backend console. Refuses to run under the "prod" profile. */
@Component
@ConditionalOnProperty(name = "app.sms.provider", havingValue = "console", matchIfMissing = true)
public class ConsoleSmsSender implements SmsSender {
  private static final Logger log = LoggerFactory.getLogger(ConsoleSmsSender.class);
  private final Map<String, String> last = new ConcurrentHashMap<>();
  public ConsoleSmsSender(Environment env){
    if (env.acceptsProfiles(Profiles.of("prod"))) throw new IllegalStateException("The console SMS sender must not run in production. Set app.sms.provider to a real provider.");
  }
  @Override public void send(String to, String message){ last.put(to, message); log.info("SMS (development, NOT sent) to {}: {}", to, message); }
  public String lastMessage(String to){ return last.get(to); }
}
