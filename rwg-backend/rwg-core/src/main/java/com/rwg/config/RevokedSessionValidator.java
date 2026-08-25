package com.rwg.config;

import com.rwg.identity.service.SessionRevocationStore;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.UUID;

/**
 * Từ chối access token được phát TRƯỚC mốc thu hồi phiên của người dùng.
 *
 * VÌ SAO CẦN: JWT không trạng thái, nên thu hồi refresh token chỉ chặn việc GIA HẠN phiên.
 * Một tài khoản vừa bị khóa vẫn gọi API bình thường tới khi access token hết hạn — 15 phút
 * theo `rwg.security.access-token-ttl`. Với tài khoản bị khóa vì nghi gian lận, đó là đủ
 * thời gian để đặt thêm rất nhiều vòng cược.
 *
 * VÌ SAO ĐẶT Ở TẦNG VALIDATOR CỦA DECODER, không viết một servlet filter riêng: bean
 * {@code jwtDecoder} được DÙNG CHUNG bởi cả REST và WebSocket (xem
 * {@link WsAuthChannelInterceptor} — nó gọi {@code jwtDecoder.decode} ở frame STOMP CONNECT).
 * Một filter HTTP sẽ bỏ sót hẳn đường WebSocket, tức người bị khóa vẫn mở được socket mới và
 * tiếp tục nhận dữ liệu thời gian thực.
 *
 * CHI PHÍ: một lần đọc store cho mỗi request đã đăng nhập. Với bản Redis đó là một lệnh
 * {@code GET} tới Redis đang chạy cùng máy, và rate limiter vốn đã nằm trên cùng đường đi —
 * nên không đổi bậc độ trễ.
 */
public class RevokedSessionValidator implements OAuth2TokenValidator<Jwt> {

    /**
     * Mã lỗi trả về khi token bị thu hồi.
     *
     * Dùng {@code invalid_token} theo RFC 6750 thay vì một mã riêng: client (xem
     * {@code authedRequest} trong playerApi.ts) đã xử lý 401 bằng cách thử gia hạn token một
     * lần, và lần gia hạn đó cũng thất bại vì refresh token đã bị thu hồi cùng lúc — kết quả
     * là người dùng được đưa về trang đăng nhập. Một mã lạ sẽ đi qua nhánh xử lý khác và
     * không có hành vi nào được định nghĩa cho nó.
     */
    private static final OAuth2Error REVOKED = new OAuth2Error(
            "invalid_token", "Session has been revoked", null);

    /**
     * Biên chịu lệch giờ giữa các máy.
     *
     * `iat` do máy PHÁT HÀNH token đóng dấu, còn mốc thu hồi do máy XỬ LÝ lệnh khóa ghi. Hai
     * máy lệch nhau vài giây là bình thường. KHÔNG có biên này thì một token phát ra ngay
     * TRƯỚC lệnh khóa vài trăm mili giây có thể có `iat` lớn hơn mốc và lọt qua.
     *
     * Cộng biên vào phía token, tức nghiêng về TỪ CHỐI khi không chắc: từ chối sai chỉ khiến
     * người dùng phải đăng nhập lại, còn cho qua sai thì để tài khoản bị khóa tiếp tục cược.
     */
    private static final long CLOCK_SKEW_SECONDS = 5;

    private final SessionRevocationStore store;

    public RevokedSessionValidator(SessionRevocationStore store) {
        this.store = store;
    }

    @Override
    public OAuth2TokenValidatorResult validate(Jwt jwt) {
        Instant issuedAt = jwt.getIssuedAt();
        if (issuedAt == null) {
            // Mọi token của dự án đều đóng dấu `iat` (xem JwtService), nên trường hợp này chỉ
            // xảy ra với token do nơi khác phát. Không có `iat` thì không so được với mốc —
            // để các validator còn lại (chữ ký, issuer, exp) quyết định.
            return OAuth2TokenValidatorResult.success();
        }

        UUID userId = subjectOf(jwt);
        if (userId == null) {
            return OAuth2TokenValidatorResult.success();
        }

        Instant revokedBefore = store.revokedBefore(userId);
        if (revokedBefore == null) {
            // Trường hợp thường gặp: chưa từng bị thu hồi, hoặc mốc đã hết hạn.
            return OAuth2TokenValidatorResult.success();
        }

        // Cộng biên vào MỐC THU HỒI, tức nới rộng khoảng bị từ chối.
        //
        // Cộng vào `iat` của token thì làm điều NGƯỢC LẠI: nó đẩy token ra xa mốc và khiến
        // token sát ranh giới được cho qua. Chiều đúng là nghiêng về TỪ CHỐI khi không chắc —
        // từ chối sai chỉ khiến người dùng phải đăng nhập lại, còn cho qua sai thì để một tài
        // khoản đã bị khóa tiếp tục cược.
        if (issuedAt.isBefore(revokedBefore.plusSeconds(CLOCK_SKEW_SECONDS))) {
            return OAuth2TokenValidatorResult.failure(REVOKED);
        }
        return OAuth2TokenValidatorResult.success();
    }

    /** `sub` của token dạng UUID, hoặc null nếu không phải UUID. */
    private static UUID subjectOf(Jwt jwt) {
        String subject = jwt.getSubject();
        if (subject == null) {
            return null;
        }
        try {
            return UUID.fromString(subject);
        } catch (IllegalArgumentException notAUuid) {
            return null;
        }
    }
}
