package com.rwg.identity.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * Đổi ngôn ngữ hiển thị của user. Chỉ chấp nhận 4 locale hỗ trợ: en, vi, zh, ja.
 */
public record UpdateLocaleRequest(
        @NotBlank(message = "{validation.locale.not_blank}")
        @Pattern(regexp = "^(en|vi|zh|ja)$", message = "{validation.locale.invalid}")
        String locale
) {
}
