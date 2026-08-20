package com.rwg.config;

import com.rwg.identity.service.UserLocaleService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.servlet.LocaleResolver;

import java.util.Enumeration;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * LocaleResolver stateless cho API JWT:
 * 1. Ưu tiên header Accept-Language — khớp language tag với 4 locale hỗ trợ
 *    (en/vi/zh/ja; "zh-CN" vẫn khớp "zh").
 * 2. Request không gửi Accept-Language (hoặc không khớp) và user ĐÃ xác thực
 *    -> dùng locale user đã lưu trong bảng users (qua cache, không query DB
 *    mỗi request).
 * 3. Mặc định: en (bundle messages.properties là bản tiếng Anh).
 *
 * Không hỗ trợ setLocale (không session/cookie — stateless theo DECISIONS.md).
 */
public class RwgLocaleResolver implements LocaleResolver {

    private static final Map<String, Locale> SUPPORTED = Map.of(
            "en", Locale.ENGLISH,
            "vi", Locale.forLanguageTag("vi"),
            "zh", Locale.SIMPLIFIED_CHINESE,
            "ja", Locale.JAPANESE);

    private final UserLocaleService userLocaleService;

    public RwgLocaleResolver(UserLocaleService userLocaleService) {
        this.userLocaleService = userLocaleService;
    }

    @Override
    public Locale resolveLocale(HttpServletRequest request) {
        // 1. Accept-Language
        Enumeration<Locale> requested = request.getLocales();
        while (requested.hasMoreElements()) {
            Locale candidate = SUPPORTED.get(requested.nextElement().getLanguage());
            if (candidate != null) {
                return candidate;
            }
        }
        // 2. Locale đã lưu của user đã xác thực (JWT sub = userId)
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof Jwt jwt) {
            try {
                UUID userId = UUID.fromString(jwt.getSubject());
                return SUPPORTED.getOrDefault(userLocaleService.localeOf(userId), Locale.ENGLISH);
            } catch (IllegalArgumentException ignored) {
                // subject không phải UUID — không thể tra locale, dùng mặc định
            }
        }
        // 3. Mặc định
        return Locale.ENGLISH;
    }

    @Override
    public void setLocale(HttpServletRequest request, HttpServletResponse response, Locale locale) {
        throw new UnsupportedOperationException(
                "Stateless API — đổi locale qua PATCH /api/v1/users/me/locale");
    }
}
