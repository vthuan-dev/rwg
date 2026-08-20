package com.rwg.identity.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Admin duyệt KYC: nâng/hạ mức xác minh (NONE | LEVEL_1 | LEVEL_2 | LEVEL_3). */
public record UpdateKycLevelRequest(
        @NotBlank(message = "{validation.admin.kyc_level.not_blank}")
        String kycLevel,

        @Size(max = 255, message = "{validation.admin.reason.size}")
        String reason
) {
}
