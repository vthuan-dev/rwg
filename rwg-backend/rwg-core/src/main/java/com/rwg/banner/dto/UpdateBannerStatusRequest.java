package com.rwg.banner.dto;

import jakarta.validation.constraints.NotNull;

/**
 * Request cập nhật trạng thái bật/tắt hiển thị của Banner.
 */
public record UpdateBannerStatusRequest(
        @NotNull(message = "{validation.banner.status.not_null}")
        Boolean active
) {}
