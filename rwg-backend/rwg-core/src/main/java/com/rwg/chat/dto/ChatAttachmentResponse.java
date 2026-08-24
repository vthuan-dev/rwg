package com.rwg.chat.dto;

import com.rwg.media.service.MediaStorageService;

/**
 * Kết quả tải một tệp đính kèm lên, trả về cho client trước khi nó gửi tin nhắn.
 *
 * Client giữ nguyên ba giá trị này rồi gửi lại ở bước tạo tin. Server không lưu trạng
 * thái "đã upload nhưng chưa gửi" ở đâu cả: làm vậy sẽ cần một bảng tạm cùng công việc
 * dọn rác định kỳ, chỉ để phục vụ khoảng thời gian vài giây giữa hai request.
 *
 * Hệ quả có chủ ý: người dùng chọn ảnh rồi đóng trang mà không gửi thì tệp vẫn nằm trên
 * đĩa. Đó là rác, nhưng là rác có giới hạn — hạn mức upload đã chặn việc tạo rác quy mô
 * lớn, và một công việc dọn tệp không được tham chiếu có thể thêm sau mà không phải đổi
 * gì trong luồng này.
 */
public record ChatAttachmentResponse(

        /** Đường dẫn công khai dạng "/uploads/media/<uuid>.jpg". */
        String url,

        /** Tên tệp gốc do người dùng đặt. */
        String name,

        /** Dung lượng byte. */
        long size,

        /** IMAGE. */
        String type
) {

    public static ChatAttachmentResponse image(MediaStorageService.StoredAttachment stored) {
        return new ChatAttachmentResponse(stored.url(), stored.originalName(),
                stored.sizeBytes(), "IMAGE");
    }
}
