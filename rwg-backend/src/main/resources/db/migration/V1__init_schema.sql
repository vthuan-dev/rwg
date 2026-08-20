-- ============================================================================
-- RWG Backend - V1: init schema (MySQL 8.4 LTS)
-- Quy ước tiền tệ (DECISIONS.md): DECIMAL(20,8), BigDecimal, HALF_UP.
-- Port từ bản PostgreSQL: giữ nguyên cấu trúc bảng/cột/ràng buộc/chỉ mục;
-- thay cú pháp PostgreSQL bằng MySQL:
--   * UUID -> CHAR(36), id do Hibernate (GenerationType.UUID) sinh phía Java
--     (MySQL 8 không có DEFAULT UUID tự sinh, xóa CREATE EXTENSION pgcrypto)
--   * TIMESTAMPTZ -> DATETIME(6) (Hibernate map Instant theo UTC)
--   * JSONB -> JSON
--   * GENERATED ALWAYS AS IDENTITY -> BIGINT AUTO_INCREMENT
--   * Engine InnoDB, charset utf8mb4; CHECK được MySQL 8.0.16+ thực thi thật
-- ============================================================================

-- ----------------------------------------------------------------------------
-- MODULE identity: users
-- ----------------------------------------------------------------------------
CREATE TABLE users (
    id                       CHAR(36)     NOT NULL,
    username                 VARCHAR(32)  NOT NULL,
    email                    VARCHAR(255) NOT NULL,
    password_hash            VARCHAR(100) NOT NULL,           -- BCrypt strength 12
    withdrawal_password_hash VARCHAR(100),                    -- hash riêng, nullable đến khi user đặt
    role                     VARCHAR(16)  NOT NULL DEFAULT 'PLAYER',   -- PLAYER | ADMIN
    status                   VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE',   -- ACTIVE | LOCKED | CLOSED
    kyc_level                VARCHAR(16)  NOT NULL DEFAULT 'NONE',     -- NONE | LEVEL_1 | LEVEL_2 | LEVEL_3
    last_login_at            DATETIME(6),
    created_at               DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at               DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT uq_users_username UNIQUE (username),
    CONSTRAINT uq_users_email    UNIQUE (email)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE INDEX idx_users_status ON users (status);

-- ----------------------------------------------------------------------------
-- MODULE identity: refresh_tokens (bản ghi DB phục vụ audit/thu hồi theo user;
-- trạng thái rotation nóng nằm ở Redis - xem RedisRefreshTokenStore)
-- ----------------------------------------------------------------------------
CREATE TABLE refresh_tokens (
    id         CHAR(36)    NOT NULL,
    user_id    CHAR(36)    NOT NULL,
    token_id   VARCHAR(64) NOT NULL,                 -- định danh opaque của token (không lưu raw token)
    ip_address VARCHAR(64),
    user_agent VARCHAR(512),
    revoked    BOOLEAN     NOT NULL DEFAULT FALSE,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    expires_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uq_refresh_tokens_token_id UNIQUE (token_id),
    -- QUYẾT ĐỊNH: giữ ON DELETE CASCADE — bảng này là PLACEHOLDER cho audit/thu hồi
    -- tập trung ở bước sau (DECISIONS.md); trạng thái rotation nóng nằm ở Redis/in-memory,
    -- xóa user thì session của user đó cũng hết ý nghĩa.
    CONSTRAINT fk_refresh_tokens_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE INDEX idx_refresh_tokens_user_id ON refresh_tokens (user_id);

-- ----------------------------------------------------------------------------
-- MODULE identity: audit_log (append-only: chỉ INSERT, không UPDATE/DELETE)
-- ----------------------------------------------------------------------------
CREATE TABLE audit_log (
    id             BIGINT       NOT NULL AUTO_INCREMENT,
    actor_id       CHAR(36),                         -- NULL với sự kiện chưa đăng nhập (register, login fail)
    actor_username VARCHAR(32),
    action         VARCHAR(64)  NOT NULL,            -- USER_REGISTERED, LOGIN_SUCCESS, LOGIN_FAILED, ...
    target_type    VARCHAR(32),                      -- USER, WALLET, BET, ...
    target_id      VARCHAR(64),
    details        JSON,                             -- payload bổ sung, KHÔNG chứa mật khẩu thô
    ip_address     VARCHAR(64),
    created_at     DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    -- PK composite (id, created_at): partition-ready theo thời gian (DECISIONS.md).
    PRIMARY KEY (id, created_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE INDEX idx_audit_log_actor_id   ON audit_log (actor_id);
CREATE INDEX idx_audit_log_action     ON audit_log (action);
CREATE INDEX idx_audit_log_created_at ON audit_log (created_at);

-- ----------------------------------------------------------------------------
-- MODULE wallet (CHỈ schema - chuẩn bị cho Bước 2, chưa có code logic)
-- Ledger double-entry kiểu debit/credit trên mỗi dòng giao dịch.
-- M1: bet trừ tiền ngay khi đặt cược (transaction BET, trạng thái LOCKED).
-- ----------------------------------------------------------------------------
CREATE TABLE wallets (
    id         CHAR(36)       NOT NULL,
    user_id    CHAR(36)       NOT NULL,
    balance    DECIMAL(20, 8) NOT NULL DEFAULT 0,
    currency   VARCHAR(8)     NOT NULL DEFAULT 'USD',
    version    BIGINT         NOT NULL DEFAULT 0,    -- optimistic locking
    created_at DATETIME(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT uq_wallets_user_id UNIQUE (user_id),
    CONSTRAINT chk_wallets_balance_non_negative CHECK (balance >= 0),
    -- ON DELETE RESTRICT: xóa user KHÔNG được xóa sổ cái; dùng soft-delete status=CLOSED.
    CONSTRAINT fk_wallets_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE RESTRICT
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE wallet_transactions (
    id              CHAR(36)       NOT NULL,
    wallet_id       CHAR(36)       NOT NULL,
    debit           DECIMAL(20, 8) NOT NULL DEFAULT 0,   -- tiền ra (đặt cược, rút tiền...)
    credit          DECIMAL(20, 8) NOT NULL DEFAULT 0,   -- tiền vào (thắng, nạp tiền, hoàn cược...)
    balance_after   DECIMAL(20, 8) NOT NULL,
    ref_type        VARCHAR(32)    NOT NULL,             -- BET | WIN | DEPOSIT | WITHDRAWAL | REFUND | BONUS | ADJUSTMENT
    ref_id          VARCHAR(64)    NOT NULL,             -- id đối tượng tham chiếu (bet id, order id...)
    idempotency_key VARCHAR(128)   NOT NULL,
    status          VARCHAR(16)    NOT NULL DEFAULT 'LOCKED',  -- LOCKED | SETTLED | VOIDED
    description     VARCHAR(255),
    created_at      DATETIME(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    -- PK composite (id, created_at): partition-ready theo thời gian (DECISIONS.md).
    -- UNIQUE idempotency kèm created_at để hợp lệ với PK composite khi partition.
    PRIMARY KEY (id, created_at),
    CONSTRAINT uq_wallet_transactions_idempotency UNIQUE (idempotency_key, created_at),
    CONSTRAINT chk_wallet_tx_debit_non_negative  CHECK (debit >= 0),
    CONSTRAINT chk_wallet_tx_credit_non_negative CHECK (credit >= 0),
    CONSTRAINT chk_wallet_tx_single_direction    CHECK (NOT (debit > 0 AND credit > 0)),
    -- ON DELETE RESTRICT: ledger là nguồn sự thật tài chính, không bao giờ xóa theo ví/user.
    CONSTRAINT fk_wallet_tx_wallet FOREIGN KEY (wallet_id) REFERENCES wallets (id) ON DELETE RESTRICT
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE INDEX idx_wallet_transactions_wallet_id ON wallet_transactions (wallet_id, created_at);
CREATE INDEX idx_wallet_transactions_ref       ON wallet_transactions (ref_type, ref_id);
