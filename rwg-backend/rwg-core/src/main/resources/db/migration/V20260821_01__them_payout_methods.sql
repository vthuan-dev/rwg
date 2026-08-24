-- ============================================================================
-- RWG Backend - V20260821_01: phuong thuc nhan tien (bank + crypto).
--
-- VAN DE: bang bank_accounts hien chi luu duoc TAI KHOAN NGAN HANG. Nguoi choi
--   muon nhan tien bang USDT thi khong co cho luu mang luoi (TRC20/ERC20/BEP20),
--   va rang buoc validate cu chi nhan CHU SO nen dia chi vi bi chan thang.
--
-- VI SAO MO RONG bank_accounts CHU KHONG TAO BANG MOI: payment_orders.bank_account_id
--   dang tro tới id cua bang nay va WithdrawalService doc bang nay de lay noi nhan
--   tien. Tao bang thu hai se sinh ra HAI nguon su that cho cung mot cau hoi
--   "tien cua lenh rut nay di ve dau" - va do la loai lech du lieu khong the sua
--   bang code sau nay. Ten bang giu nguyen de khong pha FK/index dang chay.
--
-- address_fingerprint: HMAC-SHA256 CO KHOA cua dia chi da chuan hoa.
--   VI SAO PHAI CO NGAY: ciphertext dung IV ngau nhien moi lan ma hoa, nen CUNG
--   MOT dia chi luu hai lan cho ra hai ciphertext khac nhau -> KHONG the so trung.
--   Khong co cot nay thi khong chan duoc nguoi choi them trung mot vi, va khong
--   phat hien duoc hai tai khoan khac nhau dung CHUNG mot vi USDT (dau hieu da
--   tai khoan manh). Them cot sau khi bang da co du lieu that se phai backfill,
--   ma backfill can giai ma toan bo bang - viec rui ro khong can thiet.
--
--   VI SAO HMAC CO KHOA chu khong SHA-256 tran: SHA-256 tran cua mot dia chi vi la
--   tinh duoc offline. Ai doc duoc bang DB se do ra dia chi bang cach bam thu danh
--   sach vi cong khai tren blockchain. HMAC co khoa lam viec do bat kha thi.
--
-- KHONG dat UNIQUE tren fingerprint: bang nay co PK composite (id, created_at)
--   theo DECISIONS.md muc (b), nen moi UNIQUE phai kem created_at - ma nhu vay thi
--   hai ban ghi cung dia chi khac thoi diem van lot, tuc UNIQUE tro nen vo nghia
--   cho dung muc dich chong trung. Vi vay chong trung dat o tang service.
--
-- Quy uoc MySQL 8.4: CHAR(36) cho UUID, DATETIME(6), VARCHAR cho ma/enum.
-- ============================================================================

-- BANK | CRYPTO. DEFAULT 'BANK' de moi dong DA TON TAI tro thanh hop le ngay,
-- khong can UPDATE backfill rieng.
ALTER TABLE bank_accounts
    ADD COLUMN payout_type VARCHAR(16) NOT NULL DEFAULT 'BANK';

-- TRC20 | ERC20 | BEP20. NULL voi payout_type='BANK'.
ALTER TABLE bank_accounts
    ADD COLUMN network VARCHAR(16) NULL;

-- Tai san nhan (hien chi USDT). Tach khoi network vi mot mang cho nhieu tai san.
ALTER TABLE bank_accounts
    ADD COLUMN asset VARCHAR(16) NULL;

-- Ky tu DAU cua dia chi de hien dang "TXk9...aB3f". Rieng 4 so cuoi khong du nhan
-- dang mot dia chi vi: hai vi khac nhau trung 4 ky tu cuoi la chuyen thuong.
ALTER TABLE bank_accounts
    ADD COLUMN masked_prefix VARCHAR(8) NULL;

-- HMAC-SHA256 hex (64 ky tu) - xem ghi chu dau file.
ALTER TABLE bank_accounts
    ADD COLUMN address_fingerprint VARCHAR(64) NULL;

-- Vi crypto KHONG co ma ngan hang va KHONG co ten chu tai khoan -> hai cot nay
-- phai cho phep NULL. Rang buoc "bank thi bat buoc co" duoc chuyen sang CHECK
-- ben duoi de khong mat kiem soat.
ALTER TABLE bank_accounts
    MODIFY COLUMN bank_code VARCHAR(32) NULL;

ALTER TABLE bank_accounts
    MODIFY COLUMN holder_name VARCHAR(128) NULL;

-- masked_last4 chi co nghia voi so tai khoan ngan hang.
ALTER TABLE bank_accounts
    MODIFY COLUMN masked_last4 VARCHAR(4) NULL;

-- Chan o TANG DB, khong chi o service: mot dong "lai" (crypto ma thieu mang luoi,
-- hoac bank ma thieu ten chu tai khoan) se lam nguoi van hanh chuyen tien di dau
-- khong biet. Bat buoc moi duong ghi tuan thu, ke ca script SQL chay tay.
ALTER TABLE bank_accounts
    ADD CONSTRAINT chk_bank_accounts_payout_type
        CHECK (payout_type IN ('BANK', 'CRYPTO'));

ALTER TABLE bank_accounts
    ADD CONSTRAINT chk_bank_accounts_bank_fields
        CHECK (payout_type <> 'BANK'
               OR (bank_code IS NOT NULL AND holder_name IS NOT NULL
                   AND masked_last4 IS NOT NULL));

ALTER TABLE bank_accounts
    ADD CONSTRAINT chk_bank_accounts_crypto_fields
        CHECK (payout_type <> 'CRYPTO'
               OR (network IS NOT NULL AND asset IS NOT NULL
                   AND masked_prefix IS NOT NULL AND address_fingerprint IS NOT NULL));

ALTER TABLE bank_accounts
    ADD CONSTRAINT chk_bank_accounts_network
        CHECK (network IS NULL OR network IN ('TRC20', 'ERC20', 'BEP20'));

-- Do trung dia chi trong pham vi mot user (chong them trung mot vi).
CREATE INDEX idx_bank_accounts_fingerprint ON bank_accounts (user_id, address_fingerprint);
