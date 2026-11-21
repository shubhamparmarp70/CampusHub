package com.campushub.files;
import com.campushub.common.ApiResponse;
import com.campushub.security.AuthUser;
import java.util.Map;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
public class FileController {
  private final FileService files;
  public FileController(FileService f){ files = f; }
  @PostMapping(value = "/api/student/files", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  ResponseEntity<ApiResponse<Map<String,Object>>> upload(@AuthenticationPrincipal AuthUser u, @RequestParam("file") MultipartFile file) throws Exception {
    return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok("File uploaded.", files.upload(u.id(), file))); }
  @DeleteMapping("/api/student/files/{id}") ApiResponse<Void> delete(@AuthenticationPrincipal AuthUser u, @PathVariable long id){ files.deleteOwn(u.id(), id); return ApiResponse.ok("File deleted.", null); }
  @GetMapping("/api/files/{id}/download-url") ApiResponse<Map<String,Object>> url(@AuthenticationPrincipal AuthUser u, @PathVariable long id){ return ApiResponse.ok("OK", files.downloadUrl(u, id)); }
}
