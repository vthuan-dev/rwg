package com.rwg.payment;

import com.rwg.identity.domain.User;
import com.rwg.identity.repository.UserRepository;
import com.rwg.payment.domain.PaymentOrder;
import com.rwg.payment.repository.PaymentOrderRepository;
import com.rwg.payment.service.FirstDepositEvent;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Test luồng nạp tiền (chặng 2 Phase b) trên H2 MODE=MySQL:
 * - hạn mức min $10 / max $50,000.
 * - stub auto-success: order SUCCESS + credit ví.
 * - FirstDepositEvent publish CHỈ lần đầu (kể cả 2 lệnh nạp SONG SONG — fix M5).
 * - webhook callback: bắt buộc X-Callback-Secret (fix M4), chỉ xử lý SUCCESS/FAILED,
 *   idempotent theo providerTxnId (không credit 2 lần).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@RecordApplicationEvents
class DepositFlowTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    PaymentOrderRepository orderRepository;

    @Autowired
    UserRepository userRepository;

    @Autowired
    FirstDepositEventCounter firstDepositEventCounter;

    @Value("${rwg.payment.callback-secret}")
    String callbackSecret;

    private static final String PASSWORD = "MatKhau@12345";

    private String unique(String prefix) {
        return prefix + UUID.randomUUID().toString().substring(0, 8);
    }

    private String registerLoginBearer(String username) throws Exception {
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
        return "Bearer " + objectMapper.readTree(result.getResponse().getContentAsString())
                .get("accessToken").asText();
    }

    private BigDecimal balance(String bearer) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/wallet/me")
                        .header("Authorization", bearer))
                .andExpect(status().isOk())
                .andReturn();
        return new BigDecimal(objectMapper.readTree(result.getResponse().getContentAsString())
                .get("balance").asText());
    }

    private MvcResult deposit(String bearer, String amount) throws Exception {
        return mockMvc.perform(post("/api/v1/wallet/deposits")
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"amount":"%s"}
                                """.formatted(amount)))
                .andReturn();
    }

    private MvcResult callback(String providerTxnId, String providerStatus, String secret) throws Exception {
        var builder = post("/api/v1/payments/callback")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"providerTxnId":"%s","status":"%s"}
                        """.formatted(providerTxnId, providerStatus));
        if (secret != null) {
            builder = builder.header("X-Callback-Secret", secret);
        }
        return mockMvc.perform(builder).andReturn();
    }

    @Test
    void depositBelowMinAndAboveMaxAreRejected() throws Exception {
        String bearer = registerLoginBearer(unique("depmin"));

        mockMvc.perform(post("/api/v1/wallet/deposits")
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"amount":"5"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        mockMvc.perform(post("/api/v1/wallet/deposits")
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"amount":"50001"}
                                """))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/v1/wallet/deposits")
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"amount":"abc"}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void stubSuccessCreditsWalletAndPublishesFirstDepositEventOnce(
            ApplicationEvents applicationEvents) throws Exception {
        String username = unique("depok");
        String bearer = registerLoginBearer(username);

        MvcResult first = deposit(bearer, "100");
        assertThat(first.getResponse().getStatus()).isEqualTo(201);
        assertThat(balance(bearer)).isEqualByComparingTo("100");
        // providerTxnId KHÔNG còn trả về client (fix M4) — lấy thẳng từ DB để test callback.
        String orderId = objectMapper.readTree(first.getResponse().getContentAsString()).get("id").asText();
        String providerTxnId = orderRepository.findFirstById(UUID.fromString(orderId))
                .orElseThrow().getProviderTxnId();
        assertThat(providerTxnId).isNotBlank();

        // FirstDepositEvent publish đúng 1 lần cho lần nạp thành công ĐẦU TIÊN.
        long firstEvents = applicationEvents.stream(FirstDepositEvent.class).count();
        assertThat(firstEvents).isEqualTo(1);

        // Nạp lần hai: KHÔNG còn FirstDepositEvent.
        deposit(bearer, "50");
        assertThat(balance(bearer)).isEqualByComparingTo("150");
        assertThat(applicationEvents.stream(FirstDepositEvent.class).count()).isEqualTo(1);

        // ---- Webhook callback IDEMPOTENT theo providerTxnId (kèm secret — fix M4) ----
        mockMvc.perform(post("/api/v1/payments/callback")
                        .header("X-Callback-Secret", callbackSecret)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"providerTxnId":"%s","status":"SUCCESS"}
                                """.formatted(providerTxnId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESS"));
        // Gửi lại callback lần nữa — số dư KHÔNG đổi (không credit lần hai).
        mockMvc.perform(post("/api/v1/payments/callback")
                        .header("X-Callback-Secret", callbackSecret)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"providerTxnId":"%s","status":"SUCCESS"}
                                """.formatted(providerTxnId)))
                .andExpect(status().isOk());
        assertThat(balance(bearer)).isEqualByComparingTo("150");
    }

    @Test
    void callbackWithoutValidSecretReturnsUnauthorized() throws Exception {
        // Thiếu header secret -> 401 (fix M4).
        assertThat(callback("STUB-bat-ky", "SUCCESS", null).getResponse().getStatus())
                .isEqualTo(401);
        // Sai secret -> 401.
        assertThat(callback("STUB-bat-ky", "SUCCESS", "sai-secret-hoan-toan").getResponse().getStatus())
                .isEqualTo(401);
    }

    @Test
    void callbackUnknownStatusIsNoOp() throws Exception {
        String username = unique("depproc");
        String bearer = registerLoginBearer(username);
        User user = userRepository.findByUsername(username).orElseThrow();

        // Tạo lệnh PENDING thẳng trong DB (stub auto-success nên API luôn ra SUCCESS).
        PaymentOrder pending = orderRepository.save(PaymentOrder.deposit(
                user.getId(), "stub", new BigDecimal("75"), "DEPOSIT:" + UUID.randomUUID()));
        pending.setProviderTxnId("STUB-processing-" + UUID.randomUUID());
        orderRepository.save(pending);

        // PROCESSING -> 200 no-op, lệnh GIỮ PENDING, ví KHÔNG đổi (fix M4).
        mockMvc.perform(post("/api/v1/payments/callback")
                        .header("X-Callback-Secret", callbackSecret)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"providerTxnId":"%s","status":"PROCESSING"}
                                """.formatted(pending.getProviderTxnId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"));
        assertThat(balance(bearer)).isEqualByComparingTo("0");

        // Sau đó FAILED -> lệnh FAILED, ví vẫn 0.
        mockMvc.perform(post("/api/v1/payments/callback")
                        .header("X-Callback-Secret", callbackSecret)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"providerTxnId":"%s","status":"FAILED"}
                                """.formatted(pending.getProviderTxnId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FAILED"));
        assertThat(balance(bearer)).isEqualByComparingTo("0");
    }

    /** Fix M5: 2 lệnh nạp SONG SONG cùng user -> FirstDepositEvent phát ĐÚNG 1 lần. */
    @Test
    void concurrentDepositsPublishFirstDepositEventOnce(
            ApplicationEvents applicationEvents) throws Exception {
        String username = unique("deprace");
        String bearer = registerLoginBearer(username);
        User user = userRepository.findByUsername(username).orElseThrow();

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(2);
        AtomicInteger created = new AtomicInteger();
        for (int i = 0; i < 2; i++) {
            pool.submit(() -> {
                try {
                    start.await();
                    MvcResult r = deposit(bearer, "100");
                    if (r.getResponse().getStatus() == 201) {
                        created.incrementAndGet();
                    }
                } catch (Exception ignored) {
                    // bỏ qua — assert bên dưới kiểm tra kết quả cuối cùng
                } finally {
                    done.countDown();
                }
            });
        }
        start.countDown();
        done.await();
        pool.shutdown();

        // Cả 2 lệnh đều thành công, tiền vào ví ĐỦ 200 (mỗi lệnh credit đúng 1 lần).
        assertThat(created.get()).isEqualTo(2);
        assertThat(balance(bearer)).isEqualByComparingTo("200");

        // Claim nguyên tử ở DB -> event chỉ phát ĐÚNG 1 lần (không phải 2).
        assertThat(applicationEvents.stream(FirstDepositEvent.class)
                .filter(e -> e.userId().equals(user.getId())).count()).isEqualTo(1);
        assertThat(firstDepositEventCounter.countFor(user.getId())).isEqualTo(1);
    }

    @Test
    void callbackUnknownTxnIdReturns404() throws Exception {
        mockMvc.perform(post("/api/v1/payments/callback")
                        .header("X-Callback-Secret", callbackSecret)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"providerTxnId":"STUB-khong-ton-tai","status":"SUCCESS"}
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void depositRequiresJwt() throws Exception {
        mockMvc.perform(post("/api/v1/wallet/deposits")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"amount":"100"}
                                """))
                .andExpect(status().isUnauthorized());
    }
}
