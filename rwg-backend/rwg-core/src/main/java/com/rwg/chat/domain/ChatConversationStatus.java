package com.rwg.chat.domain;

/**
 * Trạng thái một luồng hội thoại hỗ trợ.
 *
 * CHỈ HAI trạng thái. Cân nhắc thêm PENDING/WAITING_STAFF nhưng bỏ: thông tin
 * "đang chờ nhân sự trả lời" đã nằm sẵn trong {@code unread_for_admin > 0} và
 * {@code assigned_admin_id IS NULL}. Thêm một cột trạng thái nói lại điều đó tạo
 * ra hai nguồn sự thật, và chúng sẽ lệch nhau ngay lần đầu có ai quên cập nhật.
 */
public enum ChatConversationStatus {

    /** Đang hoạt động — hiện trong hàng đợi của khu quản trị. */
    OPEN,

    /**
     * Nhân sự đã đóng vì vấn đề đã xử lý xong.
     *
     * KHÔNG phải trạng thái cuối: người chơi gửi tin mới sẽ tự mở lại thành OPEN.
     * Nếu đóng là vĩnh viễn thì người chơi gửi tiếp mà không ai thấy — cách hỏng
     * tệ nhất có thể của một hệ thống hỗ trợ.
     */
    CLOSED
}
