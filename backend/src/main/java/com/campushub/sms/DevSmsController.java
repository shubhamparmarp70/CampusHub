package com.campushub.sms;
import com.campushub.common.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

/** Exists only with the console SMS sender: lets the smoke test read the code it would have received. */
@RestController
@ConditionalOnProperty(name = "app.sms.provider", havingValue = "console", matchIfMissing = true)
public class DevSmsController {
  private final ConsoleSmsSender sms;
  public DevSmsController(ConsoleSmsSender sms){ this.sms = sms; }
  @GetMapping("/api/dev/last-sms") ApiResponse<Map<String,String>> last(@RequestParam String phone){
    String m = sms.lastMessage(Phones.normalize(phone));
    if (m == null) throw BusinessException.notFound("No SMS yet for that number.");
    return ApiResponse.ok("OK", Map.of("message", m));
  }
}
