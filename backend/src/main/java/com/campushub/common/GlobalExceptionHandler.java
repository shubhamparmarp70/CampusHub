package com.campushub.common;
import com.campushub.notification.NotificationService;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.*;
import org.springframework.http.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

@RestControllerAdvice
public class GlobalExceptionHandler {
  private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
  private final NotificationService notes; private final AtomicLong lastAlert = new AtomicLong();
  public GlobalExceptionHandler(NotificationService notes){ this.notes = notes; }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  ResponseEntity<ApiResponse<Map<String,String>>> validation(MethodArgumentNotValidException e){
    Map<String,String> f = new LinkedHashMap<>();
    e.getBindingResult().getFieldErrors().forEach(x -> f.putIfAbsent(x.getField(), x.getDefaultMessage()));
    return ResponseEntity.badRequest().body(ApiResponse.fail("Please fix the highlighted fields.", f));
  }
  @ExceptionHandler(BusinessException.class)
  ResponseEntity<ApiResponse<Map<String,String>>> business(BusinessException e){
    return ResponseEntity.status(e.status()).body(ApiResponse.fail(e.getMessage(), Map.of("code", e.code())));
  }
  @ExceptionHandler(BadCredentialsException.class)
  ResponseEntity<ApiResponse<Void>> creds(BadCredentialsException e){
    return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiResponse.fail("Wrong email or password.", null));
  }
  @ExceptionHandler(AccessDeniedException.class)
  ResponseEntity<ApiResponse<Void>> denied(AccessDeniedException e){
    return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.fail("You don't have access to this.", null));
  }
  @ExceptionHandler(MaxUploadSizeExceededException.class)
  ResponseEntity<ApiResponse<Void>> tooBig(MaxUploadSizeExceededException e){
    return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).body(ApiResponse.fail("File is too large.", null));
  }
  @ExceptionHandler(org.springframework.http.converter.HttpMessageNotReadableException.class)
  ResponseEntity<ApiResponse<Void>> unreadable(Exception e){
    return ResponseEntity.badRequest().body(ApiResponse.fail("Request body is missing or malformed.", null));
  }
  @ExceptionHandler(org.springframework.dao.DuplicateKeyException.class)
  ResponseEntity<ApiResponse<Void>> duplicate(Exception e){
    return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiResponse.fail("That already exists.", null));
  }
  @ExceptionHandler(org.springframework.web.bind.MissingRequestHeaderException.class)
  ResponseEntity<ApiResponse<Void>> header(Exception e){
    return ResponseEntity.badRequest().body(ApiResponse.fail("A required header is missing.", null));
  }
  @ExceptionHandler(Exception.class)
  ResponseEntity<ApiResponse<Void>> other(Exception e){
    log.error("Unhandled error", e);   // stack trace goes to logs only, never to the client
    long now = System.currentTimeMillis(), last = lastAlert.get();
    if (now - last > 300_000 && lastAlert.compareAndSet(last, now)) {      // at most one developer alert per 5 minutes
      try { notes.notifyAdmins("Server error", e.getClass().getSimpleName()); } catch (Exception ignored) {}
    }
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ApiResponse.fail("Something went wrong. Please try again.", null));
  }
}
