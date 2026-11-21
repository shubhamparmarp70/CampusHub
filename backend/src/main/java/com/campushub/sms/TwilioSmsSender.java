package com.campushub.sms;
import com.campushub.common.BusinessException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import org.slf4j.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/**
 * Sends SMS through Twilio's REST API. For Indian numbers, carriers require a registered sender and message template (DLT);
 * an India-based provider such as MSG91 is usually easier in production. Add one by implementing SmsSender.
 */
@Component
@ConditionalOnProperty(name = "app.sms.provider", havingValue = "twilio")
public class TwilioSmsSender implements SmsSender {
  private static final Logger log = LoggerFactory.getLogger(TwilioSmsSender.class);
  @Value("${app.sms.twilio.account-sid}") private String sid;
  @Value("${app.sms.twilio.auth-token}") private String token;
  @Value("${app.sms.twilio.from:}") private String from;
  @Value("${app.sms.twilio.messaging-service-sid:}") private String messagingServiceSid;
  private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
  private static String enc(String s){ return URLEncoder.encode(s, StandardCharsets.UTF_8); }
  private static BusinessException failed(){ return new BusinessException(HttpStatus.BAD_GATEWAY, "SMS_FAILED", "We couldn't send the text message right now. Please try again in a moment."); }

  @Override public void send(String to, String message){
    String form = "To=" + enc(to) + "&Body=" + enc(message) + (messagingServiceSid.isBlank() ? "&From=" + enc(from) : "&MessagingServiceSid=" + enc(messagingServiceSid));
    HttpRequest req = HttpRequest.newBuilder(URI.create("https://api.twilio.com/2010-04-01/Accounts/" + sid + "/Messages.json")).timeout(Duration.ofSeconds(10))
      .header("Authorization", "Basic " + Base64.getEncoder().encodeToString((sid + ":" + token).getBytes(StandardCharsets.UTF_8)))
      .header("Content-Type", "application/x-www-form-urlencoded").POST(HttpRequest.BodyPublishers.ofString(form)).build();
    try {
      HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
      if (res.statusCode() >= 300) { log.warn("Twilio rejected an SMS: HTTP {}", res.statusCode()); throw failed(); }
    } catch (java.io.IOException e) { log.warn("Twilio request failed: {}", e.getClass().getSimpleName()); throw failed(); }
    catch (InterruptedException e) { Thread.currentThread().interrupt(); throw failed(); }
  }
}
