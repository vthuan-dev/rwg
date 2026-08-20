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
import java.util.UUID;

/**
 * Xác thực JWT tại STOMP CONNECT (Phase c). Token lấy từ native header
 * {@code Authorization: Bearer <token>} của frame CONNECT; giải mã bằng chung
 * {@link JwtDecoder} của REST (issuer + exp + chữ ký HS256).
 *
 * Thành công: gắn principal có name = userId (subject của JWT) để
 * SimpMessagingTemplate.convertAndSendToUser unicast đúng {@code /user/queue/...}.
 * Thất bại (thiếu/sai token): ném {@link MessageDeliveryException} -> client
 * nhận ERROR frame và bị ngắt, KHÔNG có phiên STOMP.
 */
@Component
public class WsAuthChannelInterceptor implements ChannelInterceptor {

    public static final String AUTHORIZATION_HEADER = "Authorization";
    public static final String BEARER_PREFIX = "Bearer ";

    private final JwtDecoder jwtDecoder;

    public WsAuthChannelInterceptor(JwtDecoder jwtDecoder) {
        this.jwtDecoder = jwtDecoder;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null || StompCommand.CONNECT != accessor.getCommand()) {
            return message;
        }
        String authHeader = accessor.getFirstNativeHeader(AUTHORIZATION_HEADER);
        if (authHeader == null || !authHeader.startsWith(BEARER_PREFIX)) {
            throw new MessageDeliveryException("STOMP CONNECT requires a Bearer token");
        }
        String token = authHeader.substring(BEARER_PREFIX.length()).trim();
        try {
            Jwt jwt = jwtDecoder.decode(token);
            UUID userId = UUID.fromString(jwt.getSubject());
            accessor.setUser(new UserIdPrincipal(userId));
        } catch (JwtException | IllegalArgumentException invalidToken) {
            throw new MessageDeliveryException("STOMP CONNECT rejected: invalid token");
        }
        return message;
    }

    /** Principal tối giản: name = userId để unicast /user/queue/... đúng đích. */
    public record UserIdPrincipal(UUID userId) implements Principal {
        @Override
        public String getName() {
            return userId.toString();
        }
    }
}
