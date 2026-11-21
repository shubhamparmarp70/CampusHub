package com.campushub.catalog;
import com.campushub.common.*;
import com.campushub.security.AuthUser;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
@Component
public class ShopAccess {
  private final Db db;
  public ShopAccess(Db db){ this.db = db; }
  public long shopId(AuthUser u){ return db.scalar(Long.class, "SELECT id FROM shops WHERE owner_id=?", u.id()).orElseThrow(() -> BusinessException.notFound("Create your shop first.")); }
  /**
   * The owner's shop id, but only once the platform has verified the shop. Adding products, stock, prices and offers all go through here,
   * so the right switches on by itself the moment the admin approves, and off again if the shop or owner is disabled.
   */
  public long approvedShopId(AuthUser u){
    long id = shopId(u);
    var r = db.one("SELECT s.approval, u.status FROM shops s JOIN users u ON u.id=s.owner_id WHERE s.id=?", id).orElseThrow();
    if ("DISABLED".equals(r.get("approval")) || "SUSPENDED".equals(r.get("status")))
      throw new BusinessException(HttpStatus.FORBIDDEN, "SHOP_DISABLED", "Your shop has been disabled by the platform team. Please contact support.");
    if (!"APPROVED".equals(r.get("approval")))
      throw new BusinessException(HttpStatus.FORBIDDEN, "SHOP_NOT_APPROVED", "Your shop is waiting for verification. You can add products as soon as the platform team approves it.");
    return id;
  }
  public String vertical(long shopId){ return db.scalar(String.class, "SELECT vertical FROM shops WHERE id=?", shopId).orElseThrow(); }
  public void requireProduct(long shopId, long productId){ if (db.count("SELECT COUNT(*) FROM products WHERE id=? AND shop_id=?", productId, shopId) == 0) throw BusinessException.notFound("Product not found."); }
}
