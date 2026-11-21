package com.campushub.review;
import com.campushub.common.Db;
import org.springframework.stereotype.Component;
@Component
public class Ratings {
  private final Db db;
  public Ratings(Db db){ this.db = db; }
  public void refresh(long shopId){
    db.update("UPDATE shops s SET avg_rating=(SELECT COALESCE(ROUND(AVG(rating),2),0) FROM reviews WHERE shop_id=s.id AND hidden=FALSE), rating_count=(SELECT COUNT(*) FROM reviews WHERE shop_id=s.id AND hidden=FALSE) WHERE s.id=?", shopId); }
}
