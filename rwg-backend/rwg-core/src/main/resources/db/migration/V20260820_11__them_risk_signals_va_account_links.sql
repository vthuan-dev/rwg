-- ============================================================================
-- RWG Backend - V20260820_11: tin hieu risk & lien ket tai khoan (chang 7).
--
-- VAN DE: he hoa hong chan duoc TU GIOI THIEU (A dung ma cua A) va VONG LAP
--   (A->B->A), nhung khong chan duoc cach truc loi that: mot nguoi tao tai khoan
--   A, roi tu tao B/C/D dang ky bang ma cua A, cuoc bang tien cua chinh minh o
--   B/C/D roi rut hoa hong ve A. Khong rang buoc nao bi vi pham: B khac A, khong
--   co vong lap, va turnover cua B la CUOC THAT -> job hoa hong tra tien binh
--   thuong. Day la duong rut tien duong ky vong, chi gioi han boi toc do cuoc.
--
-- account_signals: dau vet luc DANG KY, moi user dung 1 dong.
--   IP luu PLAINTEXT chu khong hash: audit_log.ip_address da luu plaintext nen
--   hash o day khong giam duoc muc pho bay du lieu, ma lai lam nguoi dieu tra
--   khong doi chieu duoc voi log. Nguoc lai user_agent thi HASH: no dai, khong ai
--   doc thu cong, va chi dung de so khop bang nhau.
--
-- account_links: cap tai khoan bi nghi la CUNG MOT NGUOI.
--   CHOT QUAN TRONG: cap luu DA SAP XEP (user_a_id < user_b_id). Neu luu tu do
--   thi (A,B) va (B,A) la hai dong khac nhau -> UNIQUE vo hieu, va admin se thay
--   cung mot lien ket hai lan voi hai trang thai co the NGUOC NHAU. CHECK o tang
--   DB bat buoc moi duong ghi tuan thu, khong chi duong di qua service.
--
-- DIEM CHAN LA HOA HONG, KHONG PHAI TAI KHOAN: bang nay KHONG khoa tai khoan.
--   Tai khoan thu hai cua mot nguoi tu no khong phai gian lan (vo chong dung chung
--   may la chuyen that). Cai gay thiet hai la dong hoa hong A tu tra cho chinh
--   minh -> chan dung dong do, nguoi dung van choi/nap/cuoc binh thuong.
--
-- Quy uoc MySQL 8.4: CHAR(36) cho UUID, DATETIME(6), DECIMAL(20,8) cho tien.
-- ============================================================================

CREATE TABLE account_signals (
    -- Moi user dung 1 dong -> user_id lam PK luon, khong can cot id rieng.
    user_id            CHAR(36)     NOT NULL,
    -- IP luc dang ky (plaintext, xem ghi chu dau file).
    registration_ip    VARCHAR(64)  NOT NULL,
    -- SHA-256 cua header X-Device-Id. NULL khi client khong gui (khong bat buoc).
    device_fingerprint VARCHAR(128) NULL,
    -- SHA-256 cua User-Agent: chi de so khop, khong can doc lai.
    user_agent_hash    VARCHAR(64)  NULL,
    created_at         DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (user_id),
    CONSTRAINT fk_account_signals_user FOREIGN KEY (user_id)
        REFERENCES users (id) ON DELETE RESTRICT
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- Dem tai khoan cung IP trong cua so thoi gian -> can ca hai cot trong index.
CREATE INDEX idx_account_signals_ip_created ON account_signals (registration_ip, created_at);
-- Do trung thiet bi: lookup thang theo fingerprint.
CREATE INDEX idx_account_signals_device ON account_signals (device_fingerprint);

CREATE TABLE account_links (
    id          CHAR(36)     NOT NULL,
    -- Cap DA SAP XEP: user_a_id < user_b_id (xem ghi chu dau file).
    user_a_id   CHAR(36)     NOT NULL,
    user_b_id   CHAR(36)     NOT NULL,
    -- SHARED_DEVICE (manh) | SHARED_IP (yeu) | MANUAL (nguoi that tu noi)
    link_type   VARCHAR(16)  NOT NULL,
    -- SUSPECTED (may do) | CONFIRMED (nguoi xac nhan) | CLEARED (go oan)
    status      VARCHAR(16)  NOT NULL DEFAULT 'SUSPECTED',
    -- Bang chung dang JSON: fingerprint/IP da khop, so tai khoan trong chum.
    evidence    TEXT         NULL,
    -- Vet nguoi that da xem: ai xem, khi nao, ket luan gi.
    reviewed_by CHAR(36)     NULL,
    reviewed_at DATETIME(6)  NULL,
    note        VARCHAR(255) NULL,
    created_at  DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at  DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    -- Mot cap chi co dung 1 dong. Chi co hieu luc vi cap da duoc sap xep.
    CONSTRAINT uq_account_links_pair UNIQUE (user_a_id, user_b_id),
    -- Bat buoc thu tu cap ngay o tang DB.
    CONSTRAINT chk_account_links_ordered CHECK (user_a_id < user_b_id),
    CONSTRAINT chk_account_links_type
        CHECK (link_type IN ('SHARED_DEVICE', 'SHARED_IP', 'MANUAL')),
    CONSTRAINT chk_account_links_status
        CHECK (status IN ('SUSPECTED', 'CONFIRMED', 'CLEARED')),
    CONSTRAINT fk_account_links_user_a FOREIGN KEY (user_a_id)
        REFERENCES users (id) ON DELETE RESTRICT,
    CONSTRAINT fk_account_links_user_b FOREIGN KEY (user_b_id)
        REFERENCES users (id) ON DELETE RESTRICT,
    CONSTRAINT fk_account_links_reviewer FOREIGN KEY (reviewed_by)
        REFERENCES users (id) ON DELETE RESTRICT
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- Hang doi cho nguoi that xem: quet theo status + thoi diem tao.
CREATE INDEX idx_account_links_status_created ON account_links (status, created_at);
-- Job hoa hong tra nguoc: mot user dang bi lien ket voi nhung ai. Can CA HAI
-- chieu vi cap da sap xep nen mot user co the nam o cot a hoac cot b.
CREATE INDEX idx_account_links_user_a ON account_links (user_a_id, status);
CREATE INDEX idx_account_links_user_b ON account_links (user_b_id, status);
