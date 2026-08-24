-- ============================================================================
-- RWG Backend - V20260823_03: Gỡ bỏ hoàn toàn ví điện tử (CRYPTO/USDT) khỏi bank_accounts.
--
-- Theo yêu cầu của user, phương thức rút tiền CHỈ dùng tài khoản ngân hàng (BANK).
-- Dòng crypto thử nghiệm cũ sẽ bị xoá trước khi DROP cột để tránh lỗi ràng buộc.
-- ============================================================================

-- 1. Xoá dòng crypto thử nghiệm cũ (nếu có)
DELETE FROM bank_accounts WHERE payout_type = 'CRYPTO';

-- 2. Gỡ bỏ các CHECK constraint liên quan đến crypto
ALTER TABLE bank_accounts DROP CONSTRAINT chk_bank_accounts_payout_type;
ALTER TABLE bank_accounts DROP CONSTRAINT chk_bank_accounts_bank_fields;
ALTER TABLE bank_accounts DROP CONSTRAINT chk_bank_accounts_crypto_fields;
ALTER TABLE bank_accounts DROP CONSTRAINT chk_bank_accounts_network;

-- 3. Gỡ bỏ chỉ mục fingerprint chống trùng của crypto
DROP INDEX idx_bank_accounts_fingerprint ON bank_accounts;

-- 4. Loại bỏ các cột dành riêng cho crypto
ALTER TABLE bank_accounts DROP COLUMN payout_type;
ALTER TABLE bank_accounts DROP COLUMN network;
ALTER TABLE bank_accounts DROP COLUMN asset;
ALTER TABLE bank_accounts DROP COLUMN masked_prefix;
ALTER TABLE bank_accounts DROP COLUMN address_fingerprint;

-- 5. Khôi phục các cột của tài khoản ngân hàng về dạng bắt buộc (NOT NULL)
-- Cần làm riêng từng câu để H2 compatibility mode chạy trơn tru không sập.
ALTER TABLE bank_accounts MODIFY COLUMN bank_code VARCHAR(32) NOT NULL;
ALTER TABLE bank_accounts MODIFY COLUMN holder_name VARCHAR(128) NOT NULL;
ALTER TABLE bank_accounts MODIFY COLUMN masked_last4 VARCHAR(4) NOT NULL;
