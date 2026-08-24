-- ============================================================================
-- RWG Backend - V20260824_02: thẻ duyệt lệnh rút ngay trong luồng chat hỗ trợ.
--
-- Khi người chơi tạo lệnh rút, một tin SYSTEM được chèn vào luồng chat của họ,
-- mang mã lệnh rút. Khu quản trị vẽ tin đó thành một thẻ có nút duyệt/từ chối để
-- xử lý tại chỗ, không phải rời khỏi cuộc trò chuyện.
--
-- CHỈ LƯU MÃ LỆNH, KHÔNG lưu số tiền hay trạng thái. Chép chúng vào đây nghĩa là
-- có hai nguồn sự thật cho cùng một lệnh: một lệnh bị duyệt ở trang "Duyệt Nạp &
-- Rút Tiền" sẽ để lại thẻ trong chat hiện "chờ duyệt" vĩnh viễn, và người vận
-- hành bấm duyệt lần thứ hai. Số tiền và trạng thái đọc từ payment_orders mỗi
-- lần tải luồng.
--
-- MỖI THAY ĐỔI MỘT CÂU LỆNH RIÊNG, KHÔNG dùng `AFTER`: test chạy trên H2 ở chế
-- độ MODE=MySQL và H2 không nhận nhiều ADD COLUMN trong một ALTER TABLE, cũng
-- không nhận AFTER. Đây là quy ước sẵn có của mọi migration trước — xem
-- V20260824_01__them_chat_attachment.sql.
-- ============================================================================

-- Mã lệnh rút mà thẻ trỏ tới. NULL với mọi tin nhắn thường.
--
-- KHÔNG đặt FOREIGN KEY tới payment_orders. Hai lý do:
-- 1. Bảng đó có PK composite (id, created_at) theo DECISIONS.md mục (b), nên FK
--    sẽ buộc cột này phải đi kèm một cột thời gian nữa mà không dùng để làm gì.
-- 2. Cùng lý do với sender_id: tin nhắn phải sống lâu hơn những bản ghi nó nhắc
--    tới. Khi tra soát khiếu nại nhiều tháng sau, việc một lệnh cũ đã bị dọn
--    khỏi bảng không được phép làm biến mất dấu vết trao đổi về nó.
ALTER TABLE chat_messages
    ADD COLUMN withdrawal_order_id CHAR(36) NULL;

-- ---------------------------------------------------------------------------
-- Ai được đọc tin này: ALL | STAFF.
--
-- Cần cột riêng thay vì suy ra từ "có withdrawal_order_id thì là tin nội bộ":
-- quyền đọc là một khái niệm độc lập với việc tin đó nói về cái gì, và các tin
-- chỉ dành cho nhân sự sau này (ghi chú nội bộ về một khách, cảnh báo rủi ro) sẽ
-- dùng lại đúng cột này mà không phải sửa lại chỗ lọc.
--
-- DEFAULT 'ALL' để toàn bộ tin nhắn đang có giữ nguyên hành vi hiện tại.
-- ---------------------------------------------------------------------------
ALTER TABLE chat_messages
    ADD COLUMN visible_to VARCHAR(8) NOT NULL DEFAULT 'ALL';

-- ---------------------------------------------------------------------------
-- Thẻ duyệt tiền BẮT BUỘC là tin nội bộ.
--
-- Một thẻ có nút "duyệt lệnh rút" lọt sang phía người chơi là sự cố phân quyền,
-- không phải lỗi hiển thị. Tầng service đã lọc theo visible_to, nhưng một đường
-- ghi mới quên đặt cờ sẽ không báo lỗi gì ở tầng đó — nó chỉ hiện ra khi có
-- người chơi kể lại rằng họ nhìn thấy nút bấm tiền. Chặn tại DB để sai sót đó
-- lộ ra ngay lúc ghi.
-- ---------------------------------------------------------------------------
ALTER TABLE chat_messages
    ADD CONSTRAINT ck_chat_messages_withdrawal_staff_only
        CHECK (withdrawal_order_id IS NULL OR visible_to = 'STAFF');

-- Tìm lại thẻ theo mã lệnh — dùng khi cần đối chiếu một lệnh rút với đoạn trao
-- đổi quanh nó. Không có index thì mỗi lần tra là một lần quét bảng lớn nhất
-- của tính năng chat.
CREATE INDEX idx_chat_messages_withdrawal
    ON chat_messages (withdrawal_order_id);
