-- ============================================================================
-- RWG Backend - V20260820_04: guard idempotency ledger + mốc first-deposit.
-- Fix review C1: UNIQUE(idempotency_key, created_at) trên wallet_transactions
--   KHÔNG chặn được 2 insert song song cùng key (khác created_at) -> double
--   credit/debit. Bảng guard này có PK THUẦN trên idempotency_key (KHÔNG kèm
--   created_at): đây là cột guard duy nhất toàn cục, KHÔNG phải bảng partition.
--   Pattern: insert guard TRƯỚC trong cùng transaction với UPDATE số dư + dòng
--   ledger; trùng key -> vi phạm UNIQUE -> DataIntegrityViolationException ->
--   transaction rollback và service trả kết quả hiện có (idempotent success).
-- Fix review M5: wallets.first_deposit_at là cờ claim NGUYÊN TỬ cho
--   FirstDepositEvent (conditional UPDATE ... WHERE first_deposit_at IS NULL
--   chỉ đúng 1 row thắng trong 2 giao dịch nạp song song).
-- ============================================================================

CREATE TABLE wallet_ledger_guard (
    idempotency_key VARCHAR(128) NOT NULL,
    created_at      DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    -- PK THUẦN trên idempotency_key: đảm bảo duy nhất THỰC SỰ ở tầng DB
    -- (khác uq_wallet_transactions_idempotency kèm created_at chỉ để partition-ready).
    PRIMARY KEY (idempotency_key)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

ALTER TABLE wallets ADD COLUMN first_deposit_at DATETIME(6) NULL;
