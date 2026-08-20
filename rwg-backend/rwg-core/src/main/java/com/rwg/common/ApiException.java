package com.rwg.common;

import java.util.Map;

/**
 * Exception nghiệp vụ chuẩn. Ném từ service, được {@link GlobalExceptionHandler}
 * chuyển thành response {code, message, details, traceId}.
 *
 * i18n: nếu {@code messageKey} được đặt, GlobalExceptionHandler resolve message qua
 * MessageSource theo locale request (kèm {@code args}); nếu không có key hoặc key
 * không tồn tại -> fallback về message thô / {@link ErrorCode#defaultMessage()}.
 */
public class ApiException extends RuntimeException {

    private final ErrorCode errorCode;
    private final Map<String, Object> details;
    private final String messageKey;
    private final Object[] args;

    public ApiException(ErrorCode errorCode) {
        this(errorCode, errorCode.defaultMessage(), null);
    }

    public ApiException(ErrorCode errorCode, String message) {
        this(errorCode, message, null);
    }

    public ApiException(ErrorCode errorCode, String message, Map<String, Object> details) {
        this(errorCode, message, details, null, (Object[]) null);
    }

    /** Constructor i18n: message resolve từ bundle theo messageKey + args. */
    public ApiException(ErrorCode errorCode, String message, Map<String, Object> details,
                        String messageKey, Object... args) {
        super(message);
        this.errorCode = errorCode;
        this.details = details;
        this.messageKey = messageKey;
        this.args = args;
    }

    public ErrorCode errorCode() {
        return errorCode;
    }

    public Map<String, Object> details() {
        return details;
    }

    /** Key i18n optional; null = dùng message thô (fallback). */
    public String messageKey() {
        return messageKey;
    }

    /** Args cho MessageFormat trong bundle; null nếu không có. */
    public Object[] args() {
        return args;
    }
}
