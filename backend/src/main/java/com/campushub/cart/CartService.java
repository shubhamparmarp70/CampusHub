package com.campushub.cart;
import com.campushub.common.*;
import com.campushub.pricing.PriceBreakdown;
import com.campushub.pricing.PrintPricingLoader;
import java.util.Map;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CartService {
  private final Db db; private final PricingEngine engine; private final PrintPricingLoader loader; private final ObjectMapper om;
  public CartService(Db db, PricingEngine e, PrintPricingLoader l, ObjectMapper om){ this.db = db; engine = e; loader = l; this.om = om; }

  private long cartFor(long studentId, long shopId){
    var shop = db.one("SELECT vertical, approval FROM shops WHERE id=?", shopId).orElseThrow(() -> BusinessException.notFound("Shop not found."));
    if (!"APPROVED".equals(shop.get("approval"))) throw BusinessException.notFound("Shop not found.");
    var c = db.one("SELECT id, shop_id FROM carts WHERE student_id=?", studentId).orElse(null);
    if (c == null) return db.insert("INSERT INTO carts(student_id, shop_id) VALUES (?,?)", studentId, shopId);
    long id = Db.l(c.get("id"));
    if (Db.l(c.get("shop_id")) != shopId) {
      if (db.count("SELECT COUNT(*) FROM cart_items WHERE cart_id=?", id) > 0) throw BusinessException.conflict("CART_OTHER_SHOP", "Your cart has items from another shop. Clear it first.");
      db.update("UPDATE carts SET shop_id=?, discount_code=NULL WHERE id=?", shopId, id);
    }
    return id;
  }
  @Transactional public Quote addProduct(long studentId, long shopId, long productId, int qty){
    if (qty < 1 || qty > 100) throw BusinessException.badRequest("BAD_QUANTITY", "Quantity must be between 1 and 100.");
    if (db.count("SELECT COUNT(*) FROM products WHERE id=? AND shop_id=? AND active=TRUE", productId, shopId) == 0) throw BusinessException.notFound("Product not found.");
    long cart = cartFor(studentId, shopId);
    if (db.update("UPDATE cart_items SET quantity=LEAST(quantity+?,100) WHERE cart_id=? AND product_id=?", qty, cart, productId) == 0)
      db.insert("INSERT INTO cart_items(cart_id, product_id, quantity) VALUES (?,?,?)", cart, productId, qty);
    return engine.quote(studentId, false);
  }
  @Transactional public Quote setQuantity(long studentId, long itemId, int qty){
    if (qty < 0 || qty > 100) throw BusinessException.badRequest("BAD_QUANTITY", "Quantity must be between 0 and 100.");
    if (qty == 0) return remove(studentId, itemId);
    if (db.update("UPDATE cart_items ci JOIN carts c ON c.id=ci.cart_id SET ci.quantity=? WHERE ci.id=? AND c.student_id=? AND ci.product_id IS NOT NULL", qty, itemId, studentId) == 0) throw BusinessException.notFound("Cart item not found.");
    return engine.quote(studentId, false);
  }
  private PriceBreakdown checkPrint(long studentId, long shopId, long fileId, PrintConfigInput cfg){
    if (!"DESK".equals(db.scalar(String.class, "SELECT vertical FROM shops WHERE id=? AND approval='APPROVED'", shopId).orElseThrow(() -> BusinessException.notFound("Shop not found."))))
      throw BusinessException.badRequest("NOT_A_PRINT_SHOP", "This shop doesn't offer printing.");
    var f = db.one("SELECT page_count FROM print_files WHERE id=? AND owner_id=? AND deleted_at IS NULL", fileId, studentId).orElseThrow(() -> BusinessException.notFound("Document not found. Upload it again."));
    return engine.priceConfig(loader.load(shopId), Db.i(f.get("page_count")), cfg);       // throws a friendly error if the shop can't do this configuration
  }
  public PriceBreakdown preview(long studentId, long shopId, long fileId, PrintConfigInput cfg){ return checkPrint(studentId, shopId, fileId, cfg); }

  /** Puts the products from an earlier order back in the cart at today's prices. Print jobs are skipped: their documents are deleted after collection. */
  @Transactional public Map<String,Object> reorder(long studentId, long orderId){
    var o = db.one("SELECT shop_id FROM orders WHERE id=? AND student_id=?", orderId, studentId).orElseThrow(() -> BusinessException.notFound("Order not found."));
    long shop = Db.l(o.get("shop_id"));
    long printJobs = db.count("SELECT COUNT(*) FROM order_items WHERE order_id=? AND print_job_id IS NOT NULL", orderId);
    var addable = new java.util.ArrayList<java.util.Map<String,Object>>(); int unavailable = 0;
    for (var it : db.rows("SELECT product_id, quantity FROM order_items WHERE order_id=? AND product_id IS NOT NULL", orderId)) {
      if (db.count("SELECT COUNT(*) FROM products WHERE id=? AND shop_id=? AND active=TRUE", it.get("product_id"), shop) > 0) addable.add(it); else unavailable++;
    }
    if (addable.isEmpty()) throw BusinessException.conflict("NOTHING_TO_REORDER", "None of the products from this order are available any more." + (printJobs > 0 ? " Print jobs can't be re-ordered, so please upload your document again." : ""));
    long cart = cartFor(studentId, shop);
    for (var it : addable) {
      if (db.update("UPDATE cart_items SET quantity=LEAST(quantity+?,100) WHERE cart_id=? AND product_id=?", it.get("quantity"), cart, it.get("product_id")) == 0)
        db.insert("INSERT INTO cart_items(cart_id, product_id, quantity) VALUES (?,?,LEAST(?,100))", cart, it.get("product_id"), it.get("quantity"));
    }
    Map<String,Object> out = new java.util.LinkedHashMap<>();
    out.put("quote", engine.quote(studentId, false)); out.put("added", addable.size()); out.put("unavailable", unavailable); out.put("printJobsSkipped", printJobs);
    return out;
  }

  @Transactional public Quote addPrintJob(long studentId, long shopId, long fileId, PrintConfigInput cfg) throws Exception {
    checkPrint(studentId, shopId, fileId, cfg); long cart = cartFor(studentId, shopId);
    db.insert("INSERT INTO cart_items(cart_id, file_id, quantity, print_config) VALUES (?,?,1,CAST(? AS JSON))", cart, fileId, om.writeValueAsString(cfg));
    return engine.quote(studentId, false);
  }
  @Transactional public Quote editPrintJob(long studentId, long itemId, PrintConfigInput cfg) throws Exception {
    var it = db.one("SELECT ci.file_id, c.shop_id FROM cart_items ci JOIN carts c ON c.id=ci.cart_id WHERE ci.id=? AND c.student_id=? AND ci.file_id IS NOT NULL", itemId, studentId).orElseThrow(() -> BusinessException.notFound("Print job not found."));
    checkPrint(studentId, Db.l(it.get("shop_id")), Db.l(it.get("file_id")), cfg);
    db.update("UPDATE cart_items SET print_config=CAST(? AS JSON) WHERE id=?", om.writeValueAsString(cfg), itemId);
    return engine.quote(studentId, false);
  }
  @Transactional public Quote remove(long studentId, long itemId){
    if (db.update("DELETE ci FROM cart_items ci JOIN carts c ON c.id=ci.cart_id WHERE ci.id=? AND c.student_id=?", itemId, studentId) == 0) throw BusinessException.notFound("Cart item not found.");
    return engine.quote(studentId, false);
  }
  @Transactional public Quote clear(long studentId){ db.update("DELETE FROM carts WHERE student_id=?", studentId); return engine.quote(studentId, false); }
  @Transactional public Quote setDiscount(long studentId, String code){
    if (db.update("UPDATE carts SET discount_code=? WHERE student_id=?", code == null || code.isBlank() ? null : code.trim().toUpperCase(), studentId) == 0) throw BusinessException.badRequest("EMPTY_CART", "Add something to your cart first.");
    Quote q = engine.quote(studentId, false);
    if (q.discountError() != null) throw new BusinessException(org.springframework.http.HttpStatus.BAD_REQUEST, "INVALID_DISCOUNT", q.discountError());   // rolls the code back
    return q;
  }
}
