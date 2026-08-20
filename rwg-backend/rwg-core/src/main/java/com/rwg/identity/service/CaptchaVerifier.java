package com.rwg.identity.service;

/**
 * Verifier cho captcha token phía server.
 * Khi rate-limiter báo captchaRequired mà request thiếu captchaToken hợp lệ,
 * login bị TỪ CHỐI trước khi chạm BCrypt/DB.
 *
 * Hiện dùng {@link DevCaptchaVerifier} (stub). TODO: tích hợp provider thật
 * (hCaptcha/reCAPTCHA...) ở bước sau — chỉ cần thêm impl khác của interface này.
 */
public interface CaptchaVerifier {

    /** Trả về true nếu captchaToken hợp lệ. */
    boolean verify(String captchaToken);
}
