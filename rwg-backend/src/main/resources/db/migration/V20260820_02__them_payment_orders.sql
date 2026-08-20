-- ============================================================================
-- RWG Backend - V20260820_02: payment_orders (chặng 2 Phase b).
-- Lệnh thanh toán chung cho NẠP (DEPOSIT) và RÚT (WITHDRAWAL).
-- Quy ước tiền tệ (DECISIONS.md): DECIMAL(20,8), BigDecimal, HALF_UP.
-- PK composite (id, created_at): partition-ready theo thời gian (DECISIONS.md mục b).
-- ============================================================================

CREATE TABLE payment_orders (
    id              CHAR(36)       NOT NULL,
    user_id         CHAR(36)       NOT NULL,
    provider        VARCHAR(32)    NOT NULL,                    -- stub | (provider thật ở chặng sau)
    type            VARCHAR(16)    NOT NULL,                    -- DEPOSIT | WITHDRAWAL
    amount          DECIMAL(20, 8) NOT NULL,
    currency        VARCHAR(8)     NOT NULL DEFAULT 'USD',
    status          VARCHAR(16)    NOT NULL DEFAULT 'PENDING',  -- PENDING | SUCCESS | FAILED | SETTLED | VOIDED
    provider_txn_id VARCHAR(128),                               -- mã giao dịch phía provider (webhook idempotency)
    idempotency_key VARCHAR(128)   NOT NULL,                    -- chống tạo lệnh trùng phía client/service
    bank_account_id CHAR(36),                                   -- chỉ dùng cho WITHDRAWAL
    created_at      DATETIME(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at      DATETIME(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    -- PK composite (id, created_at) theo DECISIONS.md mục b.
    PRIMARY KEY (id, created_at),
    -- UNIQUE kèm created_at để hợp lệ với PK composite khi partition.
    CONSTRAINT uq_payment_orders_provider_txn UNIQUE (provider_txn_id, created_at),
    CONSTRAINT uq_payment_orders_idempotency  UNIQUE (idempotency_key, created_at),
    CONSTRAINT chk_payment_orders_amount_positive CHECK (amount > 0),
    CONSTRAINT fk_payment_orders_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE RESTRICT
    -- bank_account_id KHÔNG đặt FK vì bảng bank_accounts tạo ở migration 03 (sau file này);
    -- toàn vẹn tham chiếu được đảm bảo ở tầng service (WithdrawalService kiểm bank tồn tại).
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE INDEX idx_payment_orders_user_id    ON payment_orders (user_id, created_at);
CREATE INDEX idx_payment_orders_status     ON payment_orders (status);
CREATE INDEX idx_payment_orders_provider   ON payment_orders (provider_txn_id);
CREATE INDEX idx_payment_orders_bank       ON payment_orders (bank_account_id);
