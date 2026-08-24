-- V20260820_12__them_bang_banners.sql
-- Thêm bảng banners lưu trữ thông tin video/ảnh banner quảng cáo trang chủ.
-- Thiết kế theo tiêu chuẩn DECISIONS.md (xem root repository):
-- - PK VARCHAR(36) UUID v4
-- - Cột media_type ('VIDEO' hoặc 'IMAGE')
-- - Cột media_url lưu đường dẫn tới file lưu trữ công khai
-- - Cột is_active đánh dấu trạng thái hiển thị
-- - Cột sort_order để sắp xếp thứ tự hiển thị banner

CREATE TABLE IF NOT EXISTS banners (
    id VARCHAR(36) NOT NULL,
    title VARCHAR(255) NOT NULL,
    media_type VARCHAR(20) NOT NULL,
    media_url VARCHAR(512) NOT NULL,
    link_url VARCHAR(512) NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    sort_order INT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_banners PRIMARY KEY (id),
    CONSTRAINT chk_banners_media_type CHECK (media_type IN ('VIDEO', 'IMAGE'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_banners_active_order ON banners(is_active, sort_order ASC, created_at DESC);
