package com.rwg.config;

import com.rwg.identity.service.ActiveSessionStore;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.UUID;

/**
 * Từ chối access token KHÔNG thuộc phiên hiện hành của người dùng — quy tắc "một tài khoản,
 * một phiên": đăng nhập ở thiết bị mới thì thiết bị cũ mất quyền.
 *
 * VÌ SAO ĐẶT Ở TẦNG VALIDATOR CỦA DECODER, không viết servlet filter: bean {@code jwtDecoder}
 * được DÙNG CHUNG bởi cả REST và WebSocket ({@link WsAuthChannelInterceptor} gọi
 * {@code jwtDecoder.decode} ở frame STOMP CONNECT). Một filter HTTP sẽ bỏ sót hẳn đường
 * WebSocket, tức thiết bị cũ vẫn mở được socket mới và tiếp tục nhận dữ liệu thời gian thực.
 *
 * VÌ SAO LÀ CLASS RIÊNG, không nhồi vào {@link RevokedSessionValidator}: hai lớp trả lời hai
 * câu hỏi khác nhau — "tài khoản này có bị khoá không" và "phiên này có phải phiên hiện hành
 * không". Chúng có công tắc bật/tắt riêng, và gộp lại thì một thay đổi ở quy tắc này có thể
 * làm hỏng việc chặn tài khoản bị khoá.
 */
public class SingleSessionValidator implements OAuth2TokenValidator<Jwt> {

    /**
     * Dùng {@code invalid_token} theo RFC 6750 thay vì một mã riêng.
     *
     * Client (xem {@code authedRequest} trong playerApi.ts) đã xử lý 401 bằng cách thử gia
     * hạn token một lần; lần gia hạn đó cũng thất bại vì refresh token của phiên cũ đã bị thu
     * hồi — kết quả là người dùng được đưa về trang đăng nhập. Một mã lạ sẽ đi qua nhánh xử
     * lý khác mà không có hành vi nào được định nghĩa cho nó.
     */
    private static final OAuth2Error NOT_CURRENT_SESSION = new OAuth2Error(
            "invalid_token", "Session superseded by a newer login", null);

    private final ActiveSessionStore store;

    public SingleSessionValidator(ActiveSessionStore store) {
        this.store = store;
    }

    @Override
    public OAuth2TokenValidatorResult validate(Jwt jwt) {
        String tokenSession = jwt.getClaimAsString(SecurityConfig.SESSION_CLAIM);
        if (tokenSession == null) {
            // Token KHÔNG mang claim phiên -> cho qua.
            //
            // Ba nhóm token rơi vào đây, cả ba đều CẦN được cho qua:
            // - Token phát trước khi tính năng này triển khai. Chặn chúng nghĩa là đúng giây
            //   deploy toàn bộ người đang chơi bị đăng xuất.
            // - Token của nhân sự quản trị, nếu quy tắc không áp cho họ.
            // - Phiên hỗ trợ khách quên mật khẩu.
            //
            // Không có nguy cơ giả mạo: token vẫn phải có chữ ký hợp lệ, và không ai tạo được
            // token như vậy ngoài chính server.
            return OAuth2TokenValidatorResult.success();
        }

        UUID userId = subjectOf(jwt);
        if (userId == null) {
            return OAuth2TokenValidatorResult.success();
        }

        String currentSession = store.current(userId);
        if (currentSession == null) {
            // Chưa chốt phiên nào, hoặc bản ghi đã hết hạn (TTL bằng TTL refresh token, nên
            // tới lúc đó chính refresh token cũng đã chết). Không có gì để so.
            return OAuth2TokenValidatorResult.success();
        }

        if (!currentSession.equals(tokenSession)) {
            return OAuth2TokenValidatorResult.failure(NOT_CURRENT_SESSION);
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
