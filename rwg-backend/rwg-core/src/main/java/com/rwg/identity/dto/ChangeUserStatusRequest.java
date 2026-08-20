package com.rwg.identity.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Admin đổi trạng thái tài khoản. status là String (không bind trực tiếp enum) để
 * giá trị sai trả VALIDATION_ERROR có message i18n thay vì 400 thô của Jackson.
 *
 * reason: BẮT BUỘC khi chuyển sang LOCKED/BANNED/CLOSED (kiểm tra ở service, không
 * ở annotation vì phụ thuộc giá trị status).
 */
public record ChangeUserStatusRequest(
        @NotBlank(message = "{validation.admin.status.not_blank}")
        String status,

        @Size(max = 255, message = "{validation.admin.reason.size}")
        String reason
) {
}
