package com.rwg.common;

import java.time.Instant;
import java.util.Map;

/**
 * Shape chuẩn của response lỗi: {code, message, details, traceId}.
 */
public record ApiError(
        String code,
        String message,
        Map<String, Object> details,
        String traceId,
        Instant timestamp
) {

    public static ApiError of(ErrorCode errorCode, String message, Map<String, Object> details, String traceId) {
        return new ApiError(errorCode.name(), message, details, traceId, Instant.now());
    }
}
