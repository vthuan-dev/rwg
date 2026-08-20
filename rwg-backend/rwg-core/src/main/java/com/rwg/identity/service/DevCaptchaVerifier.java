package com.rwg.identity.service;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Captcha stub cho DEV/TEST: chấp nhận mọi captchaToken KHÁC rỗng.
 * Kích hoạt khi rwg.captcha.provider=dev (mặc định). KHÔNG dùng cho production.
 * TODO: thay bằng impl tích hợp provider thật (hCaptcha/reCAPTCHA) ở bước sau.
 */
@Component
@ConditionalOnProperty(name = "rwg.captcha.provider", havingValue = "dev", matchIfMissing = true)
public class DevCaptchaVerifier implements CaptchaVerifier {

    @Override
    public boolean verify(String captchaToken) {
        return captchaToken != null && !captchaToken.isBlank();
    }
}
