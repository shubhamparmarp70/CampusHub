package com.campushub.user;
import com.campushub.cart.PrintConfigInput;
import com.campushub.common.*;
import com.campushub.security.AuthUser;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.*;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/** Saved print settings and favourite products. */
@RestController @RequestMapping("/api/student")
public class StudentLibraryController {
  public record PresetReq(@NotBlank @Size(max=60) String name, @Valid @NotNull PrintConfigInput config) {}
  private static final int MAX_PRESETS = 20, MAX_FAVORITES = 100;
  private final Db db; private final ObjectMapper om;
  public StudentLibraryController(Db db, ObjectMapper om){ this.db = db; this.om = om; }

  @GetMapping("/print-presets") ApiResponse<List<Map<String,Object>>> presets(@AuthenticationPrincipal AuthUser u) throws Exception {
    List<Map<String,Object>> out = new ArrayList<>();
    for (var r : db.rows("SELECT id, name, config FROM print_presets WHERE student_id=? ORDER BY name", u.id())) { var m = new LinkedHashMap<>(r); m.put("config", om.readValue(r.get("config").toString(), Map.class)); out.add(m); }
    return ApiResponse.ok("OK", out);
  }
  @PostMapping("/print-presets") ResponseEntity<ApiResponse<Void>> addPreset(@AuthenticationPrincipal AuthUser u, @Valid @RequestBody PresetReq r) throws Exception {
    if (db.count("SELECT COUNT(*) FROM print_presets WHERE student_id=?", u.id()) >= MAX_PRESETS) throw BusinessException.conflict("TOO_MANY_PRESETS", "You can save up to " + MAX_PRESETS + " print settings. Delete one first.");
    if (db.count("SELECT COUNT(*) FROM print_presets WHERE student_id=? AND name=?", u.id(), r.name().trim()) > 0) throw BusinessException.conflict("PRESET_EXISTS", "You already have saved settings with that name.");
    var c = r.config();    // the page range belongs to one document, so it is never saved
    var clean = new PrintConfigInput(c.size(), c.color(), c.sides(), c.copies(), null, c.orientation(), c.binding(), c.lamination(), c.stapling());
    db.insert("INSERT INTO print_presets(student_id, name, config) VALUES (?,?,CAST(? AS JSON))", u.id(), r.name().trim(), om.writeValueAsString(clean));
    return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok("Settings saved.", null));
  }
  @DeleteMapping("/print-presets/{id}") ApiResponse<Void> deletePreset(@AuthenticationPrincipal AuthUser u, @PathVariable long id){
    db.update("DELETE FROM print_presets WHERE id=? AND student_id=?", id, u.id()); return ApiResponse.ok("Deleted.", null); }

  @GetMapping("/favorites") ApiResponse<List<Map<String,Object>>> favorites(@AuthenticationPrincipal AuthUser u){
    return ApiResponse.ok("OK", db.rows("SELECT p.id product_id, p.name, p.selling_price, s.id shop_id, s.name shop_name, s.vertical, (p.kind='SERVICE' OR COALESCE(i.quantity,0)>0) AS in_stock FROM favorites f JOIN products p ON p.id=f.product_id AND p.active=TRUE JOIN shops s ON s.id=p.shop_id AND s.approval='APPROVED' LEFT JOIN inventory i ON i.product_id=p.id WHERE f.student_id=? ORDER BY f.created_at DESC", u.id())); }
  @PostMapping("/favorites/{productId}") ApiResponse<Void> addFavorite(@AuthenticationPrincipal AuthUser u, @PathVariable long productId){
    if (db.count("SELECT COUNT(*) FROM products p JOIN shops s ON s.id=p.shop_id WHERE p.id=? AND p.active=TRUE AND s.approval='APPROVED'", productId) == 0) throw BusinessException.notFound("Product not found.");
    if (db.count("SELECT COUNT(*) FROM favorites WHERE student_id=?", u.id()) >= MAX_FAVORITES) throw BusinessException.conflict("TOO_MANY_FAVORITES", "You can have up to " + MAX_FAVORITES + " favourites.");
    db.update("INSERT IGNORE INTO favorites(student_id, product_id) VALUES (?,?)", u.id(), productId); return ApiResponse.ok("Added to favourites.", null); }
  @DeleteMapping("/favorites/{productId}") ApiResponse<Void> removeFavorite(@AuthenticationPrincipal AuthUser u, @PathVariable long productId){
    db.update("DELETE FROM favorites WHERE student_id=? AND product_id=?", u.id(), productId); return ApiResponse.ok("Removed.", null); }
}
