-- Existing accounts stay usable (default TRUE). New sign-ups are saved as FALSE by the app until they enter the SMS code.
ALTER TABLE users ADD COLUMN phone_verified BOOLEAN NOT NULL DEFAULT TRUE;
CREATE TABLE phone_verifications (
  user_id BIGINT PRIMARY KEY, phone VARCHAR(20) NOT NULL, otp_hash CHAR(64) NOT NULL, expires_at DATETIME(3) NOT NULL,
  attempts TINYINT NOT NULL DEFAULT 0, sent_count INT NOT NULL DEFAULT 1, window_start DATETIME(3) NOT NULL, last_sent_at DATETIME(3) NOT NULL,
  FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE);
