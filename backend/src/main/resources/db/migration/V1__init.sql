-- ID strategy: BIGINT AUTO_INCREMENT everywhere. Public order codes (CD1234) are separate columns.
-- Money: DECIMAL(10,2). Never FLOAT.
CREATE TABLE roles (id BIGINT AUTO_INCREMENT PRIMARY KEY, name VARCHAR(30) NOT NULL UNIQUE);
INSERT INTO roles(name) VALUES ('STUDENT'),('SHOP_OWNER'),('SUPER_ADMIN');

CREATE TABLE users (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  email VARCHAR(190) NOT NULL UNIQUE, password_hash VARCHAR(100) NOT NULL,
  full_name VARCHAR(120) NOT NULL, phone VARCHAR(20),
  status ENUM('ACTIVE','SUSPENDED','PENDING') NOT NULL DEFAULT 'ACTIVE',
  email_verified BOOLEAN NOT NULL DEFAULT FALSE, failed_logins INT NOT NULL DEFAULT 0,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3));
CREATE TABLE user_roles (user_id BIGINT NOT NULL, role_id BIGINT NOT NULL, PRIMARY KEY(user_id, role_id),
  FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE, FOREIGN KEY (role_id) REFERENCES roles(id));
CREATE TABLE refresh_tokens (id BIGINT AUTO_INCREMENT PRIMARY KEY, user_id BIGINT NOT NULL, token_hash CHAR(64) NOT NULL UNIQUE,
  expires_at DATETIME(3) NOT NULL, revoked BOOLEAN NOT NULL DEFAULT FALSE, created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE);
CREATE TABLE password_reset_tokens (id BIGINT AUTO_INCREMENT PRIMARY KEY, user_id BIGINT NOT NULL, token_hash CHAR(64) NOT NULL UNIQUE,
  expires_at DATETIME(3) NOT NULL, used_at DATETIME(3), FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE);

CREATE TABLE student_profiles (user_id BIGINT PRIMARY KEY, photo_key VARCHAR(255), college_name VARCHAR(160), department VARCHAR(120),
  semester TINYINT, student_code VARCHAR(40), updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE);

CREATE TABLE shops (
  id BIGINT AUTO_INCREMENT PRIMARY KEY, owner_id BIGINT NOT NULL,
  vertical ENUM('FOOD','DESK') NOT NULL,            -- drives the Canteen / CampusDesk tab
  name VARCHAR(140) NOT NULL, description TEXT, address VARCHAR(255), phone VARCHAR(20), logo_key VARCHAR(255), opening_hours JSON,
  approval ENUM('PENDING','APPROVED','DISABLED') NOT NULL DEFAULT 'PENDING',
  open_status ENUM('OPEN','CLOSED') NOT NULL DEFAULT 'CLOSED',
  duplex_rule ENUM('PER_PAGE','PER_SHEET') NOT NULL DEFAULT 'PER_PAGE',
  cancellation_policy JSON, avg_rating DECIMAL(3,2) NOT NULL DEFAULT 0, rating_count INT NOT NULL DEFAULT 0,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3), updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  FOREIGN KEY (owner_id) REFERENCES users(id), INDEX idx_shops_vertical (vertical, approval));
CREATE TABLE shop_followers (shop_id BIGINT NOT NULL, student_id BIGINT NOT NULL, created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (shop_id, student_id), FOREIGN KEY (shop_id) REFERENCES shops(id), FOREIGN KEY (student_id) REFERENCES users(id));

CREATE TABLE categories (id BIGINT AUTO_INCREMENT PRIMARY KEY, vertical ENUM('FOOD','DESK') NOT NULL, name VARCHAR(80) NOT NULL, active BOOLEAN NOT NULL DEFAULT TRUE, UNIQUE(vertical, name));
CREATE TABLE products (
  id BIGINT AUTO_INCREMENT PRIMARY KEY, shop_id BIGINT NOT NULL, category_id BIGINT NOT NULL,
  kind ENUM('ITEM','SERVICE') NOT NULL DEFAULT 'ITEM',   -- SERVICE = spiral binding, lamination ...
  name VARCHAR(140) NOT NULL, description TEXT, image_key VARCHAR(255), sku VARCHAR(60),
  selling_price DECIMAL(10,2) NOT NULL CHECK (selling_price >= 0), cost_price DECIMAL(10,2) CHECK (cost_price >= 0),
  active BOOLEAN NOT NULL DEFAULT TRUE,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3), updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  UNIQUE (shop_id, sku), INDEX idx_products_shop (shop_id, active),
  FOREIGN KEY (shop_id) REFERENCES shops(id), FOREIGN KEY (category_id) REFERENCES categories(id));
