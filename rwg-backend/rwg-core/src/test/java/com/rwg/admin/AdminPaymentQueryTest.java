package com.rwg.admin;

import com.rwg.identity.domain.User;
import com.rwg.identity.domain.UserRole;
import com.rwg.identity.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Kiểm chứng tra soát lệnh nạp/rút + nhật ký hệ thống phía admin (chặng 3).
 * Toàn bộ là READ-ONLY: không endpoint nào ở đây được đổi trạng thái lệnh.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminPaymentQueryTest {

    private static final String PASSWORD = "MatKhau@12345";
    private static final String WITHDRAW_PW = "654321";

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    UserRepository userRepository;

    private String unique(String prefix) {
        return prefix + UUID.randomUUID().toString().substring(0, 8);
    }

    private JsonNode registerAndLogin(String username) throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"%s","email":"%s@example.com","password":"%s"}
                                """.formatted(username, username, PASSWORD)))
                .andExpect(status().isCreated());
        return login(username);
    }

    private JsonNode login(String username) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"identifier":"%s","password":"%s"}
                                """.formatted(username, PASSWORD)))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private String adminBearer() throws Exception {
        String username = unique("admpay");
        registerAndLogin(username);
        User admin = userRepository.findByUsername(username).orElseThrow();
        admin.setRole(UserRole.ADMIN);
        userRepository.save(admin);
        return "Bearer " + login(username).get("accessToken").asText();
    }

    /** Player đã nạp $100, đặt mật khẩu rút, thêm bank mặc định và tạo 1 lệnh rút PENDING. */
    private Player playerWithPendingWithdrawal() throws Exception {
        String username = unique("pay");
        JsonNode tokens = registerAndLogin(username);
        String bearer = "Bearer " + tokens.get("accessToken").asText();

        mockMvc.perform(post("/api/v1/wallet/deposits")
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"amount":"100"}
                                """))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/v1/users/me/withdrawal-password")
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"loginPassword":"%s","newWithdrawalPassword":"%s"}
                                """.formatted(PASSWORD, WITHDRAW_PW)))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/wallet/me/bank-accounts")
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"bankCode":"VCB","accountNumber":"0123456789","holderName":"NGUYEN VAN A"}
                                """))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/v1/wallet/withdrawals")
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"amount":"50","withdrawalPassword":"%s"}
                                """.formatted(WITHDRAW_PW)))
                .andExpect(status().isCreated());

        return new Player(userRepository.findByUsername(username).orElseThrow().getId(), bearer);
    }

    private record Player(UUID id, String bearer) {
    }

    @Test
    void playerCannotQueryAdminPaymentEndpoints() throws Exception {
        Player player = playerWithPendingWithdrawal();

        mockMvc.perform(get("/api/v1/admin/deposits").header("Authorization", player.bearer()))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/admin/withdrawals").header("Authorization", player.bearer()))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/admin/audit/logs").header("Authorization", player.bearer()))
                .andExpect(status().isForbidden());
    }

    @Test
    void anonymousRequestIsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/admin/withdrawals"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void depositsAndWithdrawalsAreFilterableByUser() throws Exception {
        Player player = playerWithPendingWithdrawal();
        String admin = adminBearer();

        MvcResult deposits = mockMvc.perform(get("/api/v1/admin/deposits")
                        .header("Authorization", admin)
                        .param("userId", player.id().toString()))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode depositBody = objectMapper.readTree(deposits.getResponse().getContentAsString());
        assertThat(depositBody.get("totalElements").asLong()).isEqualTo(1);
        assertThat(depositBody.get("content").get(0).get("type").asText()).isEqualTo("DEPOSIT");
        assertThat(depositBody.get("content").get(0).get("status").asText()).isEqualTo("SUCCESS");

        MvcResult withdrawals = mockMvc.perform(get("/api/v1/admin/withdrawals")
                        .header("Authorization", admin)
                        .param("userId", player.id().toString())
                        .param("status", "PENDING"))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode withdrawalBody = objectMapper.readTree(withdrawals.getResponse().getContentAsString());
        assertThat(withdrawalBody.get("totalElements").asLong()).isEqualTo(1);
        assertThat(withdrawalBody.get("content").get(0).get("type").asText()).isEqualTo("WITHDRAWAL");

        // Lệnh rút KHÔNG lọt vào danh sách nạp (filter type là bắt buộc, không nhầm lẫn).
        MvcResult settled = mockMvc.perform(get("/api/v1/admin/deposits")
                        .header("Authorization", admin)
                        .param("userId", player.id().toString())
                        .param("status", "PENDING"))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(objectMapper.readTree(settled.getResponse().getContentAsString())
                .get("totalElements").asLong()).isZero();
    }

    @Test
    void pendingWithdrawalCountIncreasesAfterRequest() throws Exception {
        String admin = adminBearer();
        long before = pendingCount(admin);

        playerWithPendingWithdrawal();

        assertThat(pendingCount(admin)).isEqualTo(before + 1);
    }

    private long pendingCount(String admin) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/admin/withdrawals/pending-count")
                        .header("Authorization", admin))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .get("pendingWithdrawals").asLong();
    }

    @Test
    void dateRangeOutsideActivityReturnsNothing() throws Exception {
        Player player = playerWithPendingWithdrawal();
        String admin = adminBearer();

        // Khoảng ngày nằm hoàn toàn trong quá khứ -> không có lệnh nào.
        String past = LocalDate.now(ZoneOffset.UTC).minusDays(30).toString();
        String pastEnd = LocalDate.now(ZoneOffset.UTC).minusDays(20).toString();

        MvcResult result = mockMvc.perform(get("/api/v1/admin/deposits")
                        .header("Authorization", admin)
                        .param("userId", player.id().toString())
                        .param("fromDate", past)
                        .param("toDate", pastEnd))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(objectMapper.readTree(result.getResponse().getContentAsString())
                .get("totalElements").asLong()).isZero();

        // Khoảng ngày bao hôm nay -> thấy lệnh nạp (biên toDate là nửa mở, bao trọn ngày).
        String today = LocalDate.now(ZoneOffset.UTC).toString();
        MvcResult included = mockMvc.perform(get("/api/v1/admin/deposits")
                        .header("Authorization", admin)
                        .param("userId", player.id().toString())
                        .param("fromDate", today)
                        .param("toDate", today))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(objectMapper.readTree(included.getResponse().getContentAsString())
                .get("totalElements").asLong()).isEqualTo(1);
    }

    @Test
    void invalidFilterValuesAreRejected() throws Exception {
        String admin = adminBearer();

        mockMvc.perform(get("/api/v1/admin/deposits")
                        .header("Authorization", admin)
                        .param("status", "KHONG_TON_TAI"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        mockMvc.perform(get("/api/v1/admin/deposits")
                        .header("Authorization", admin)
                        .param("fromDate", "20-08-2026"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        // fromDate sau toDate -> INVALID_REQUEST, không trả kết quả rỗng im lặng.
        mockMvc.perform(get("/api/v1/admin/deposits")
                        .header("Authorization", admin)
                        .param("fromDate", LocalDate.now(ZoneOffset.UTC).toString())
                        .param("toDate", LocalDate.now(ZoneOffset.UTC).minusDays(10).toString()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    void adminActionsLeaveAuditTrail() throws Exception {
        Player player = playerWithPendingWithdrawal();
        String admin = adminBearer();

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .patch("/api/v1/admin/users/" + player.id() + "/kyc")
                        .header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"kycLevel":"LEVEL_1","reason":"đã xác minh CMND"}
                                """))
                .andExpect(status().isOk());

        MvcResult logs = mockMvc.perform(get("/api/v1/admin/audit/logs")
                        .header("Authorization", admin)
                        .param("action", "ADMIN_KYC_UPDATED")
                        .param("targetId", player.id().toString()))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode body = objectMapper.readTree(logs.getResponse().getContentAsString());

        assertThat(body.get("totalElements").asLong()).isEqualTo(1);
        JsonNode entry = body.get("content").get(0);
        assertThat(entry.get("action").asText()).isEqualTo("ADMIN_KYC_UPDATED");
        assertThat(entry.get("targetType").asText()).isEqualTo("USER");
        assertThat(entry.get("details").asText()).contains("LEVEL_1");
    }
}
