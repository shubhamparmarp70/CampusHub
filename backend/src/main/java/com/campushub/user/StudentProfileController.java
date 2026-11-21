package com.campushub.user;
import com.campushub.common.*;
import com.campushub.files.StorageService;
import com.campushub.security.AuthUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.*;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController @RequestMapping("/api/student/profile")
public class StudentProfileController {
  public record Req(@NotBlank @Size(max=120) String fullName,
    @Size(max=160) String collegeName, @Size(max=120) String department, @Min(1) @Max(12) Integer semester, @Size(max=40) String studentCode) {}
  private static final int MAX_PHOTO = 2 * 1024 * 1024;
  private final Db db; private final StorageService storage; private final AuditService audit;
  public StudentProfileController(Db db, StorageService s, AuditService a){ this.db = db; storage = s; audit = a; }

  @GetMapping ApiResponse<Map<String,Object>> get(@AuthenticationPrincipal AuthUser u){
    var r = db.one("SELECT u.full_name fullName, u.email, u.phone, p.college_name collegeName, p.department, p.semester, p.student_code studentCode, p.photo_key FROM users u LEFT JOIN student_profiles p ON p.user_id=u.id WHERE u.id=?", u.id()).orElseThrow();
    Map<String,Object> m = new LinkedHashMap<>(r); String key = (String) m.remove("photo_key");
    String url = null; if (key != null) { try { url = storage.presignGet(key, "profile"); } catch (Exception ignored) { /* storage not configured: show initials */ } }
    m.put("photoUrl", url);
    return ApiResponse.ok("OK", m);
  }
  @PutMapping ApiResponse<Void> put(@AuthenticationPrincipal AuthUser u, @Valid @RequestBody Req r){
    db.update("UPDATE users SET full_name=? WHERE id=?", r.fullName().trim(), u.id());
    db.update("INSERT INTO student_profiles(user_id, college_name, department, semester, student_code) VALUES (?,?,?,?,?) AS n ON DUPLICATE KEY UPDATE college_name=n.college_name, department=n.department, semester=n.semester, student_code=n.student_code",
      u.id(), r.collegeName(), r.department(), r.semester(), r.studentCode());
    return ApiResponse.ok("Profile saved.", null);
  }
  /** JPEG or PNG up to 2 MB, checked by content. Stored in the private bucket and shown through short-lived signed links. */
  @PostMapping(value = "/photo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  ApiResponse<Void> photo(@AuthenticationPrincipal AuthUser u, @RequestParam("file") MultipartFile f) throws Exception {
    byte[] b = f.getBytes();
    if (b.length == 0 || b.length > MAX_PHOTO) throw BusinessException.badRequest("BAD_PHOTO", "Photos can be up to 2 MB.");
    boolean jpg = b.length > 3 && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF;
    boolean png = b.length > 8 && (b[0] & 0xFF) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G';
    if (!jpg && !png) throw BusinessException.badRequest("BAD_PHOTO", "Please choose a JPEG or PNG photo.");
    String key = "u" + u.id() + "/photo-" + UUID.randomUUID() + (png ? ".png" : ".jpg");
    storage.put(key, b, png ? "image/png" : "image/jpeg");
    String old = db.scalar(String.class, "SELECT photo_key FROM student_profiles WHERE user_id=?", u.id()).orElse(null);
    db.update("INSERT INTO student_profiles(user_id, photo_key) VALUES (?,?) AS n ON DUPLICATE KEY UPDATE photo_key=n.photo_key", u.id(), key);
    if (old != null) { try { storage.delete(old); } catch (Exception ignored) {} }
    audit.log(u.id(), u.email(), "PROFILE_PHOTO_CHANGED", "user", String.valueOf(u.id()));
    return ApiResponse.ok("Photo updated.", null);
  }
}
