package com.rwg.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

import java.util.List;

/**
 * WebSocket + STOMP cho realtime game (Phase c).
 * Endpoint handshake: /ws — handshake phải qua Spring Security (JWT);
 * STOMP CONNECT xác thực tiếp bởi {@link WsAuthChannelInterceptor} gắn principal userId.
 *
 * Allowed origins lấy từ property rwg.websocket.allowed-origin-patterns theo profile
 * (dev đặt ["*"]; môi trường khác PHẢI cấu hình danh sách cụ thể — KHÔNG hard-code "*").
 * Danh sách rỗng -> không cho phép cross-origin nào.
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final WebSocketProperties webSocketProperties;
    private final WsAuthChannelInterceptor wsAuthChannelInterceptor;

    public WebSocketConfig(WebSocketProperties webSocketProperties,
                           WsAuthChannelInterceptor wsAuthChannelInterceptor) {
        this.webSocketProperties = webSocketProperties;
        this.wsAuthChannelInterceptor = wsAuthChannelInterceptor;
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        // Xác thực JWT tại STOMP CONNECT trước khi frame tới broker.
        registration.interceptors(wsAuthChannelInterceptor);
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        // Client subscribe: /topic/... (kết quả ván chơi, số dư, chat bàn...)
        registry.enableSimpleBroker("/topic", "/queue");
        // Client gửi message tới: /app/...
        registry.setApplicationDestinationPrefixes("/app");
        // Unicast theo principal userId (WsAuthChannelInterceptor): /user/queue/wallet,
        // /user/queue/game/results.
        registry.setUserDestinationPrefix("/user");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        List<String> patterns = webSocketProperties.allowedOriginPatterns();
        var endpoint = registry.addEndpoint("/ws");
        if (!patterns.isEmpty()) {
            endpoint.setAllowedOriginPatterns(patterns.toArray(new String[0]));
        }
        // Danh sách rỗng: không cấu hình allowed origin -> mặc định chỉ same-origin.
    }
}
