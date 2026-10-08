package com.rwg.identity.service;

import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Lớp bảo vệ và phân biệt quyền giữa Admin 1 (Master Admin - Genting2004)
 * và Admin 2 (Sub Admin / Operator / các nhân sự quản trị khác).
 *
 * Chỉ Master Admin (Genting2004) mới có quyền ẩn/hiện người chơi và xem
 * toàn bộ các người chơi đã bị ẩn. Mọi tài khoản khác chỉ xem được người chơi công khai.
 */
public final class AdminAccessGuard {

    public static final String MASTER_ADMIN_USERNAME = "genting2004";

    private AdminAccessGuard() {
        // Utility class
    }

    public static boolean isMasterAdmin(String username) {
        return username != null && MASTER_ADMIN_USERNAME.equalsIgnoreCase(username.trim());
    }

    public static boolean isMasterAdmin(Jwt jwt) {
        if (jwt == null) {
            return false;
        }
        String username = jwt.getClaimAsString("username");
        if (username != null && !username.isBlank()) {
            return isMasterAdmin(username);
        }
        // Fallback kiểm tra subject nếu username claim bị thiếu
        return isMasterAdmin(jwt.getSubject());
    }

    public static boolean isMasterAdmin(Authentication authentication) {
        if (authentication == null) {
            return false;
        }
        if (authentication.getPrincipal() instanceof Jwt jwt) {
            return isMasterAdmin(jwt);
        }
        return isMasterAdmin(authentication.getName());
    }
}
