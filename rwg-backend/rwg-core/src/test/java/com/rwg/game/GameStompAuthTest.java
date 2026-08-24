package com.rwg.game;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.messaging.converter.JacksonJsonMessageConverter;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;
import tools.jackson.databind.ObjectMapper;

import java.lang.reflect.Type;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * STOMP CONNECT bắt buộc JWT (WsAuthChannelInterceptor, Phase c):
 * - Handshake /ws KHÔNG token -> Spring Security từ chối (đã bỏ permitAll).
 * - CONNECT KHÔNG token (handshake có token) -> bị ngắt, không có phiên.
 * - CONNECT có token -> nhận broadcast phase trên /topic/game/table/{tableId}.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "rwg.game.round.betting-open=PT0.4S",
        "rwg.game.round.betting-closed=PT0.1S",
        "rwg.game.round.spinning=PT0.1S",
        "rwg.game.round.result=PT0.1S",
        "rwg.game.round.settle=PT0.2S",
        "rwg.game.bet-placed-window=PT0.1S"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class GameStompAuthTest {

    private static final String TABLE_ID = "11111111-2222-3333-4444-555555555555";
    private static final String PASSWORD = "MatKhau@12345";

    @LocalServerPort
    int port;

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    private String wsUrl() {
        return "ws://localhost:" + port + "/ws";
    }

    private WebSocketStompClient newClient() {
        WebSocketStompClient client = new WebSocketStompClient(new StandardWebSocketClient());
        // Server phát JSON (content-type application/json) -> client cần converter JSON.
        client.setMessageConverter(new JacksonJsonMessageConverter());
        return client;
    }

    private String registerLoginToken(String username) throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"%s","password":"%s"}
                                """.formatted(username, PASSWORD)))
                .andExpect(status().isCreated());
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"identifier":"%s","password":"%s"}
                                """.formatted(username, PASSWORD)))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .get("accessToken").asText();
    }

    @Test
    void handshakeWithoutTokenIsRejectedBySecurity() {
        WebSocketStompClient client = newClient();
        StompHeaders connect = new StompHeaders();
        connect.add("Authorization", "Bearer " + UUID.randomUUID()); // không quan trọng: handshake đã trượt
        CompletableFuture<StompSession> future = client.connectAsync(wsUrl(), new WebSocketHttpHeaders(),
                connect, new StompSessionHandlerAdapter() { });

        // /ws hết permitAll -> handshake yêu cầu JWT -> 401, future KHÔNG bao giờ nối thành công.
        assertThatThrownBy(() -> future.get(10, TimeUnit.SECONDS)).hasCauseInstanceOf(Exception.class);
    }

    @Test
    void connectWithoutTokenIsRejectedByInterceptor() throws Exception {
        String token = registerLoginToken("stompno" + UUID.randomUUID().toString().substring(0, 8));
        WebSocketHttpHeaders handshake = new WebSocketHttpHeaders();
        handshake.add("Authorization", "Bearer " + token);

        WebSocketStompClient client = newClient();
        // CONNECT KHÔNG kèm Authorization native header -> interceptor từ chối.
        CompletableFuture<StompSession> future = client.connectAsync(wsUrl(), handshake,
                new StompHeaders(), new StompSessionHandlerAdapter() { });

        assertThatThrownBy(() -> future.get(10, TimeUnit.SECONDS)).hasCauseInstanceOf(Exception.class);
    }

    @Test
    void connectWithTokenReceivesPhaseBroadcast() throws Exception {
        String token = registerLoginToken("stompok" + UUID.randomUUID().toString().substring(0, 8));
        WebSocketHttpHeaders handshake = new WebSocketHttpHeaders();
        handshake.add("Authorization", "Bearer " + token);
        StompHeaders connect = new StompHeaders();
        connect.add("Authorization", "Bearer " + token);

        WebSocketStompClient client = newClient();
        List<String> received = new CopyOnWriteArrayList<>();
        CountDownLatch gotMessage = new CountDownLatch(1);

        StompSession session = client.connectAsync(wsUrl(), handshake, connect,
                new StompSessionHandlerAdapter() {
                    @Override
                    public void afterConnected(StompSession session, StompHeaders connectedHeaders) {
                        session.subscribe("/topic/game/table/" + TABLE_ID, new StompFrameHandler() {
                            @Override
                            public Type getPayloadType(StompHeaders headers) {
                                return Map.class;
                            }

                            @Override
                            public void handleFrame(StompHeaders headers, Object payload) {
                                received.add(String.valueOf(payload));
                                gotMessage.countDown();
                            }
                        });
                    }
                }).get(10, TimeUnit.SECONDS);

        // Vòng lặp phase chạy duration ngắn -> gói ROUND_PHASE/ROUND_RESULT tới rất nhanh.
        assertThat(gotMessage.await(15, TimeUnit.SECONDS))
                .as("phải nhận broadcast phase sau khi CONNECT có token")
                .isTrue();
        assertThat(received.get(0)).contains(TABLE_ID);
        assertThat(received.stream().anyMatch(m -> m.contains("ROUND_PHASE") || m.contains("ROUND_RESULT")))
                .isTrue();
        session.disconnect();
    }
}
