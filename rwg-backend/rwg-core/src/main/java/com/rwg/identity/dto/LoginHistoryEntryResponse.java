package com.rwg.identity.dto;

import java.time.Instant;

/**
 * Một lần đăng nhập trong lịch sử của một tài khoản, dựng từ {@code audit_log}.
 *
 * VÌ SAO CÓ DTO RIÊNG THAY VÌ DÙNG LẠI {@link AuditLogResponse}: DTO đó trả {@code details}
 * là chuỗi JSON THÔ cùng {@code actorId}/{@code targetType}/{@code targetId}. Giao diện lịch
 * sử đăng nhập không dùng những trường đó, nhưng lại buộc phải tự phân tích chuỗi JSON mới
 * biết lần đó thành công hay thất bại — tức đẩy việc diễn giải nghiệp vụ sang phía hiển thị.
 * Ở đây backend kết luận sẵn bằng hai trường {@code success} và {@code channel}.
 *
 * KHÔNG có trường lý do thất bại: {@code audit_log.details} của LOGIN_FAILED chỉ ghi cờ
 * captcha/khoá, không ghi mật khẩu sai ở đâu — và đó là điều đúng đắn, không nên đổi.
 */
public record LoginHistoryEntryResponse(

        /** Thời điểm sự kiện (audit_log.created_at). */
        Instant at,

        /**
         * false CHỈ khi đây là lần đăng nhập thất bại. Suy từ action chứ không lưu riêng:
         * bảng audit_log là nguồn sự thật duy nhất và nó phân biệt bằng tên action.
         */
        boolean success,

        /**
         * IP nhìn thấy lúc đó. Có thể null với bản ghi cũ ghi trước khi hệ thống lấy IP.
         */
        String ipAddress,

        /**
         * PLAYER = đăng nhập ở trang khách; BACKOFFICE = đăng nhập vào khu quản trị.
         *
         * Phân biệt hai kênh vì với một tài khoản nhân sự, đăng nhập ở trang khách và đăng
         * nhập vào backoffice là hai việc có mức rủi ro khác nhau hoàn toàn.
         */
        String channel
) {

    public static final String CHANNEL_PLAYER = "PLAYER";
    public static final String CHANNEL_BACKOFFICE = "BACKOFFICE";
}
