package com.rwg.chat.domain;

/**
 * Ai được đọc một tin nhắn trong luồng hỗ trợ.
 *
 * Là khái niệm ĐỘC LẬP với {@link ChatSenderType}: một tin do hệ thống chèn có thể
 * dành cho cả hai phía ("cuộc trò chuyện đã đóng") hoặc chỉ dành cho nhân sự (thẻ
 * duyệt lệnh rút). Suy quyền đọc từ loại người gửi sẽ gộp hai thứ đó lại và không
 * còn cách nào diễn tả sự khác biệt.
 */
public enum ChatVisibility {

    /** Cả người chơi và nhân sự đều đọc được — mặc định của mọi tin nhắn. */
    ALL,

    /**
     * CHỈ nhân sự quản trị đọc được.
     *
     * Dùng cho những gì thuộc quy trình xử lý nội bộ mà người chơi không nên thấy:
     * thẻ duyệt lệnh rút (có nút chuyển tiền), và về sau là ghi chú nội bộ hoặc
     * cảnh báo rủi ro về chính người đang trò chuyện.
     */
    STAFF
}
