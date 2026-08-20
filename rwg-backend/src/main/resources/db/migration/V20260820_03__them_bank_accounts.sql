-- ============================================================================
-- RWG Backend - V20260820_03: bank_accounts (chặng 2 Phase b).
-- Tài khoản ngân hàng liên kết của user. Số tài khoản được MÃ HÓA AES-256-GCM
-- ở tầng ứng dụng (EncryptedStringConverter) — DB chỉ lưu ciphertext + iv, KHÔNG plaintext.
-- PK composite (id, created_at): partition-ready theo thời gian (DECISIONS.md mục b).
-- ============================================================================

CREATE TABLE bank_accounts (
    id                        CHAR(36)      NOT NULL,
    user_id                   CHAR(36)      NOT NULL,
    bank_code                 VARCHAR(32)   NOT NULL,
    account_number_ciphertext VARCHAR(512)  NOT NULL,          -- AES-256-GCM ciphertext (base64)
    account_number_iv         VARCHAR(64)   NOT NULL,          -- GCM IV (base64)
    masked_last4              VARCHAR(4)    NOT NULL,          -- 4 số cuối để hiển thị
    holder_name               VARCHAR(128)  NOT NULL,
    is_default                BOOLEAN       NOT NULL DEFAULT FALSE,
    status                    VARCHAR(16)   NOT NULL DEFAULT 'ACTIVE',  -- ACTIVE | REMOVED
    created_at                DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at                DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    -- PK composite (id, created_at) theo DECISIONS.md mục b.
    PRIMARY KEY (id, created_at),
    CONSTRAINT fk_bank_accounts_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE RESTRICT
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE INDEX idx_bank_accounts_user_id ON bank_accounts (user_id, created_at);
CREATE INDEX idx_bank_accounts_default ON bank_accounts (user_id, is_default);
