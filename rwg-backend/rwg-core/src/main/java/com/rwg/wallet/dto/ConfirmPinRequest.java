package com.rwg.wallet.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Yêu cầu thực hiện thao tác nhạy cảm trên sổ ví (xóa / ẩn giao dịch), đòi hỏi mã PIN của admin.
 */
public record ConfirmPinRequest(
        @NotBlank
        String confirmPin
) {
}