CREATE TABLE product_variants (id BIGINT AUTO_INCREMENT PRIMARY KEY, product_id BIGINT NOT NULL, label VARCHAR(80) NOT NULL,
  price_delta DECIMAL(10,2) NOT NULL DEFAULT 0, active BOOLEAN NOT NULL DEFAULT TRUE, FOREIGN KEY (product_id) REFERENCES products(id) ON DELETE CASCADE);

-- Owner-configured print prices. 'ANY' sentinel keeps the unique key meaningful (MySQL treats NULLs as distinct).
CREATE TABLE print_pricing (
  id BIGINT AUTO_INCREMENT PRIMARY KEY, shop_id BIGINT NOT NULL,
  kind ENUM('PAGE_RATE','DUPLEX_DISCOUNT','BINDING_SPIRAL','BINDING_TAPE','LAMINATION','STAPLING') NOT NULL,
  page_size ENUM('A4','A3','LEGAL','ANY') NOT NULL DEFAULT 'ANY', color_mode ENUM('BLACK_WHITE','COLOR','ANY') NOT NULL DEFAULT 'ANY',
  price DECIMAL(10,2) NOT NULL CHECK (price >= 0), updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  UNIQUE (shop_id, kind, page_size, color_mode), FOREIGN KEY (shop_id) REFERENCES shops(id));

CREATE TABLE inventory (product_id BIGINT PRIMARY KEY, quantity INT NOT NULL DEFAULT 0 CHECK (quantity >= 0), min_level INT NOT NULL DEFAULT 5,
  version BIGINT NOT NULL DEFAULT 0, updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  FOREIGN KEY (product_id) REFERENCES products(id) ON DELETE CASCADE);

CREATE TABLE discounts (
  id BIGINT AUTO_INCREMENT PRIMARY KEY, shop_id BIGINT NOT NULL, name VARCHAR(120) NOT NULL, description VARCHAR(255), code VARCHAR(40),
  type ENUM('PERCENT','FIXED') NOT NULL, value DECIMAL(10,2) NOT NULL CHECK (value > 0), min_order_amount DECIMAL(10,2) NOT NULL DEFAULT 0,
  applies_to JSON, starts_at DATETIME NOT NULL, ends_at DATETIME NOT NULL, max_usage INT, used_count INT NOT NULL DEFAULT 0, active BOOLEAN NOT NULL DEFAULT TRUE,
  UNIQUE (shop_id, code), FOREIGN KEY (shop_id) REFERENCES shops(id));

CREATE TABLE orders (
  id BIGINT AUTO_INCREMENT PRIMARY KEY, order_code VARCHAR(20) NOT NULL UNIQUE, student_id BIGINT NOT NULL, shop_id BIGINT NOT NULL, discount_id BIGINT,
  status ENUM('CREATED','PAYMENT_PENDING','PARTIALLY_PAID','PLACED','ACCEPTED','QUEUED','PROCESSING','PRINTING','BINDING','READY','COLLECTED','CANCELLED','REFUND_PENDING','REFUNDED','FAILED') NOT NULL DEFAULT 'CREATED',
  payment_status ENUM('UNPAID','PARTIALLY_PAID','PAID','REFUNDED') NOT NULL DEFAULT 'UNPAID',
  subtotal DECIMAL(10,2) NOT NULL, discount_amount DECIMAL(10,2) NOT NULL DEFAULT 0, tax_amount DECIMAL(10,2) NOT NULL DEFAULT 0,
  total_amount DECIMAL(10,2) NOT NULL, advance_amount DECIMAL(10,2) NOT NULL, remaining_amount DECIMAL(10,2) NOT NULL,
  est_prep_min SMALLINT, est_prep_max SMALLINT, ready_at DATETIME(3), collected_at DATETIME(3), stock_deducted BOOLEAN NOT NULL DEFAULT FALSE,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3), updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  CHECK (total_amount >= 0 AND advance_amount + remaining_amount = total_amount),
  INDEX idx_orders_student (student_id, created_at), INDEX idx_orders_shop_status (shop_id, status, created_at),
  FOREIGN KEY (student_id) REFERENCES users(id), FOREIGN KEY (shop_id) REFERENCES shops(id), FOREIGN KEY (discount_id) REFERENCES discounts(id));
