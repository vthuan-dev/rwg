package com.rwg.payment;

import com.rwg.identity.domain.User;
import com.rwg.identity.domain.UserRole;
import com.rwg.identity.repository.UserRepository;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

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
 * Test luồng rút tiền (chặng 2 Phase b) trên H2 MODE=MySQL:
 * - thiếu mật khẩu rút tiền -> chặn; sai mật khẩu -> 401.
 * - không có bank default -> chặn.
 * - reject hoàn tiền ĐÚNG số; approve KHÔNG double-credit.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class WithdrawalFlowTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    UserRepository userRepository;

    private static final String PASSWORD = "MatKhau@12345";
    private static final String WITHDRAW_PW = "654321";

    /**
     * Body lý do cho thao tác duyệt/từ chối.
     *
     * Endpoint duyệt và từ chối đều BẮT BUỘC kèm lý do để ghi nhật ký — gọi không kèm body
     * sẽ nhận 400 trước khi chạm tới nghiệp vụ.
     */
    private static final String DECISION_BODY = "{\"note\":\"kiem tra tu dong\"}";

    private String unique(String prefix) {
        return prefix + UUID.randomUUID().toString().substring(0, 8);
    }

    private JsonNode registerAndLogin(String username) throws Exception {
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
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    /** Nạp $100 qua API deposit (stub auto-success). */
    private void fund(String bearer) throws Exception {
        mockMvc.perform(post("/api/v1/wallet/deposits")
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"amount":"100"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("SUCCESS"));
    }

    private BigDecimal balance(String bearer) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/wallet/me")
                        .header("Authorization", bearer))
                .andExpect(status().isOk())
                .andReturn();
        return new BigDecimal(objectMapper.readTree(result.getResponse().getContentAsString())
                .get("balance").asText());
    }

    private void setWithdrawalPassword(String bearer) throws Exception {
        mockMvc.perform(post("/api/v1/users/me/withdrawal-password")
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"loginPassword":"%s","newWithdrawalPassword":"%s"}
                                """.formatted(PASSWORD, WITHDRAW_PW)))
                .andExpect(status().isOk());
    }

    /**
     * PHẢI gọi sau {@link #setWithdrawalPassword}: liên kết tài khoản nhận tiền bây giờ
     * đòi xác nhận mật khẩu rút, nên tài khoản chưa đặt sẽ bị từ chối.
     */
    private String addDefaultBank(String bearer) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/wallet/me/bank-accounts")
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"bankCode":"VCB","accountNumber":"0123456789","holderName":"NGUYEN VAN A","withdrawalPassword":"%s"}
                                """.formatted(WITHDRAW_PW)))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText();
    }

    /** Đăng ký + phong role ADMIN trực tiếp trong DB (chưa có API quản trị user). */
    private String adminBearer() throws Exception {
        String username = unique("adminwd");
        JsonNode tokens = registerAndLogin(username);
        User admin = userRepository.findByUsername(username).orElseThrow();
        admin.setRole(UserRole.ADMIN);
        userRepository.save(admin);
        // Token cũ phát hành TRƯỚC khi phong role; đăng nhập lại để lấy claim ROLE_ADMIN.
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

    private ResultActions requestWithdrawal(String bearer, String amount, String pw) throws Exception {
        return mockMvc.perform(post("/api/v1/wallet/withdrawals")
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"amount":"%s","withdrawalPassword":"%s"}
                                """.formatted(amount, pw)));
    }

    @Test
    void missingWithdrawalPasswordIsRejected() throws Exception {
        String username = unique("wdnopw");
        JsonNode tokens = registerAndLogin(username);
        String bearer = "Bearer " + tokens.get("accessToken").asText();
        fund(bearer);

        requestWithdrawal(bearer, "30", WITHDRAW_PW)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("WITHDRAWAL_PASSWORD_NOT_SET"));
    }

    @Test
    void wrongWithdrawalPasswordReturns401() throws Exception {
        String username = unique("wdwrongpw");
        JsonNode tokens = registerAndLogin(username);
        String bearer = "Bearer " + tokens.get("accessToken").asText();
        fund(bearer);
        setWithdrawalPassword(bearer);

        requestWithdrawal(bearer, "30", "999999")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
    }

    @Test
    void missingDefaultBankAccountIsRejected() throws Exception {
        String username = unique("wdnobank");
        JsonNode tokens = registerAndLogin(username);
        String bearer = "Bearer " + tokens.get("accessToken").asText();
        fund(bearer);
        setWithdrawalPassword(bearer);

        requestWithdrawal(bearer, "30", WITHDRAW_PW)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BANK_ACCOUNT_REQUIRED"));
    }

    @Test
    void belowMinAndAboveMaxAreRejected() throws Exception {
        String username = unique("wdlimit");
        JsonNode tokens = registerAndLogin(username);
        String bearer = "Bearer " + tokens.get("accessToken").asText();
        fund(bearer); // $100
        setWithdrawalPassword(bearer);
        addDefaultBank(bearer);

        // Dưới min $20 -> 400.
        requestWithdrawal(bearer, "10", WITHDRAW_PW)
                .andExpect(status().isBadRequest());

        // Vượt số dư ($100) -> INSUFFICIENT_BALANCE.
        requestWithdrawal(bearer, "150", WITHDRAW_PW)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_BALANCE"));
    }

    @Test
    void approveDoesNotDoubleCreditAndIsIdempotent() throws Exception {
        String username = unique("wdapprove");
        JsonNode tokens = registerAndLogin(username);
        String bearer = "Bearer " + tokens.get("accessToken").asText();
        fund(bearer); // 100
        setWithdrawalPassword(bearer);
        addDefaultBank(bearer);

        MvcResult created = requestWithdrawal(bearer, "30", WITHDRAW_PW)
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode order = objectMapper.readTree(created.getResponse().getContentAsString());
        assertThat(order.get("status").asText()).isEqualTo("PENDING");
        assertThat(balance(bearer)).isEqualByComparingTo("70"); // đã debit ngay khi tạo lệnh

        String adminBearer = adminBearer();
        mockMvc.perform(post("/api/v1/admin/withdrawals/" + order.get("id").asText() + "/approve")
                        .header("Authorization", adminBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(DECISION_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SETTLED"));

        // Approve KHÔNG hoàn tiền — số dư giữ nguyên 70 (không double-credit).
        assertThat(balance(bearer)).isEqualByComparingTo("70");

        // Duyệt lại lệnh đã SETTLED -> 400 (idempotent-guard, không đổi trạng thái).
        mockMvc.perform(post("/api/v1/admin/withdrawals/" + order.get("id").asText() + "/approve")
                        .header("Authorization", adminBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(DECISION_BODY))
                .andExpect(status().isBadRequest());
        assertThat(balance(bearer)).isEqualByComparingTo("70");
    }

    @Test
    void rejectRefundsExactAmountAndIsIdempotent() throws Exception {
        String username = unique("wdreject");
        JsonNode tokens = registerAndLogin(username);
        String bearer = "Bearer " + tokens.get("accessToken").asText();
        fund(bearer); // 100
        setWithdrawalPassword(bearer);
        addDefaultBank(bearer);

        MvcResult created = requestWithdrawal(bearer, "45", WITHDRAW_PW)
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode order = objectMapper.readTree(created.getResponse().getContentAsString());
        assertThat(balance(bearer)).isEqualByComparingTo("55"); // 100 - 45

        String adminBearer = adminBearer();
        mockMvc.perform(post("/api/v1/admin/withdrawals/" + order.get("id").asText() + "/reject")
                        .header("Authorization", adminBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(DECISION_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("VOIDED"));

        // Hoàn tiền ĐÚNG số đã trừ: 55 + 45 = 100.
        assertThat(balance(bearer)).isEqualByComparingTo("100");

        // Từ chối lại lệnh đã VOIDED -> 400, KHÔNG hoàn tiền lần hai.
        mockMvc.perform(post("/api/v1/admin/withdrawals/" + order.get("id").asText() + "/reject")
                        .header("Authorization", adminBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(DECISION_BODY))
                .andExpect(status().isBadRequest());
        assertThat(balance(bearer)).isEqualByComparingTo("100");
    }

    @Test
    void adminEndpointForbidsPlayer() throws Exception {
        String username = unique("wdplayer");
        JsonNode tokens = registerAndLogin(username);
        String bearer = "Bearer " + tokens.get("accessToken").asText();
        mockMvc.perform(post("/api/v1/admin/withdrawals/" + UUID.randomUUID() + "/approve")
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(DECISION_BODY))
                .andExpect(status().isForbidden());
    }

    /**
     * Fix C2: 2 thread approve+reject SONG SONG cùng lệnh -> đúng 1 kết quả,
     * refund đúng 1 lần (VOIDED) hoặc 0 lần nếu approve thắng (SETTLED).
     * KHÔNG có cảnh vừa SETTLED vừa refund (mất tiền) hay refund kép.
     */
    @Test
    void concurrentApproveRejectYieldsSingleOutcome() throws Exception {
        String username = unique("wdrace");
        JsonNode tokens = registerAndLogin(username);
        String bearer = "Bearer " + tokens.get("accessToken").asText();
        fund(bearer); // 100
        setWithdrawalPassword(bearer);
        addDefaultBank(bearer);

        MvcResult created = requestWithdrawal(bearer, "40", WITHDRAW_PW)
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode order = objectMapper.readTree(created.getResponse().getContentAsString());
        String orderId = order.get("id").asText();
        assertThat(balance(bearer)).isEqualByComparingTo("60"); // 100 - 40

        String adminBearer = adminBearer();
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(2);
        AtomicInteger ok = new AtomicInteger();      // 200 (thắng)
        AtomicInteger conflict = new AtomicInteger(); // 400 (thua - đã bị chuyển)

        String[] actions = {"approve", "reject"};
        for (String action : actions) {
            pool.submit(() -> {
                try {
                    start.await();
                    MvcResult r = mockMvc.perform(post("/api/v1/admin/withdrawals/" + orderId + "/" + action)
                                    .header("Authorization", adminBearer)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(DECISION_BODY))
                            .andReturn();
                    int sc = r.getResponse().getStatus();
                    if (sc == 200) ok.incrementAndGet();
                    else if (sc == 400) conflict.incrementAndGet();
                } catch (Exception ignored) {
                } finally {
                    done.countDown();
                }
            });
        }
        start.countDown();
        done.await();
        pool.shutdown();

        // Đúng 1 thao tác thắng (200), thao tác kia bị chặn (400).
        assertThat(ok.get()).isEqualTo(1);
        assertThat(conflict.get()).isEqualTo(1);

        // Số dư cuối CỐ ĐỊNH theo kết quả thắng: SETTLED -> giữ 60; VOIDED -> hoàn về 100.
        // (Dùng compareTo thay vì isIn/equals vì BigDecimal.equals phân biệt scale.)
        BigDecimal finalBalance = balance(bearer);
        boolean approveThang = finalBalance.compareTo(new BigDecimal("60")) == 0;   // KHÔNG refund
        boolean rejectThang = finalBalance.compareTo(new BigDecimal("100")) == 0;  // refund ĐÚNG 1 lần
        assertThat(approveThang || rejectThang)
                .as("Số dư cuối phải là 60 (approve thắng) hoặc 100 (reject thắng, refund 1 lần), thực tế: %s", finalBalance)
                .isTrue();
    }

    /**
     * Fix M3: 2 lệnh rút SONG SONG cùng user -> tổng KHÔNG vượt hạn mức ngày ($5,000).
     * Khóa row ví per-user (FOR UPDATE) serialize sumAmountSince + insert.
     * Dùng số dư LỚN hơn hạn mức để hạn mức ngày (chứ KHÔNG phải số dư) là ràng buộc chặn.
     */
    @Test
    void concurrentWithdrawalsRespectDailyLimit() throws Exception {
        String username = unique("wdlimitrace");
        JsonNode tokens = registerAndLogin(username);
        String bearer = "Bearer " + tokens.get("accessToken").asText();
        mockMvc.perform(post("/api/v1/wallet/deposits")
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"amount":"8000"}
                                """))
                .andExpect(status().isCreated());
        setWithdrawalPassword(bearer);
        addDefaultBank(bearer);

        // Mỗi lệnh $4,000; hạn mức ngày $5,000 -> tối đa 1 lệnh được duyệt.
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(2);
        AtomicInteger created = new AtomicInteger();
        AtomicInteger limited = new AtomicInteger();

        for (int i = 0; i < 2; i++) {
            pool.submit(() -> {
                try {
                    start.await();
                    MvcResult r = mockMvc.perform(post("/api/v1/wallet/withdrawals")
                                    .header("Authorization", bearer)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content("""
                                            {"amount":"4000","withdrawalPassword":"%s"}
                                            """.formatted(WITHDRAW_PW)))
                            .andReturn();
                    int sc = r.getResponse().getStatus();
                    if (sc == 201) created.incrementAndGet();
                    else if (sc == 400) limited.incrementAndGet();
                } catch (Exception ignored) {
                } finally {
                    done.countDown();
                }
            });
        }
        start.countDown();
        done.await();
        pool.shutdown();

        // Đúng 1 lệnh tạo (lệnh kia bị WITHDRAWAL_LIMIT_EXCEEDED do đã thấy $4,000 dùng trong ngày),
        // tổng rút = $4,000 <= hạn mức $5,000; số dư = 8000 - 4000 = 4000.
        assertThat(created.get()).isEqualTo(1);
        assertThat(limited.get()).isEqualTo(1);
        assertThat(balance(bearer)).isEqualByComparingTo("4000");
    }

    /**
     * Fix m9: brute-force mật khẩu rút tiền -> sau ngưỡng sai bị KHÓA tạm thời (429),
     * kể cả khi sau đó gửi đúng mật khẩu.
     */
    @Test
    void withdrawalPasswordBruteForceIsLocked() throws Exception {
        String username = unique("wdbrute");
        JsonNode tokens = registerAndLogin(username);
        String bearer = "Bearer " + tokens.get("accessToken").asText();
        fund(bearer); // 100
        setWithdrawalPassword(bearer);
        addDefaultBank(bearer);

        // Gửi liên tiếp mật khẩu rút tiền SAI cho đến khi bị khóa (lockThreshold=10).
        boolean locked = false;
        for (int i = 0; i < 15 && !locked; i++) {
            MvcResult r = requestWithdrawal(bearer, "25", "000000").andReturn();
            if (r.getResponse().getStatus() == 429) {
                locked = true;
            }
        }
        assertThat(locked).as("phải bị khóa (429) sau nhiều lần sai mật khẩu rút tiền").isTrue();

        // Sau khi khóa: gửi ĐÚNG mật khẩu vẫn bị từ chối (429) cho đến hết cửa sổ.
        requestWithdrawal(bearer, "25", WITHDRAW_PW)
                .andExpect(status().isTooManyRequests());
        // Tiền KHÔNG bị trừ trong suốt quá trình brute-force.
        assertThat(balance(bearer)).isEqualByComparingTo("100");
    }

    private ResultActions requestWithdrawalWithBank(String bearer, String amount, String pw, String bankAccountId) throws Exception {
        return mockMvc.perform(post("/api/v1/wallet/withdrawals")
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"amount":"%s","withdrawalPassword":"%s","bankAccountId":"%s"}
                                """.formatted(amount, pw, bankAccountId)));
    }

    @Test
    void withdrawWithSpecificBankAccount() throws Exception {
        String username = unique("wdspec");
        JsonNode tokens = registerAndLogin(username);
        String bearer = "Bearer " + tokens.get("accessToken").asText();
        fund(bearer); // 100
        setWithdrawalPassword(bearer);
        String bankId = addDefaultBank(bearer);

        // 1. Rút chỉ định bankAccountId của chính mình -> thành công
        requestWithdrawalWithBank(bearer, "30", WITHDRAW_PW, bankId)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.bankAccountId").value(bankId));
    }

    @Test
    void withdrawWithOtherUserBankAccountReturns404() throws Exception {
        String username1 = unique("wdother1");
        JsonNode tokens1 = registerAndLogin(username1);
        String bearer1 = "Bearer " + tokens1.get("accessToken").asText();
        setWithdrawalPassword(bearer1);
        String otherUserBankId = addDefaultBank(bearer1);

        String username2 = unique("wdother2");
        JsonNode tokens2 = registerAndLogin(username2);
        String bearer2 = "Bearer " + tokens2.get("accessToken").asText();
        fund(bearer2); // 100
        setWithdrawalPassword(bearer2);
        addDefaultBank(bearer2); // default bank của user 2

        // 2. Rút chỉ định bankAccountId của người khác -> 404 NOT_FOUND
        requestWithdrawalWithBank(bearer2, "30", WITHDRAW_PW, otherUserBankId)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void withdrawWithInvalidBankAccountUuidReturns400() throws Exception {
        String username = unique("wdinvaliduuid");
        JsonNode tokens = registerAndLogin(username);
        String bearer = "Bearer " + tokens.get("accessToken").asText();
        fund(bearer); // 100
        setWithdrawalPassword(bearer);
        addDefaultBank(bearer);

        // 3. Truyền bankAccountId không phải UUID hợp lệ -> 400
        requestWithdrawalWithBank(bearer, "30", WITHDRAW_PW, "not-a-uuid-at-all")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }
}
