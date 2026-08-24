package com.rwg.chat.domain;

/**
 * Loại tệp đính kèm trong tin nhắn hỗ trợ.
 *
 * Hiện chỉ có IMAGE. Vẫn là enum thay vì một cờ boolean {@code hasImage}: khi thêm
 * PDF hay video, cờ boolean sẽ phải thành enum và mọi chỗ đọc nó đều phải sửa — kể cả
 * ở client. Enum một giá trị thì lần mở rộng đó chỉ là thêm một hằng số.
 *
 * Client dùng giá trị này để quyết định vẽ bằng thẻ nào, thay vì tự phân tích đuôi tệp
 * trong URL. Tự phân tích nghĩa là mỗi client (web, iOS, Android) tự viết lại cùng một
 * bảng tra cứu, và ba bảng đó sẽ lệch nhau.
 */
public enum ChatAttachmentType {

    /** Ảnh hiển thị trực tiếp trong bong bóng chat (PNG, JPG, WebP). */
    IMAGE
}
