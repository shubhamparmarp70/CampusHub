package com.campushub.files;
import com.campushub.common.*;
import com.campushub.security.AuthUser;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.*;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class FileService {
  private static final int MAX_BYTES = 10 * 1024 * 1024, MAX_PAGES = 500;
  private static final List<String> RISKY = List.of("/JavaScript", "/Launch", "/EmbeddedFile", "/RichMedia");
  private final Db db; private final StorageService storage; private final Settings settings; private final AuditService audit;
  public FileService(Db db, StorageService s, Settings st, AuditService a){ this.db = db; storage = s; settings = st; audit = a; }

  /** Validates by content (not the filename or the browser's content type), counts pages itself, and rejects risky PDFs. */
  @Transactional
  public Map<String,Object> upload(long userId, MultipartFile f) throws java.io.IOException {
    byte[] data = f.getBytes();
    if (data.length == 0) throw BusinessException.badRequest("EMPTY_FILE", "That file is empty.");
    if (data.length > MAX_BYTES) throw BusinessException.badRequest("FILE_TOO_BIG", "Files can be up to 10 MB.");
    if (data.length < 5 || !new String(data, 0, 5, StandardCharsets.ISO_8859_1).equals("%PDF-")) throw BusinessException.badRequest("NOT_PDF", "Only PDF files are supported for now.");
    String text = new String(data, StandardCharsets.ISO_8859_1);
    for (String t : RISKY) if (text.contains(t)) throw BusinessException.badRequest("UNSAFE_PDF", "This PDF contains scripts or attachments, so we can't accept it. Export a plain PDF and try again.");
    int pages;
    try (PDDocument d = Loader.loadPDF(data)) {
      if (d.isEncrypted()) throw BusinessException.badRequest("ENCRYPTED_PDF", "Password-protected PDFs can't be printed. Remove the password and upload again.");
      pages = d.getNumberOfPages();
    } catch (java.io.IOException e) { throw BusinessException.badRequest("BAD_PDF", "We couldn't read that PDF. Is the file damaged?"); }
    if (pages < 1 || pages > MAX_PAGES) throw BusinessException.badRequest("BAD_PAGES", "A document can have 1 to " + MAX_PAGES + " pages.");
    String name = Optional.ofNullable(f.getOriginalFilename()).orElse("document.pdf").replaceAll("[\\\\/]", "_");
    if (name.length() > 200) name = name.substring(name.length() - 200);
    String key = "u" + userId + "/" + UUID.randomUUID() + ".pdf";
    storage.put(key, data, "application/pdf");
    long id = db.insert("INSERT INTO print_files(owner_id, storage_key, original_name, mime_type, size_bytes, page_count, sha256, scan_status, delete_after) VALUES (?,?,?,?,?,?,?,'CLEAN',?)",
      userId, key, name, "application/pdf", data.length, pages, Tokens.sha256(text), LocalDateTime.now().plusDays(settings.getInt("file_retention_days", 7)));   // hook a real AV scan (e.g. ClamAV) here and start as PENDING
    return Map.of("fileId", id, "name", name, "pageCount", pages, "sizeBytes", data.length);
  }

  @Transactional
  public void deleteOwn(long userId, long fileId){
    var f = db.one("SELECT storage_key FROM print_files WHERE id=? AND owner_id=? AND deleted_at IS NULL", fileId, userId).orElseThrow(() -> BusinessException.notFound("File not found."));
    if (db.count("SELECT COUNT(*) FROM print_jobs WHERE file_id=?", fileId) > 0) throw BusinessException.conflict("FILE_IN_USE", "This file is part of an order and can't be deleted yet.");
    db.update("DELETE FROM cart_items WHERE file_id=?", fileId);
    storage.delete((String) f.get("storage_key")); db.update("UPDATE print_files SET deleted_at=NOW(3) WHERE id=?", fileId);
  }

  /** Owner of the file, or the shop that has an active order using it. Everyone else gets a plain 404. */
  public Map<String,Object> downloadUrl(AuthUser u, long fileId){
    var f = db.one("SELECT owner_id, storage_key, original_name FROM print_files WHERE id=? AND deleted_at IS NULL", fileId).orElseThrow(() -> BusinessException.notFound("File not found."));
    boolean ok = Db.l(f.get("owner_id")) == u.id() || db.count("SELECT COUNT(*) FROM print_jobs pj JOIN orders o ON o.id=pj.order_id JOIN shops s ON s.id=o.shop_id WHERE pj.file_id=? AND s.owner_id=? AND o.status IN ('PLACED','ACCEPTED','QUEUED','PROCESSING','PRINTING','BINDING','READY')", fileId, u.id()) > 0;
    if (!ok) throw BusinessException.notFound("File not found.");
    audit.log(u.id(), u.email(), "FILE_ACCESS", "file", String.valueOf(fileId));
    return Map.of("url", storage.presignGet((String) f.get("storage_key"), (String) f.get("original_name")), "expiresInSeconds", 300);
  }
}