CREATE TABLE discount_usages (id BIGINT AUTO_INCREMENT PRIMARY KEY, discount_id BIGINT NOT NULL, order_id BIGINT NOT NULL UNIQUE, student_id BIGINT NOT NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3), FOREIGN KEY (discount_id) REFERENCES discounts(id), FOREIGN KEY (order_id) REFERENCES orders(id), FOREIGN KEY (student_id) REFERENCES users(id));

CREATE TABLE order_items (
  id BIGINT AUTO_INCREMENT PRIMARY KEY, order_id BIGINT NOT NULL, product_id BIGINT, print_job_id BIGINT, description VARCHAR(255) NOT NULL,
  unit_price DECIMAL(10,2) NOT NULL, quantity INT NOT NULL CHECK (quantity > 0), line_total DECIMAL(10,2) NOT NULL,   -- price snapshot at checkout
  FOREIGN KEY (order_id) REFERENCES orders(id) ON DELETE CASCADE, FOREIGN KEY (product_id) REFERENCES products(id), INDEX idx_items_order (order_id));
CREATE TABLE inventory_transactions (
  id BIGINT AUTO_INCREMENT PRIMARY KEY, product_id BIGINT NOT NULL, order_item_id BIGINT, txn_type ENUM('ADD','REDUCE','ADJUST','DAMAGED','SALE','RESTOCK_CANCEL') NOT NULL,
  quantity_change INT NOT NULL, quantity_after INT NOT NULL, note VARCHAR(255), created_by BIGINT, created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  UNIQUE (order_item_id, txn_type),                      -- a callback replay can never deduct the same line twice
  INDEX idx_invtx_product (product_id, created_at), FOREIGN KEY (product_id) REFERENCES products(id), FOREIGN KEY (order_item_id) REFERENCES order_items(id));

CREATE TABLE print_files (id BIGINT AUTO_INCREMENT PRIMARY KEY, owner_id BIGINT NOT NULL, storage_key VARCHAR(255) NOT NULL UNIQUE, original_name VARCHAR(255) NOT NULL,
  mime_type VARCHAR(100) NOT NULL, size_bytes BIGINT NOT NULL, page_count INT NOT NULL, sha256 CHAR(64), scan_status ENUM('PENDING','CLEAN','REJECTED') NOT NULL DEFAULT 'PENDING',
  delete_after DATETIME NOT NULL, created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3), FOREIGN KEY (owner_id) REFERENCES users(id), INDEX idx_files_delete (delete_after));
CREATE TABLE print_jobs (id BIGINT AUTO_INCREMENT PRIMARY KEY, order_id BIGINT NOT NULL, file_id BIGINT NOT NULL, status ENUM('QUEUED','PRINTING','DONE') NOT NULL DEFAULT 'QUEUED',
  FOREIGN KEY (order_id) REFERENCES orders(id) ON DELETE CASCADE, FOREIGN KEY (file_id) REFERENCES print_files(id));
CREATE TABLE print_configurations (print_job_id BIGINT PRIMARY KEY, page_size ENUM('A4','A3','LEGAL') NOT NULL, color_mode ENUM('BLACK_WHITE','COLOR') NOT NULL,
  sides ENUM('SINGLE','DOUBLE') NOT NULL, copies INT NOT NULL CHECK (copies BETWEEN 1 AND 500), page_range VARCHAR(120), orientation ENUM('PORTRAIT','LANDSCAPE') NOT NULL DEFAULT 'PORTRAIT',
  binding ENUM('NONE','SPIRAL','TAPE') NOT NULL DEFAULT 'NONE', lamination BOOLEAN NOT NULL DEFAULT FALSE, stapling BOOLEAN NOT NULL DEFAULT FALSE,
  billable_pages INT NOT NULL, sheets INT NOT NULL, FOREIGN KEY (print_job_id) REFERENCES print_jobs(id) ON DELETE CASCADE);
ALTER TABLE order_items ADD CONSTRAINT fk_items_job FOREIGN KEY (print_job_id) REFERENCES print_jobs(id);

