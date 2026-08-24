package com.rwg.chat.service;

import com.rwg.chat.domain.ChatAttachmentType;
import com.rwg.chat.domain.ChatMessage;
import com.rwg.common.ApiException;
import com.rwg.common.ErrorCode;
import com.rwg.media.service.MediaStorageService;

/**
 * Kiểm tra và dựng thông tin đính kèm từ dữ liệu client gửi lên.
 *
 * DÙNG CHUNG cho cả người chơi và nhân sự — trái với việc tách {@code ChatService} /
 * {@code AdminChatService}. Lý do khác nhau: việc tách hai service là để hai phía có
 * QUYỀN khác nhau, còn ở đây quy tắc "đường dẫn phải do hệ thống sinh, có ảnh hoặc có
 * chữ" là giống nhau tuyệt đối. Viết hai bản thì bản của khu quản trị sẽ là bản bị bỏ
 * quên khi quy tắc thay đổi.
 */
final class ChatAttachments {

    /**
     * Tiền tố hợp lệ của đường dẫn đính kèm.
     *
     * VÌ SAO PHẢI KIỂM TRA: client gửi lên một chuỗi bất kỳ trong trường
     * {@code attachmentUrl}. Không kiểm thì nó có thể là
     * {@code https://trang-lua-dao.example/anh.jpg} — và tin nhắn đó hiện trên màn hình
     * người chơi dưới danh nghĩa nhân viên hỗ trợ, hoặc ngược lại nhân sự mở một URL do
     * người chơi kiểm soát. Cũng có thể là {@code ../../etc/passwd} nếu chỉ kiểm tra
     * bằng "có chứa tiền tố".
     */
    private static final String URL_PREFIX = MediaStorageService.CHAT_URL_PREFIX;

    private ChatAttachments() {
    }

    /**
     * Gắn đính kèm vào tin nếu client có gửi, sau khi kiểm tra tính hợp lệ.
     *
     * @param body nội dung chữ; rỗng thì BẮT BUỘC phải có đính kèm.
     */
    static ChatMessage applyTo(ChatMessage message, String body, String attachmentUrl,
                               String attachmentName, Long attachmentSize) {

        boolean hasText = body != null && !body.isBlank();
        boolean hasAttachment = attachmentUrl != null && !attachmentUrl.isBlank();

        // Kiểm tra ở service chứ không bằng annotation: điều kiện là quan hệ GIỮA hai
        // trường ("có cái này HOẶC có cái kia"), và Bean Validation trên một trường
        // không nhìn thấy trường còn lại.
        if (!hasText && !hasAttachment) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR,
                    "Tin nhắn phải có nội dung hoặc tệp đính kèm", null,
                    "error.chat.empty_message");
        }

        if (!hasAttachment) {
            return message;
        }

        requireSystemPath(attachmentUrl);

        // Tên và dung lượng KHÔNG bắt buộc: chúng chỉ để hiển thị, và một client cũ
        // không gửi kèm cũng không được làm hỏng việc gửi ảnh. Thiếu tên thì dùng phần
        // cuối đường dẫn, thiếu dung lượng thì để 0 và giao diện không hiện kích cỡ.
        String name = attachmentName == null || attachmentName.isBlank()
                ? attachmentUrl.substring(attachmentUrl.lastIndexOf('/') + 1)
                : attachmentName;

        long size = attachmentSize == null || attachmentSize < 0 ? 0L : attachmentSize;

        return message.withAttachment(attachmentUrl, ChatAttachmentType.IMAGE, name, size);
    }

    /**
     * Đường dẫn phải trỏ vào thư mục media của hệ thống và không được thoát ra ngoài.
     *
     * Chặn cả {@code ".."} và {@code "\\"}: trên Windows dấu gạch chéo ngược cũng là dấu
     * phân cách thư mục, nên chỉ chặn {@code ".."} thì vẫn còn đường đi vòng.
     */
    private static void requireSystemPath(String url) {
        boolean valid = url.startsWith(URL_PREFIX)
                && !url.contains("..")
                && !url.contains("\\")
                // Sau tiền tố phải còn ít nhất một ký tự tên tệp.
                && url.length() > URL_PREFIX.length();

        if (!valid) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR,
                    "Đường dẫn tệp đính kèm không hợp lệ", null,
                    "error.chat.attachment.bad_url");
        }
    }
}
