CREATE TABLE email_verification_tokens (id BIGINT AUTO_INCREMENT PRIMARY KEY, user_id BIGINT NOT NULL, token_hash CHAR(64) NOT NULL UNIQUE,
  expires_at DATETIME(3) NOT NULL, used_at DATETIME(3), FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE);
CREATE TABLE carts (id BIGINT AUTO_INCREMENT PRIMARY KEY, student_id BIGINT NOT NULL UNIQUE, shop_id BIGINT NOT NULL, discount_code VARCHAR(40),
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3), FOREIGN KEY (student_id) REFERENCES users(id), FOREIGN KEY (shop_id) REFERENCES shops(id));
CREATE TABLE cart_items (id BIGINT AUTO_INCREMENT PRIMARY KEY, cart_id BIGINT NOT NULL, product_id BIGINT, quantity INT NOT NULL DEFAULT 1 CHECK (quantity BETWEEN 1 AND 100),
  file_id BIGINT, print_config JSON, FOREIGN KEY (cart_id) REFERENCES carts(id) ON DELETE CASCADE, FOREIGN KEY (product_id) REFERENCES products(id), FOREIGN KEY (file_id) REFERENCES print_files(id));
CREATE TABLE payment_webhook_events (event_id VARCHAR(120) PRIMARY KEY, provider VARCHAR(30) NOT NULL, payload_hash CHAR(64) NOT NULL, received_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3));
CREATE TABLE system_settings (setting_key VARCHAR(80) PRIMARY KEY, setting_value VARCHAR(500) NOT NULL, updated_by BIGINT, updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3));
INSERT INTO system_settings(setting_key, setting_value) VALUES ('otp_ttl_minutes','15'),('payment_timeout_minutes','30'),('file_retention_days','7');
ALTER TABLE print_files ADD COLUMN deleted_at DATETIME(3) NULL;
ALTER TABLE reviews ADD COLUMN hidden BOOLEAN NOT NULL DEFAULT FALSE, ADD COLUMN updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3);
ALTER TABLE orders ADD COLUMN cancel_reason VARCHAR(255) NULL;
ALTER TABLE shops ADD UNIQUE KEY uq_shop_owner (owner_id);
