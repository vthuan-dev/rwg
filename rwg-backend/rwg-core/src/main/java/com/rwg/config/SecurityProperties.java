package com.rwg.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * Cấu hình JWT / session (prefix rwg.security).
 */
@ConfigurationProperties(prefix = "rwg.security")
public record SecurityProperties(
        String jwtSecret,
        String issuer,
        Duration accessTokenTtl,
        Duration refreshTokenTtl,
        /**
         * Ép MỘT phiên cho mỗi tài khoản người chơi: đăng nhập ở thiết bị mới thì thiết bị
         * cũ mất quyền ngay.
         *
         * CÓ CÔNG TẮC vì đây là thay đổi trên đường xác thực — thứ mà mọi request đều đi
         * qua. Nếu nó gây sự cố thật thì đổi một biến môi trường rồi khởi động lại nhanh hơn
         * hẳn dựng lại bản deploy cũ.
         *
         * KHÔNG áp cho nhân sự quản trị: một người mở khu quản trị trên máy văn phòng và
         * laptop sẽ liên tục đá nhau ra.
         */
        Boolean singleSessionEnabled
) {

    /**
     * Mặc định BẬT khi cấu hình không khai báo.
     *
     * Dùng kiểu bọc {@code Boolean} rồi tự xử lý null thay vì {@code boolean}: kiểu nguyên
     * thuỷ nhận giá trị mặc định là {@code false} khi thiếu khai báo, tức quên một dòng
     * trong yml sẽ ÂM THẦM tắt tính năng.
     */
    public boolean singleSessionActive() {
        return singleSessionEnabled == null || singleSessionEnabled;
    }

    public SecretKey hmacKey() {
        byte[] bytes = jwtSecret.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < 32) {
            throw new IllegalStateException("rwg.security.jwt-secret phải dài tối thiểu 32 ký tự (HS256)");
        }
        return new SecretKeySpec(bytes, "HmacSHA256");
    }
}
