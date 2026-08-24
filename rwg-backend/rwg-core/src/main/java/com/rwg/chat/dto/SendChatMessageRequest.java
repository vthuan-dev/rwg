package com.rwg.chat.dto;

import com.rwg.chat.domain.ChatMessage;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/**
 * Một tin nhắn gửi lên server (dùng cho CẢ người chơi và nhân sự quản trị).
 *
 * MỘT DTO cho hai phía: nội dung gửi đi giống nhau hoàn toàn, khác biệt duy nhất
 * là ai đang gọi — điều đó đã nằm trong JWT nên không cần đưa vào body. Tách thành
 * hai record y hệt nhau chỉ tạo ra hai chỗ phải sửa mỗi khi thêm một trường.
 */
public record SendChatMessageRequest(

        /**
         * Nội dung chữ. CÓ THỂ RỖNG khi có {@link #attachmentUrl}.
         *
         * KHÔNG còn {@code @NotBlank}: gửi một ảnh không kèm chữ là hành vi bình thường
         * và phổ biến nhất trong hỗ trợ (chụp màn hình lỗi rồi gửi luôn). Điều kiện
         * "phải có chữ HOẶC có ảnh" không diễn tả được bằng annotation trên một trường,
         * nên nó được kiểm ở service — nơi thấy được cả hai trường cùng lúc.
         */
        @Size(max = ChatMessage.MAX_BODY_LENGTH, message = "{validation.chat.body.size}")
        String body,

        /**
         * Id do client sinh để CHỐNG GỬI TRÙNG. Không bắt buộc, nhưng client nên gửi.
         *
         * Kịch bản cần nó: người dùng bấm gửi, mạng chập chờn, response mất trên
         * đường về, client thử lại. Không có id này thì server coi đó là hai tin
         * khác nhau và người nhận thấy nội dung y hệt hai lần.
         *
         * Client sinh chứ không server sinh: chỉ client biết hai lần gửi đó thực ra
         * là CÙNG một hành động của người dùng.
         */
        UUID clientMsgId,

        /**
         * Đường dẫn ảnh đã tải lên trước đó qua endpoint upload.
         *
         * HAI BƯỚC (upload rồi mới gửi tin) thay vì một request multipart: người dùng
         * chọn ảnh xong thấy ngay ảnh đã lên kèm thanh tiến trình, rồi mới gõ chữ và
         * bấm gửi. Gộp một bước thì toàn bộ thời gian tải lên nằm SAU lần bấm gửi, và
         * giao diện chỉ biết đứng chờ — trên mạng 3G với ảnh 8MB đó là hàng chục giây
         * không có phản hồi gì.
         *
         * Server KHÔNG tin giá trị này vô điều kiện: service kiểm tra nó đúng dạng
         * đường dẫn do chính hệ thống sinh ra, nếu không thì đây là chỗ để chèn URL
         * bất kỳ vào tin nhắn của người khác.
         */
        String attachmentUrl,

        /** Tên tệp gốc, do bước upload trả về. */
        String attachmentName,

        /** Dung lượng byte, do bước upload trả về. */
        Long attachmentSize
) {
}
