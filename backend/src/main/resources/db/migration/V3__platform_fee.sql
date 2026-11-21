-- Platform fee: charged to the student on top of the (discounted) item total, tracked separately from the shop's earnings.
ALTER TABLE orders ADD COLUMN platform_fee DECIMAL(10,2) NOT NULL DEFAULT 0 AFTER tax_amount;
-- Off by default (0%). Admins switch it on in /api/admin/settings.
INSERT INTO system_settings(setting_key, setting_value) VALUES ('platform_fee_percent','0'),('platform_fee_min','0'),('platform_fee_max','0');
