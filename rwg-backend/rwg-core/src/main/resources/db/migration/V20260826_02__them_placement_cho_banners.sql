-- V20260826_02__them_placement_cho_banners.sql
-- Thêm cột placement để tách banner trang chủ khỏi ảnh khuyến mãi trong khung chat.
--
-- VÌ SAO TÁI DÙNG BẢNG `banners` thay vì tạo bảng `chat_promos` riêng: toàn bộ phần
-- khó đã có sẵn và đã chạy đúng trong luồng banner — tải lên multipart, kiểm chữ ký
-- byte đầu tệp (chặn tệp thực thi đổi tên thành .jpg), trần dung lượng theo loại,
-- sinh tên UUID chống ghi đè, xoá kèm tệp trên đĩa, endpoint phục vụ công khai.
-- Bảng riêng nghĩa là viết lại hoặc gọi chéo tất cả những thứ đó, và mỗi bản sao là
-- một chỗ để lệch nhau về sau.

ALTER TABLE banners
    ADD COLUMN placement VARCHAR(32) NOT NULL DEFAULT 'HOME_CAROUSEL' AFTER title;

-- DEFAULT 'HOME_CAROUSEL' khiến mọi banner đang có tự động thành banner trang chủ —
-- đúng, vì trước khi có cột này thì bảng chỉ chứa banner trang chủ. Nhờ vậy không cần
-- script cập nhật dữ liệu, và cột NOT NULL nên không tồn tại trạng thái "chưa biết
-- thuộc khu nào".

ALTER TABLE banners
    ADD CONSTRAINT chk_banners_placement CHECK (placement IN ('HOME_CAROUSEL', 'CHAT_PROMO'));

-- Chỉ mục cũ dẫn đầu bằng `is_active`, nhưng từ nay MỌI truy vấn đều lọc `placement`
-- trước — nên cột dẫn đầu đó không còn dùng được nữa.
DROP INDEX idx_banners_active_order ON banners;

CREATE INDEX idx_banners_placement_active_order
    ON banners (placement, is_active, sort_order ASC, created_at DESC);
