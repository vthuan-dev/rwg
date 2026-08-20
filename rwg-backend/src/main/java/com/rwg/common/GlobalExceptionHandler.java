package com.rwg.common;

import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Chuyển mọi exception về response lỗi chuẩn {code, message, details, traceId}.
 * Message được resolve qua MessageSource (bundle i18n/messages) theo locale hiện
 * tại của request; thiếu key -> fallback về message thô/default của ErrorCode.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private final MessageSource messageSource;

    public GlobalExceptionHandler(MessageSource messageSource) {
        this.messageSource = messageSource;
    }

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiError> handleApiException(ApiException ex) {
        ErrorCode code = ex.errorCode();
        return ResponseEntity.status(code.httpStatus())
                .body(ApiError.of(code, resolveMessage(ex), ex.details(), traceId()));
    }

    /**
     * Ưu tiên messageKey riêng của exception; nếu không có thì dùng key chuẩn
     * error.<ErrorCode.name()>. Key không tồn tại trong bundle -> fallback message thô.
     */
    private String resolveMessage(ApiException ex) {
        Locale locale = LocaleContextHolder.getLocale();
        if (ex.messageKey() != null) {
            String resolved = messageSource.getMessage(ex.messageKey(), ex.args(), null, locale);
            if (resolved != null) {
                return resolved;
            }
        }
        return messageSource.getMessage(ex.errorCode().messageKey(), null, ex.getMessage(), locale);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException ex) {
        Map<String, Object> fieldErrors = new LinkedHashMap<>();
        for (FieldError error : ex.getBindingResult().getFieldErrors()) {
            fieldErrors.put(error.getField(), error.getDefaultMessage());
        }
        return ResponseEntity.badRequest()
                .body(ApiError.of(ErrorCode.VALIDATION_ERROR, resolveCodeMessage(ErrorCode.VALIDATION_ERROR),
                        fieldErrors, traceId()));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiError> handleConstraintViolation(ConstraintViolationException ex) {
        Map<String, Object> violations = new LinkedHashMap<>();
        ex.getConstraintViolations()
                .forEach(v -> violations.put(v.getPropertyPath().toString(), v.getMessage()));
        return ResponseEntity.badRequest()
                .body(ApiError.of(ErrorCode.VALIDATION_ERROR, resolveCodeMessage(ErrorCode.VALIDATION_ERROR),
                        violations, traceId()));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> handleUnreadable(HttpMessageNotReadableException ex) {
        return ResponseEntity.badRequest()
                .body(ApiError.of(ErrorCode.INVALID_REQUEST, resolveCodeMessage(ErrorCode.INVALID_REQUEST),
                        null, traceId()));
    }

    /** Resolve message chuẩn error.<ErrorCode.name()> theo locale request, fallback defaultMessage. */
    private String resolveCodeMessage(ErrorCode code) {
        return messageSource.getMessage(code.messageKey(), null, code.defaultMessage(), LocaleContextHolder.getLocale());
    }

    @ExceptionHandler({AuthenticationException.class})
    public ResponseEntity<ApiError> handleAuthentication(AuthenticationException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ApiError.of(ErrorCode.UNAUTHORIZED, resolveCodeMessage(ErrorCode.UNAUTHORIZED), null, traceId()));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiError> handleAccessDenied(AccessDeniedException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiError.of(ErrorCode.FORBIDDEN, resolveCodeMessage(ErrorCode.FORBIDDEN), null, traceId()));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiError> handleNotFound(NoResourceFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiError.of(ErrorCode.NOT_FOUND, resolveCodeMessage(ErrorCode.NOT_FOUND),
                        Map.of("path", ex.getResourcePath()), traceId()));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiError> handleDataIntegrity(DataIntegrityViolationException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiError.of(ErrorCode.CONFLICT, resolveCodeMessage(ErrorCode.CONFLICT),
                        null, traceId()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleUnexpected(Exception ex) {
        log.error("Unhandled exception, traceId={}", traceId(), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiError.of(ErrorCode.INTERNAL_ERROR, resolveCodeMessage(ErrorCode.INTERNAL_ERROR),
                        null, traceId()));
    }

    private String traceId() {
        String id = MDC.get("traceId");
        return id != null ? id : "unknown";
    }
}
