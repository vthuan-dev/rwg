package com.rwg.identity.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Yêu cầu mở phiên chat CSKH cho khách chưa đăng nhập bằng tên đăng nhập (username).
 */
public record GuestSupportRequest(
    @NotBlank(message = "Tên đăng nhập không được để trống")
    String username
) {}