CREATE TABLE order_status_history (id BIGINT AUTO_INCREMENT PRIMARY KEY, order_id BIGINT NOT NULL, previous_status VARCHAR(20), new_status VARCHAR(20) NOT NULL,
  changed_by BIGINT, remarks VARCHAR(255), created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3), INDEX idx_hist_order (order_id, created_at), FOREIGN KEY (order_id) REFERENCES orders(id) ON DELETE CASCADE);

CREATE TABLE payments (
  id BIGINT AUTO_INCREMENT PRIMARY KEY, order_id BIGINT NOT NULL, payment_type ENUM('ADVANCE','REMAINING','REFUND') NOT NULL, amount DECIMAL(10,2) NOT NULL CHECK (amount > 0),
  payment_method ENUM('PHONEPE','GOOGLE_PAY') NOT NULL, transaction_id VARCHAR(80) NOT NULL, gateway_reference VARCHAR(120),
  status ENUM('INITIATED','PENDING','SUCCESS','FAILED','REFUNDED') NOT NULL DEFAULT 'INITIATED', created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3), completed_at DATETIME(3),
  UNIQUE (transaction_id), UNIQUE (gateway_reference),                       -- idempotent webhooks
  INDEX idx_pay_order (order_id), FOREIGN KEY (order_id) REFERENCES orders(id));
CREATE TABLE refunds (id BIGINT AUTO_INCREMENT PRIMARY KEY, order_id BIGINT NOT NULL, payment_id BIGINT NOT NULL, amount DECIMAL(10,2) NOT NULL CHECK (amount > 0), reason VARCHAR(255),
  status ENUM('REQUESTED','APPROVED','REJECTED','COMPLETED') NOT NULL DEFAULT 'REQUESTED', created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3), completed_at DATETIME(3),
  FOREIGN KEY (order_id) REFERENCES orders(id), FOREIGN KEY (payment_id) REFERENCES payments(id));

CREATE TABLE pickup_otps (id BIGINT AUTO_INCREMENT PRIMARY KEY, order_id BIGINT NOT NULL UNIQUE, otp_hash CHAR(64) NOT NULL, expires_at DATETIME(3) NOT NULL,
  attempts TINYINT NOT NULL DEFAULT 0, max_attempts TINYINT NOT NULL DEFAULT 3, used_at DATETIME(3), created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3), FOREIGN KEY (order_id) REFERENCES orders(id) ON DELETE CASCADE);

CREATE TABLE notifications (id BIGINT AUTO_INCREMENT PRIMARY KEY, user_id BIGINT NOT NULL, order_id BIGINT, category ENUM('ORDER','PAYMENT','SHOP_UPDATE','DISCOUNT','NEW_PRODUCT','PROMO','LOW_PRICE','SYSTEM','SECURITY') NOT NULL,
  title VARCHAR(160) NOT NULL, body VARCHAR(500), read_at DATETIME(3), created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  INDEX idx_notif_user (user_id, read_at, created_at), FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE);
CREATE TABLE notification_preferences (user_id BIGINT PRIMARY KEY, order_updates BOOLEAN NOT NULL DEFAULT TRUE, payment_updates BOOLEAN NOT NULL DEFAULT TRUE, shop_updates BOOLEAN NOT NULL DEFAULT TRUE,
  new_discounts BOOLEAN NOT NULL DEFAULT TRUE, new_products BOOLEAN NOT NULL DEFAULT FALSE, promotional BOOLEAN NOT NULL DEFAULT FALSE, low_price_deals BOOLEAN NOT NULL DEFAULT FALSE,
  FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE);

CREATE TABLE reviews (id BIGINT AUTO_INCREMENT PRIMARY KEY, order_id BIGINT NOT NULL UNIQUE, shop_id BIGINT NOT NULL, student_id BIGINT NOT NULL, rating TINYINT NOT NULL CHECK (rating BETWEEN 1 AND 5),
  comment VARCHAR(1000), created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3), FOREIGN KEY (order_id) REFERENCES orders(id), FOREIGN KEY (shop_id) REFERENCES shops(id), FOREIGN KEY (student_id) REFERENCES users(id));

CREATE TABLE audit_logs (id BIGINT AUTO_INCREMENT PRIMARY KEY, actor_id BIGINT, actor_email VARCHAR(190), action VARCHAR(80) NOT NULL, entity VARCHAR(60), entity_id VARCHAR(60),
  detail JSON, ip VARCHAR(64), created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3), INDEX idx_audit_time (created_at), INDEX idx_audit_actor (actor_id, created_at));
