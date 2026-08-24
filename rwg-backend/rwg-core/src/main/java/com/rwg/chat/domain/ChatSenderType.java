package com.rwg.chat.domain;

/**
 * Ai gửi một tin nhắn.
 *
 * Lưu LOẠI người gửi thành cột riêng thay vì suy ra từ {@code sender_id} bằng cách
 * tra vai trò trong bảng users: vai trò có thể đổi. Một nhân sự hỗ trợ bị hạ thành
 * PLAYER thì mọi tin họ từng trả lời sẽ đột nhiên hiện ở phía người chơi, và cả
 * đoạn hội thoại trở nên vô nghĩa.
 */
public enum ChatSenderType {

    /** Người chơi gửi. */
    PLAYER,

    /** Nhân sự quản trị gửi (ADMIN / FINANCE / SUPPORT). */
    STAFF,

    /**
     * Hệ thống tự chèn — ví dụ "Nhân viên X đã tiếp nhận", "Cuộc trò chuyện đã đóng".
     *
     * Có loại này để những dòng đó nằm ĐÚNG vị trí theo thời gian trong luồng chat
     * thay vì phải dựng một dòng thời gian riêng ở giao diện. Chúng KHÔNG được tính
     * vào bộ đếm chưa đọc: viên đỏ báo tin mới phải có nghĩa là "có người nói với
     * bạn", nếu nó nhảy vì một dòng thông báo tự động thì người dùng sẽ học cách
     * bỏ qua nó.
     */
    SYSTEM
}
