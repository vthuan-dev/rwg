package com.rwg.identity.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * Yêu cầu Admin tự đổi mã PIN rút tiền 6 số (cấp 2) cho người chơi.
 */
public record AdminOverrideWithdrawalPasswordRequest(
    @NotBlank(message = "Mã PIN mới không được để trống")
    @Pattern(regexp = "^\\d{6}$", message = "Mã PIN phải gồm đúng 6 chữ số")
    String newPin
) {}
