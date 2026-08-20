-- ============================================================================
-- RWG Backend - V20260820_10: quy trinh 4 mat (maker-checker) cho thao tac admin.
--
-- VAN DE: truoc day moi ADMIN vua dieu chinh duoc so du vi vua tu duyet duoc lenh
--   rut -> mot nguoi co the chuyen tien ra khoi san trong 2 request. Audit log chi
--   ghi vet SAU KHI mat tien, khong ngan chan.
--
-- Bang nay luu DE NGHI thao tac vuot nguong: nguoi tao (maker) chi tao de nghi,
--   phai co admin THU HAI (checker) phe duyet thi tien moi thuc su chuyen.
--
-- chk_admin_approval_maker_ne_checker: chan nguoi tao tu duyet ngay o TANG DB.
--   Service cung chan, nhung khong dua vao mot lop duy nhat — day la rang buoc
--   cuoi cung khong the lach bang bug ung dung.
--
-- idempotency_key: khoa dung khi goi WalletService.credit/debit luc thuc thi. Nho
--   wallet_ledger_guard (PK thuan) nen bam approve hai lan KHONG cong tien hai lan.
--
-- Quy uoc MySQL 8.4: CHAR(36) cho UUID, DATETIME(6), DECIMAL(20,8) cho tien.
-- ============================================================================

CREATE TABLE admin_approval_requests (
    id              CHAR(36)      NOT NULL,
    -- Hien chi WALLET_ADJUSTMENT; mo rong ve sau (vd WITHDRAWAL_APPROVAL).
    type            VARCHAR(32)   NOT NULL,
    status          VARCHAR(16)   NOT NULL DEFAULT 'PENDING',
    -- User bi tac dong boi de nghi.
    target_user_id  CHAR(36)      NOT NULL,
    -- CREDIT | DEBIT
    direction       VARCHAR(8)    NOT NULL,
    amount          DECIMAL(20,8) NOT NULL,
    reason          VARCHAR(255)  NOT NULL,
    -- Admin tao de nghi.
    maker_id        CHAR(36)      NOT NULL,
    -- Admin phe duyet/tu choi; NULL khi con PENDING.
    checker_id      CHAR(36)      NULL,
    decided_at      DATETIME(6)   NULL,
    -- Ly do tu choi (NULL khi duyet).
    decision_note   VARCHAR(255)  NULL,
    -- Khoa idempotency dung luc thuc thi credit/debit.
    idempotency_key VARCHAR(128)  NOT NULL,
    created_at      DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT uq_admin_approval_idempotency UNIQUE (idempotency_key),
    -- CHOT AN TOAN: nguoi tao KHONG duoc la nguoi duyet (quy trinh 4 mat).
    CONSTRAINT chk_admin_approval_maker_ne_checker
        CHECK (checker_id IS NULL OR checker_id <> maker_id),
    CONSTRAINT chk_admin_approval_status
        CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED')),
    CONSTRAINT chk_admin_approval_direction
        CHECK (direction IN ('CREDIT', 'DEBIT')),
    CONSTRAINT chk_admin_approval_amount CHECK (amount > 0),
    CONSTRAINT fk_admin_approval_target FOREIGN KEY (target_user_id)
        REFERENCES users (id) ON DELETE RESTRICT,
    CONSTRAINT fk_admin_approval_maker FOREIGN KEY (maker_id)
        REFERENCES users (id) ON DELETE RESTRICT,
    CONSTRAINT fk_admin_approval_checker FOREIGN KEY (checker_id)
        REFERENCES users (id) ON DELETE RESTRICT
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- Hang doi cho duyet: quet theo status + thoi diem tao.
CREATE INDEX idx_admin_approval_status_created ON admin_approval_requests (status, created_at);
-- Soi lich su thao tac cua mot admin cu the.
CREATE INDEX idx_admin_approval_maker ON admin_approval_requests (maker_id, created_at);
