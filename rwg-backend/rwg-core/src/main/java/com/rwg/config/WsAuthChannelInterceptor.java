package com.rwg.config;

import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Component;

import java.security.Principal;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Xác thực JWT tại STOMP CONNECT (Phase c) và phân quyền tại SUBSCRIBE (chặng 8).
 *
 * CONNECT: token lấy từ native header {@code Authorization: Bearer <token>} của frame
 * CONNECT; giải mã bằng chung {@link JwtDecoder} của REST (issuer + exp + chữ ký HS256).
 * Thành công thì gắn principal có name = userId để
 * {@code SimpMessagingTemplate.convertAndSendToUser} unicast đúng {@code /user/queue/...}.
 * Thất bại (thiếu/sai token) thì ném {@link MessageDeliveryException} -> client nhận
 * ERROR frame và bị ngắt, KHÔNG có phiên STOMP.
 *
 * HAI LỚP KIỂM TRA THÊM Ở CHẶNG 8, cả hai đều bịt lỗ hổng thật:
 *
 * 1. CONNECT phải khớp {@code rwg.websocket.audience}. Hai app dùng chung
 *    {@code JWT_SECRET} và chung issuer, nên trước đó một token PLAYER hoàn toàn mở
 *    được phiên trên broker của khu quản trị.
 *
 * 2. SUBSCRIBE bị chặn theo đích. {@code enableSimpleBroker} KHÔNG có bất kỳ khái niệm
 *    phân quyền nào — mọi client đã kết nối đều subscribe được MỌI topic. Không có
 *    lớp này thì một người chơi chỉ cần subscribe {@code /topic/admin/chat} là đọc
 *    được toàn bộ tin nhắn hỗ trợ của mọi người chơi khác.
 */
@Component
public class WsAuthChannelInterceptor implements ChannelInterceptor {

    public static final String AUTHORIZATION_HEADER = "Authorization";
    public static final String BEARER_PREFIX = "Bearer ";

    /** Tiền tố đích CHỈ dành cho nhân sự quản trị. */
    private static final String ADMIN_DESTINATION_PREFIX = "/topic/admin";

    /** Vai trò thuộc khu quản trị, khớp {@code UserRole.isStaff()}. */
    private static final Set<String> STAFF_ROLES =
            Set.of("ROLE_ADMIN", "ROLE_FINANCE", "ROLE_SUPPORT", "ROLE_RISK");

    private final JwtDecoder jwtDecoder;
    private final WebSocketProperties webSocketProperties;

    public WsAuthChannelInterceptor(JwtDecoder jwtDecoder,
                                    WebSocketProperties webSocketProperties) {
        this.jwtDecoder = jwtDecoder;
        this.webSocketProperties = webSocketProperties;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null) {
            return message;
        }

        if (StompCommand.CONNECT == accessor.getCommand()) {
            authenticate(accessor);
            return message;
        }

        if (StompCommand.SUBSCRIBE == accessor.getCommand()) {
            authorizeSubscription(accessor);
        }

        return message;
    }

    /** Giải mã token ở CONNECT, kiểm tra đúng nhóm người dùng, rồi gắn principal. */
    private void authenticate(StompHeaderAccessor accessor) {
        String authHeader = accessor.getFirstNativeHeader(AUTHORIZATION_HEADER);
        if (authHeader == null || !authHeader.startsWith(BEARER_PREFIX)) {
            throw new MessageDeliveryException("STOMP CONNECT requires a Bearer token");
        }
        String token = authHeader.substring(BEARER_PREFIX.length()).trim();

        UserIdPrincipal principal;
        try {
            Jwt jwt = jwtDecoder.decode(token);
            UUID userId = UUID.fromString(jwt.getSubject());
            principal = new UserIdPrincipal(userId, rolesOf(jwt));
        } catch (JwtException | IllegalArgumentException invalidToken) {
            throw new MessageDeliveryException("STOMP CONNECT rejected: invalid token");
        }

        // Token hợp lệ nhưng THUỘC APP KHÁC.
        //
        // Thông báo lỗi cố tình không nói rõ "token của bạn thuộc app kia": người kết
        // nối đúng chỗ không bao giờ thấy lỗi này, còn người đang dò thì không nên
        // được xác nhận rằng token của họ hợp lệ ở đâu đó khác.
        boolean staff = principal.isStaff();
        boolean expectStaff = webSocketProperties.audience() == WebSocketProperties.Audience.STAFF;
        if (staff != expectStaff) {
            throw new MessageDeliveryException("STOMP CONNECT rejected: audience mismatch");
        }

        accessor.setUser(principal);
    }

    /**
     * Chặn subscribe vào đích không thuộc quyền của phiên.
     *
     * Chỉ chặn tiền tố {@code /topic/admin}. Các đích {@code /user/queue/...} KHÔNG
     * cần chặn ở đây: Spring tự viết lại chúng thành đích riêng theo principal, nên
     * một người chơi subscribe {@code /user/queue/chat} chỉ nhận được gói của chính
     * họ dù có sửa gì trong frame.
     */
    private void authorizeSubscription(StompHeaderAccessor accessor) {
        String destination = accessor.getDestination();
        if (destination == null || !destination.startsWith(ADMIN_DESTINATION_PREFIX)) {
            return;
        }

        Principal user = accessor.getUser();
        if (!(user instanceof UserIdPrincipal principal) || !principal.isStaff()) {
            throw new MessageDeliveryException(
                    "STOMP SUBSCRIBE rejected: " + destination + " requires a staff role");
        }
    }

    /** Vai trò trong claim "roles" (đã có tiền tố ROLE_ — xem SecurityConfig). */
    private static List<String> rolesOf(Jwt jwt) {
        List<String> roles = jwt.getClaimAsStringList(SecurityConfig.ROLE_CLAIM);
        return roles == null ? List.of() : List.copyOf(roles);
    }

    /**
     * Principal của phiên STOMP: name = userId để unicast {@code /user/queue/...} đúng đích.
     *
     * MANG THEO cả vai trò. Cách khác là tra lại vai trò từ DB mỗi lần cần kiểm tra,
     * nhưng SUBSCRIBE là frame nóng và một truy vấn DB cho mỗi lần subscribe là chi phí
     * không cần thiết — vai trò đã nằm trong token đã được xác thực chữ ký.
     */
    public record UserIdPrincipal(UUID userId, List<String> roles) implements Principal {

        @Override
        public String getName() {
            return userId.toString();
        }

        /** Có ít nhất một vai trò thuộc khu quản trị. */
        public boolean isStaff() {
            return roles.stream().anyMatch(STAFF_ROLES::contains);
        }
    }
}
