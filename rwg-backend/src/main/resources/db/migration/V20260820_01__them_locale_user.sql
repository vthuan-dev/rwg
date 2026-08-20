-- =============================================================================
-- Thêm cột locale cho bảng users (i18n chặng 2 - Phase a).
-- Ghi chú: quy ước khóa chính composite (id, created_at) trong DECISIONS.md mục b
-- CHỈ áp dụng khi TẠO MỚI bảng high-volume; đây là ALTER trên bảng users có sẵn
-- nên KHÔNG áp dụng quy ước này.
-- Giá trị hợp lệ: en, vi, zh, ja (validate ở tầng API - UpdateLocaleRequest).
-- =============================================================================
ALTER TABLE users ADD COLUMN locale VARCHAR(8) NOT NULL DEFAULT 'en';
