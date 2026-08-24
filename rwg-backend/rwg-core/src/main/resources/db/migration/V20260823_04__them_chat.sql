-- ============================================================================
-- RWG Backend - V20260823_04: chat hỗ trợ hai chiều giữa người chơi và nhân sự
-- quản trị (live support).
--
-- MÔ HÌNH: MỘT luồng hội thoại VĨNH VIỄN cho mỗi người chơi, không phải ticket.
-- Cách còn lại là mỗi vấn đề một luồng mới, nhưng khi đó người chơi phải tự
-- quyết định "cái này là vấn đề mới hay vấn đề cũ" — phần lớn họ sẽ gõ tiếp vào
-- luồng cũ bất kể đã đóng hay chưa, nên hàng đợi ticket sẽ đầy những ticket
-- trống mở ra rồi bỏ đó. Một luồng liên tục cũng cho nhân sự đọc được toàn bộ
-- lịch sử của người đó ngay tại chỗ mà không phải nhảy qua ticket cũ.
--
-- TÁCH LÀM HAI BẢNG chứ không dồn vào một: hộp thư của nhân sự cần sắp xếp theo
-- "hội thoại có tin mới nhất" và đếm số chưa đọc cho từng luồng. Nếu chỉ có bảng
-- tin nhắn thì mỗi lần render hộp thư phải GROUP BY trên bảng lớn nhất của tính
-- năng, và việc đó lặp lại mỗi 5 giây cho mỗi nhân sự đang mở màn hình.
-- ============================================================================

-- ---------------------------------------------------------------------------
-- chat_conversations: một dòng cho mỗi người chơi.
--
-- KHÔNG dùng PK composite (id, created_at) như quy ước ở DECISIONS.md mục (b):
-- quy ước đó dành cho bảng volume lớn cần partition theo thời gian. Bảng này bị
-- chặn trên bởi SỐ NGƯỜI CHƠI (1 dòng/người) nên nó không lớn theo thời gian, và
-- PK composite sẽ làm mọi FK từ chat_messages phải mang thêm cột thời gian một
-- cách vô nghĩa.
-- ---------------------------------------------------------------------------
CREATE TABLE chat_conversations (
    id                   CHAR(36)     NOT NULL,

    -- UNIQUE: mỗi người chơi đúng một luồng. Ràng buộc ở tầng DB chứ không chỉ
    -- ở tầng service — hai request đầu tiên của cùng một người gửi song song đều
    -- thấy "chưa có luồng" và đều tạo mới, nên nếu không có UNIQUE thì người đó
    -- có hai luồng và nhân sự chỉ thấy một nửa cuộc trò chuyện.
    user_id              CHAR(36)     NOT NULL,

    -- OPEN | CLOSED. Người chơi gửi tin mới vào luồng đã CLOSED sẽ tự mở lại
    -- (xem ChatConversation.appendPlayerMessage).
    status               VARCHAR(16)  NOT NULL DEFAULT 'OPEN',

    -- Nhân sự đang phụ trách. NULL = chưa ai nhận, vẫn nằm trong hàng đợi chung.
    -- KHÔNG đặt FK: nhân sự nghỉ việc và bị xoá tài khoản thì luồng chat vẫn phải
    -- còn nguyên, còn ON DELETE SET NULL sẽ âm thầm xoá dấu vết ai đã phụ trách.
    assigned_admin_id    CHAR(36)     NULL,

    -- ===== Ba cột phi chuẩn hoá dưới đây là CỐ TÌNH =====
    -- Chúng lặp lại thông tin đã có trong chat_messages. Đổi lại, truy vấn hộp
    -- thư (truy vấn nóng nhất của tính năng) đọc được mọi thứ cần hiển thị từ
    -- CHỈ bảng này, không cần join hay đếm gì trên bảng tin nhắn.
    -- Mọi đường ghi vào chat_messages PHẢI cập nhật chúng trong CÙNG transaction.
    last_message_at      DATETIME(6)  NULL,
    -- Đoạn xem trước trong hộp thư. 160 ký tự vừa đủ hai dòng trên màn hình
    -- desktop; dài hơn cũng bị CSS cắt nên lưu thêm là lưu thứ không ai đọc.
    last_message_preview VARCHAR(160) NULL,
    -- Hai bộ đếm RIÊNG cho hai phía: cùng một tin nhắn là "chưa đọc" với bên này
    -- và "đã gửi" với bên kia, nên một cột chung không diễn tả được.
    unread_for_admin     INT          NOT NULL DEFAULT 0,
    unread_for_player    INT          NOT NULL DEFAULT 0,

    created_at           DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at           DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),

    PRIMARY KEY (id),
    CONSTRAINT uk_chat_conversations_user UNIQUE (user_id),
    CONSTRAINT fk_chat_conversations_user FOREIGN KEY (user_id)
        REFERENCES users (id) ON DELETE CASCADE,
    -- Bộ đếm âm là dấu hiệu logic tăng/giảm đã lệch ở đâu đó. Chặn tại DB để lỗi
    -- lộ ra ngay lúc ghi, thay vì hiện thành viên đỏ "-3 tin chưa đọc" trên giao diện.
    CONSTRAINT ck_chat_conversations_unread_admin CHECK (unread_for_admin >= 0),
    CONSTRAINT ck_chat_conversations_unread_player CHECK (unread_for_player >= 0)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- Hộp thư: "các luồng đang mở, tin mới nhất lên đầu".
