package com.rwg.settings.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Yêu cầu cập nhật một mục cấu hình chữ.
 *
 * <h2>CLASS THƯỜNG, KHÔNG PHẢI RECORD</h2>
 * Jackson 3 (bản dùng trong dự án này là {@code tools.jackson}) không dựng được record từ
 * JSON nếu thiếu {@code -parameters} lúc biên dịch, và lỗi đó biểu hiện thành HTTP 500 chứ
 * không phải 400 — rất dễ chẩn đoán sai. Mọi DTO NHẬN request body ở đây đều là class
 * thường có constructor không tham số. DTO trả về thì dùng record bình thường.
 */
public class UpdateAppSettingRequest {

    /**
     * Nội dung mới.
     *
     * TRẦN 4000 KÝ TỰ dù cột là TEXT: cột không giới hạn nghĩa là một lần dán nhầm cả
     * trang web vào đây sẽ được lưu, rồi đẩy nguyên khối đó tới mọi khách mở khung chat.
     * 4000 rộng gấp tám lần đoạn chào hiện tại nên không cản trở việc dùng thật.
     */
    @NotBlank
    @Size(max = 4000)
    private String value;

    public UpdateAppSettingRequest() {
        // cho Jackson
    }

    public UpdateAppSettingRequest(String value) {
        this.value = value;
    }

    public String getValue() { return value; }
    public void setValue(String value) { this.value = value; }
}
