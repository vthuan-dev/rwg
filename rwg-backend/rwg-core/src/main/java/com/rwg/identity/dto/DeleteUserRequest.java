package com.rwg.identity.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Yêu cầu xóa tài khoản người chơi.
 *
 * Mã xác nhận nằm trong THÂN REQUEST, không phải tham số URL: tham số URL đi vào log truy
 * cập của nginx và lịch sử trình duyệt — đó là những chỗ mã bí mật không được xuất hiện.
 */
public record DeleteUserRequest(

        /**
         * Mã xác nhận thao tác không hoàn tác được.
         *
         * Bắt buộc và không được rỗng: request thiếu trường này nghĩa là giao diện đang bỏ
         * qua bước xác nhận, và đó là lỗi nghiêm trọng hơn là thiếu dữ liệu thông thường.
         * Tuy nhiên {@code AdminDestructivePinService} mới là chỗ so sánh mã đúng/sai — ở
         * đây chỉ kiểm rỗng để trả lỗi sớm và rõ hơn.
         */
        @NotBlank
        String confirmPin
) {
}
