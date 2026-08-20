-- ============================================================================
-- RWG Backend - V20260820_09: hệ thống giới thiệu & hoa hồng đại lý (Phase 2).
--
-- referral_codes: mỗi user đúng 1 mã giới thiệu (uq_referral_codes_user_id),
--   mã là duy nhất toàn hệ thống (PK trên code) để tra cứu lúc đăng ký chỉ cần
--   1 lookup theo khóa chính.
--
-- user_relations: quan hệ đại lý lưu PHẲNG THEO CẤP, không lưu cây đệ quy.
--   Mỗi user mới sinh tối đa 2 dòng: level=1 (người giới thiệu trực tiếp) và
--   level=2 (người giới thiệu của người đó). Nhờ vậy job hoa hồng chỉ cần JOIN
--   phẳng, KHÔNG cần truy vấn đệ quy — khớp giới hạn "tối đa 2 cấp" trong đặc tả.
--   uq_user_relations_descendant_level: 1 user chỉ có đúng 1 tuyến trên mỗi cấp.
--
-- commission_runs: CHỨNG TỪ chi hoa hồng, là chốt an toàn quan trọng nhất.
--   uq_commission_runs_agent_period_level đảm bảo mỗi đại lý chỉ được trả hoa
--   hồng ĐÚNG 1 LẦN cho mỗi (ngày, cấp) — job chạy lại do retry/deploy trùng/
--   admin bấm tay đều không thể trả trùng. Đây là lớp bảo vệ ở tầng DB, độc lập
--   với wallet_ledger_guard (lớp thứ hai, chặn theo idempotency_key khi credit).
--
-- Quy ước MySQL 8.4: CHAR(36) cho UUID (Hibernate GenerationType.UUID sinh phía
-- Java), DATETIME(6), DECIMAL(20,8) cho tiền — CẤM float/double.
-- ============================================================================

CREATE TABLE referral_codes (
    code       VARCHAR(16) NOT NULL,
    user_id    CHAR(36)    NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (code),
    CONSTRAINT uq_referral_codes_user_id UNIQUE (user_id),
    CONSTRAINT fk_referral_codes_user FOREIGN KEY (user_id)
        REFERENCES users (id) ON DELETE RESTRICT
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE user_relations (
    id            CHAR(36)    NOT NULL,
    -- ancestor = tuyến trên (đại lý nhận hoa hồng).
    ancestor_id   CHAR(36)    NOT NULL,
    -- descendant = tuyến dưới (người chơi tạo ra turnover).
    descendant_id CHAR(36)    NOT NULL,
    -- 1 = giới thiệu trực tiếp, 2 = giới thiệu gián tiếp. CHECK chặn cấp 3+.
    level         TINYINT     NOT NULL,
    created_at    DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT uq_user_relations_descendant_level UNIQUE (descendant_id, level),
    CONSTRAINT chk_user_relations_level CHECK (level IN (1, 2)),
    -- Chặn tự giới thiệu chính mình ngay tầng DB (service cũng chặn).
    CONSTRAINT chk_user_relations_not_self CHECK (ancestor_id <> descendant_id),
    CONSTRAINT fk_user_relations_ancestor FOREIGN KEY (ancestor_id)
        REFERENCES users (id) ON DELETE RESTRICT,
    CONSTRAINT fk_user_relations_descendant FOREIGN KEY (descendant_id)
        REFERENCES users (id) ON DELETE RESTRICT
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- Job gom hoa hồng quét theo tuyến trên -> index theo (ancestor_id, level).
CREATE INDEX idx_user_relations_ancestor_level ON user_relations (ancestor_id, level);

CREATE TABLE commission_runs (
    id             CHAR(36)      NOT NULL,
    agent_id       CHAR(36)      NOT NULL,
    -- Ngày (UTC) được chốt hoa hồng, KHÔNG phải thời điểm chạy job.
    period_date    DATE          NOT NULL,
    level          TINYINT       NOT NULL,
    -- Tổng cược hợp lệ của tuyến dưới trong ngày (chỉ bet SETTLED).
    turnover       DECIMAL(20,8) NOT NULL,
    -- Tỷ lệ áp dụng tại thời điểm chốt: lưu lại để đối soát về sau vẫn tái dựng
    -- được con số, kể cả khi admin đã đổi cấu hình % sau đó.
    rate           DECIMAL(9,6)  NOT NULL,
    amount         DECIMAL(20,8) NOT NULL,
    -- Khóa idempotency đã dùng khi credit ví (đối chiếu sang wallet_transactions).
    idempotency_key VARCHAR(128) NOT NULL,
    created_at     DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    -- CHỐT AN TOÀN: mỗi đại lý chỉ nhận hoa hồng 1 lần cho mỗi (ngày, cấp).
    CONSTRAINT uq_commission_runs_agent_period_level UNIQUE (agent_id, period_date, level),
    CONSTRAINT chk_commission_runs_level CHECK (level IN (1, 2)),
    CONSTRAINT chk_commission_runs_amount CHECK (amount >= 0),
    CONSTRAINT fk_commission_runs_agent FOREIGN KEY (agent_id)
        REFERENCES users (id) ON DELETE RESTRICT
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE INDEX idx_commission_runs_period ON commission_runs (period_date);

-- Cấu hình % hoa hồng: lưu DB để admin sửa được lúc chạy (đặc tả mục 6.2 coi
-- "thay đổi % hoa hồng" là hành vi cần audit -> hàm ý sửa được, không phải
-- hằng số biên dịch). Chỉ 1 dòng duy nhất (singleton) do CHECK id = 1.
CREATE TABLE commission_settings (
    id           TINYINT      NOT NULL,
    level1_rate  DECIMAL(9,6) NOT NULL,
    level2_rate  DECIMAL(9,6) NOT NULL,
    updated_at   DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_by   CHAR(36)     NULL,
    PRIMARY KEY (id),
    CONSTRAINT chk_commission_settings_singleton CHECK (id = 1),
    CONSTRAINT chk_commission_settings_rates
        CHECK (level1_rate >= 0 AND level1_rate <= 1
           AND level2_rate >= 0 AND level2_rate <= 1)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- Mặc định: cấp 1 = 0.5% turnover, cấp 2 = 0.2%. Admin đổi được qua API.
INSERT INTO commission_settings (id, level1_rate, level2_rate)
VALUES (1, 0.005000, 0.002000);
