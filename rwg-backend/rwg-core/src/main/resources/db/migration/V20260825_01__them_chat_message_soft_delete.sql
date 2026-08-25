-- ============================================================================
-- RWG Backend - V20260825_01: soft delete tin nhắn chat.
--
-- Tin nhắn bị "xóa" được ĐÁNH DẤU ẨN thay vì DELETE khỏi bảng. Chat hỗ trợ
-- là nơi diễn ra cam kết về tiền ("em duyệt cho anh trong 10 phút"), nên mỗi
-- tin nhắn là căn cứ khi có khiếu nại. Xóa hẳn nghĩa là không còn gì để đối
-- chiếu khi tranh chấp xảy ra ba tháng sau.
--
-- Với phía người dùng (người chơi và nhân sự), kết quả GIỐNG HỆT xóa thật:
-- tin biến mất và không bao giờ hiện lại. Khác biệt duy nhất là dữ liệu còn
-- trong cơ sở dữ liệu để tra khi cần.
--
-- Lưu ĐẦY ĐỦ thông tin về người xóa — mỗi cột riêng, KHÔNG gộp vào JSON:
-- deleted_by_username là thứ đọc được ngay khi tra log, và người xóa có thể
-- đã rời việc và bị đổi tên sau đó.
--
-- MỖI THAY ĐỔI MỘT CÂU LỆNH RIÊNG — xem lý do ở V20260824_01.
-- ============================================================================

-- Thời điểm xóa. NULL = chưa xóa.
ALTER TABLE chat_messages
    ADD COLUMN deleted_at DATETIME(6) NULL;

-- Mã UUID của người thực hiện xóa (nhân sự khu quản trị).
ALTER TABLE chat_messages
    ADD COLUMN deleted_by CHAR(36) NULL;

-- Tên đăng nhập chụp lại lúc xóa — để tra được dù người đó đã đổi tên.
ALTER TABLE chat_messages
    ADD COLUMN deleted_by_username VARCHAR(50) NULL;

-- Index để không phải quét toàn bảng khi lọc tin chưa xóa.
-- Partial index (deleted_at IS NULL) không có trong MySQL standard — dùng index
-- thường trên cột nullable; optimizer chọn NULL-skip khi lọc IS NULL trên MySQL 8.
CREATE INDEX idx_chat_messages_deleted_at ON chat_messages (deleted_at);
