package com.rwg.identity.dto;

/**
 * Master Admin (Genting2004) yêu cầu ẩn / mở cho thấy người chơi đối với Admin 2 (Sub Admin).
 */
public record ToggleUserVisibilityRequest(
        boolean hidden
) {
}
