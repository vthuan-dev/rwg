package com.rwg.game.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Đổi hạn mức cược của bàn (PATCH /api/v1/admin/games/tables/{id}/limits).
 *
 * Tiền nhận dạng CHUỖI rồi parse sang BigDecimal ở service — cùng quy ước với
 * AdjustWalletRequest. Nếu để kiểu số, Jackson có thể đưa qua double và làm mất
 * chữ số ở giá trị lớn (ArchUnit cũng cấm float/double ở package tiền).
 */
public record UpdateTableLimitsRequest(
        @NotBlank
        @Pattern(regexp = "\\d{1,15}(\\.\\d{1,8})?", message = "minBet phải là số dương tối đa 8 chữ số thập phân")
        String minBet,

        @NotBlank
        @Pattern(regexp = "\\d{1,15}(\\.\\d{1,8})?", message = "maxBet phải là số dương tối đa 8 chữ số thập phân")
        String maxBet,

        @NotBlank
        @Size(max = 255)
        String reason
) {
}
