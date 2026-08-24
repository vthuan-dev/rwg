-- ============================================================================
-- RWG Backend - V20260823_02: bảng notifications.
--
-- Thông báo cho người chơi khi tiền vào/ra ví (admin cộng/trừ, nạp xong, rút
-- được duyệt hoặc bị từ chối) và tin chung do admin đăng.
--
-- VÌ SAO PHẢI LƯU DB thay vì chỉ đẩy WebSocket: tình huống chính là admin cộng
-- tiền, và việc đó gần như luôn xảy ra lúc người chơi ĐANG OFFLINE. Chỉ đẩy qua
-- kênh thời gian thực thì tin bay vào hư không.
--
-- user_id NULL = TIN CHUNG cho mọi người. Cách còn lại là nhân bản một dòng cho
-- mỗi tài khoản, nhưng đăng một tin bảo trì cho 10.000 tài khoản sẽ tạo 10.000
-- dòng cho cùng một mẩu tin — vừa tốn chỗ vừa làm việc sửa/xoá tin trở nên phải
-- cập nhật hàng loạt.
--
-- title_key + params_json, KHÔNG lưu câu đã dịch sẵn: lưu "Admin đã cộng $500
-- vào ví của bạn" thì người dùng đổi sang tiếng Trung vẫn thấy câu tiếng Việt vì
-- câu đó đã đóng băng lúc ghi. Lưu khoá dịch cùng tham số thì mỗi lần xem đều
-- dịch theo ngôn ngữ hiện tại.
--
-- read_at là MỐC THỜI GIAN chứ không phải cờ is_read: cùng một cột trả lời được
-- cả "đã đọc chưa" và "đọc lúc nào" — cần cho việc tra cứu khi người chơi khiếu
-- nại rằng họ chưa từng được thông báo.
--
-- PK composite (id, created_at): partition-ready theo thời gian (DECISIONS.md
-- mục b). Đây là bảng ghi nhiều, sẽ lớn nhanh.
-- ============================================================================

CREATE TABLE notifications (
    id          CHAR(36)     NOT NULL,
    -- NULL = tin chung cho mọi người. Không đặt FK NOT NULL vì lẽ đó.
    user_id     CHAR(36)     NULL,
    -- DEPOSIT_COMPLETED | WITHDRAWAL_APPROVED | WITHDRAWAL_REJECTED
    -- | ADMIN_CREDIT | ADMIN_DEBIT | ANNOUNCEMENT
    type        VARCHAR(32)  NOT NULL,
    -- Khoá dịch, vd "notification.admin_credit". Dài 64 đủ cho mọi khoá hiện có.
    title_key   VARCHAR(64)  NOT NULL,
    -- Tham số cho khoá dịch, dạng JSON phẳng, vd {"amount":"500.00"}.
    -- Dùng VARCHAR không JSON: nội dung chỉ để đọc nguyên khối rồi trả cho
    -- client, không bao giờ cần truy vấn theo trường bên trong. Kiểu JSON của
    -- MySQL sẽ thêm chi phí phân tích cú pháp mỗi lần ghi mà không đổi lại gì.
    params_json VARCHAR(512) NULL,
    -- Nội dung tự do cho tin chung do admin viết. NULL với thông báo sinh tự
    -- động (những tin đó dùng title_key + params_json).
    body        VARCHAR(1024) NULL,
    read_at     DATETIME(6)  NULL,
    created_at  DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id, created_at),
    CONSTRAINT fk_notifications_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- Truy vấn chính: "thông báo của tôi, mới nhất trước". Index gộp cả created_at
-- để việc sắp xếp dùng luôn index thay vì phải sắp xếp lại trong bộ nhớ.
CREATE INDEX idx_notifications_user_created ON notifications (user_id, created_at);

-- Đếm số chưa đọc: cột read_at đứng sau user_id để lọc NULL trong cùng index.
CREATE INDEX idx_notifications_user_unread ON notifications (user_id, read_at);

-- Lấy tin chung (user_id IS NULL) xếp theo thời gian. Không dùng lại index trên
-- vì MySQL đặt các dòng NULL ở đầu index, nên một index riêng theo type gọn hơn
-- cho truy vấn "tin chung mới nhất".
CREATE INDEX idx_notifications_broadcast ON notifications (type, created_at);
