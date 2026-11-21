-- Saved print settings (no page range: that belongs to one document) and favourite products.
CREATE TABLE print_presets (
  id BIGINT AUTO_INCREMENT PRIMARY KEY, student_id BIGINT NOT NULL, name VARCHAR(60) NOT NULL, config JSON NOT NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  UNIQUE (student_id, name), FOREIGN KEY (student_id) REFERENCES users(id) ON DELETE CASCADE);
CREATE TABLE favorites (
  student_id BIGINT NOT NULL, product_id BIGINT NOT NULL, created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (student_id, product_id),
  FOREIGN KEY (student_id) REFERENCES users(id) ON DELETE CASCADE, FOREIGN KEY (product_id) REFERENCES products(id) ON DELETE CASCADE);
