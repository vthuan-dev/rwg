-- ============================================================================
-- RWG Backend - V20260824_01: cho phép gửi ảnh trong chat hỗ trợ.
--
-- MỘT TỆP MỖI TIN NHẮN, thêm cột vào chat_messages chứ không tạo bảng
-- chat_attachments riêng. Bảng riêng chỉ đáng khi một tin có NHIỀU tệp; ở đây
-- gửi ba ảnh nghĩa là ba tin nhắn (đúng cách Telegram và Messenger làm). Nếu tách
-- bảng thì mọi lần tải lịch sử phải join thêm — trên đúng truy vấn nóng nhất của
-- tính năng, để đổi lấy một khả năng không được dùng.
--
-- MỖI THAY ĐỔI MỘT CÂU LỆNH RIÊNG, và KHÔNG dùng mệnh đề `AFTER`:
-- test chạy trên H2 ở chế độ MODE=MySQL, và H2 không nhận nhiều `ADD COLUMN`
-- trong một `ALTER TABLE`, cũng không nhận `AFTER`. Đây là quy ước sẵn có của
-- mọi migration trước (xem V20260821_01__them_payout_methods.sql) — thứ tự cột
-- vật lý không có ý nghĩa gì với truy vấn, nên không đáng để làm test không chạy
-- được.
-- ============================================================================

-- Đường dẫn công khai dạng "/uploads/media/<uuid>.jpg".
-- 512 ký tự: dư nhiều so với nhu cầu hiện tại (khoảng 60 ký tự), nhưng cột này sẽ
-- phải chứa URL đầy đủ nếu sau này chuyển sang lưu trữ đám mây (S3 / Cloudflare
-- R2) — và ALTER trên bảng volume lớn thì đắt hơn hẳn việc dự trù sẵn vài trăm byte.
ALTER TABLE chat_messages
    ADD COLUMN attachment_url VARCHAR(512) NULL;

-- IMAGE là giá trị duy nhất hiện tại. Vẫn giữ cột kiểu thay vì suy ra từ đuôi tệp:
-- khi thêm PDF hoặc video thì client cần biết vẽ bằng thẻ nào NGAY khi đọc tin, và
-- tự phân tích đuôi tệp ở phía client nghĩa là mỗi client (web, iOS, Android) tự
-- viết lại cùng một bảng tra cứu.
ALTER TABLE chat_messages
    ADD COLUMN attachment_type VARCHAR(16) NULL;

-- Tên gốc do người gửi đặt. Tệp trên đĩa mang tên UUID (chống ghi đè và chống
-- "../" trong tên), nên không có cột này thì người nhận chỉ thấy một chuỗi UUID
-- vô nghĩa khi tải về.
ALTER TABLE chat_messages
    ADD COLUMN attachment_name VARCHAR(255) NULL;

-- Dung lượng byte, để giao diện hiện "2.4 MB" mà không phải gọi HEAD lên tệp.
ALTER TABLE chat_messages
    ADD COLUMN attachment_size BIGINT NULL;

-- ---------------------------------------------------------------------------
-- body: NOT NULL -> NULL.
--
-- Gửi MỘT ẢNH KHÔNG KÈM CHỮ là hành vi bình thường và phổ biến nhất trong hỗ trợ
-- (chụp màn hình lỗi rồi gửi luôn). Giữ NOT NULL thì phải lưu chuỗi rỗng, và khi
-- đó không phân biệt được "tin chỉ có ảnh" với "tin có chữ rỗng do lỗi ghi".
-- ---------------------------------------------------------------------------
ALTER TABLE chat_messages
    MODIFY COLUMN body VARCHAR(2000) NULL;

-- ---------------------------------------------------------------------------
-- Chặn tin trống hoàn toàn.
--
-- Sau khi body cho phép NULL, một lỗi ở tầng service có thể tạo ra tin không chữ
-- không ảnh. Nó sẽ hiện thành bong bóng rỗng trên màn hình cả hai phía và không
-- có cách nào biết nó đến từ đâu. Chặn tại DB để lỗi lộ ra ngay lúc ghi.
--
-- Dùng TRIM: body chỉ chứa khoảng trắng cũng là tin trống.
-- ---------------------------------------------------------------------------
ALTER TABLE chat_messages
    ADD CONSTRAINT ck_chat_messages_not_empty
        CHECK ((body IS NOT NULL AND TRIM(body) <> '') OR attachment_url IS NOT NULL);

-- ---------------------------------------------------------------------------
-- Hai cột đính kèm phải cùng có hoặc cùng không.
--
-- attachment_url mà thiếu attachment_type là bản ghi client không biết vẽ thế nào;
-- attachment_type mà thiếu url là kiểu tệp trỏ vào hư không. Cả hai đều là dữ liệu
-- hỏng lặng lẽ — không gây lỗi lúc ghi, chỉ hiện thành ô ảnh vỡ nhiều ngày sau.
-- ---------------------------------------------------------------------------
ALTER TABLE chat_messages
    ADD CONSTRAINT ck_chat_messages_attachment_pair
        CHECK ((attachment_url IS NULL AND attachment_type IS NULL)
            OR (attachment_url IS NOT NULL AND attachment_type IS NOT NULL));
