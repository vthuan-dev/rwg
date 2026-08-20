package com.rwg.common.web;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Tiện ích chung cho tầng api (fix review m10): IP client dùng cho audit/rate-limit.
 * Đặt ở com.rwg.common.web để mọi module (identity/payment/bank) dùng chung,
 * tránh coupling api-module-này -> api-module-kia.
 *
 * Lưu ý: server.forward-headers-strategy=native + tomcat.remoteip.internal-proxies
 * đã rewrite remoteAddr theo X-Forwarded-For CHỈ từ proxy tin cậy (TRUSTED_PROXIES).
 */
public final class ClientAddresses {

    private ClientAddresses() {
        // utility class
    }

    /** IP client (remoteAddr đã được RemoteIpValve xử lý theo proxy tin cậy). */
    public static String clientIp(HttpServletRequest request) {
        return request.getRemoteAddr();
    }
}