-- last_message_at nằm trong index để việc sắp xếp dùng luôn index thay vì phải
-- sắp lại toàn bộ kết quả trong bộ nhớ.
CREATE INDEX idx_chat_conversations_status_last
    ON chat_conversations (status, last_message_at);

-- Bộ lọc "của tôi" của từng nhân sự.
CREATE INDEX idx_chat_conversations_assigned
    ON chat_conversations (assigned_admin_id, last_message_at);

-- ---------------------------------------------------------------------------
-- chat_messages: bảng volume lớn — PK composite (id, created_at) theo
-- DECISIONS.md mục (b), partition-ready theo thời gian.
-- ---------------------------------------------------------------------------
CREATE TABLE chat_messages (
    id              CHAR(36)      NOT NULL,
    created_at      DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),

    conversation_id CHAR(36)      NOT NULL,

    -- PLAYER | STAFF | SYSTEM.
    -- SYSTEM dành cho các dòng do hệ thống chèn ("Nhân viên A đã tiếp nhận"),
    -- hiển thị khác hẳn bong bóng chat của người thật.
    sender_type     VARCHAR(8)    NOT NULL,

    -- NULL với sender_type = SYSTEM. KHÔNG đặt FK tới users: tin nhắn phải sống
    -- lâu hơn tài khoản gửi nó — khi tra soát khiếu nại sáu tháng sau, việc tài
    -- khoản đó đã bị xoá không được phép làm biến mất nội dung đã trao đổi.
    sender_id       CHAR(36)      NULL,

    -- Tên hiển thị CHỤP LẠI ở thời điểm gửi.
    -- Cố tình lặp dữ liệu của users.username: join lấy tên hiện tại sẽ khiến bản
    -- ghi lịch sử tự đổi theo, nên đoạn hội thoại sáu tháng trước hiện tên mới
    -- của người đã đổi tên — làm lịch sử chat không còn dùng được để đối chiếu.
    sender_username VARCHAR(32)   NULL,

    body            VARCHAR(2000) NOT NULL,

    -- Mốc phía ĐỐI DIỆN đã xem tin này. Là thời điểm chứ không phải cờ boolean,
    -- cùng lý do với notifications.read_at: trả lời được cả "đã xem chưa" và
    -- "xem lúc nào" khi phải tra cứu "tôi không được ai trả lời".
    read_at         DATETIME(6)   NULL,

    -- Id do client sinh, dùng để CHỐNG GỬI TRÙNG.
    -- Mạng chập chờn: client gửi tin, response mất trên đường về, client thử lại.
    -- Không có cột này thì người nhận thấy tin đó hai lần. Có nó thì lần thử thứ
    -- hai đụng UNIQUE và server trả về đúng bản ghi đã tạo.
    client_msg_id   CHAR(36)      NULL,

    PRIMARY KEY (id, created_at),
    CONSTRAINT fk_chat_messages_conversation FOREIGN KEY (conversation_id)
        REFERENCES chat_conversations (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- UNIQUE chống gửi trùng. PHẢI kèm created_at để hợp lệ khi bảng được partition
-- (DECISIONS.md mục b: mọi UNIQUE của bảng volume lớn phải chứa cột partition).
-- MySQL cho nhiều dòng NULL trong UNIQUE index, nên tin do nhân sự hoặc hệ thống
-- gửi (client_msg_id NULL) không bị ràng buộc này chạm tới.
CREATE UNIQUE INDEX uk_chat_messages_client_msg
    ON chat_messages (conversation_id, client_msg_id, created_at);

-- Tải lịch sử một luồng, mới nhất trước (keyset pagination theo created_at).
CREATE INDEX idx_chat_messages_conversation_created
    ON chat_messages (conversation_id, created_at);

-- Đánh dấu đã đọc theo phía: "mọi tin của PLAYER trong luồng này còn read_at NULL".
CREATE INDEX idx_chat_messages_unread
    ON chat_messages (conversation_id, sender_type, read_at);
