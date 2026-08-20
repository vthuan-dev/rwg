package com.rwg.admin;

import com.rwg.identity.domain.User;
import com.rwg.identity.domain.UserRole;
import com.rwg.identity.repository.AdminApprovalRequestRepository;
import com.rwg.identity.repository.UserRepository;
import com.rwg.wallet.domain.WalletRefType;
import com.rwg.wallet.repository.WalletRepository;
import com.rwg.wallet.repository.WalletTransactionRepository;
import org.junit.jupiter.api.DisplayName;
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

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Quy trình 4 mắt (maker-checker) và hạn mức điều chỉnh ví — chặng 5.
 *
 * Bất biến quan trọng nhất: khi vượt hạn mức, TIỀN KHÔNG ĐƯỢC CHUYỂN cho tới khi có
 * admin THỨ HAI phê duyệt. Nhiều test dưới đây kiểm số dư ở TRẠNG THÁI GIỮA (sau khi
 * tạo đề nghị, trước khi duyệt) — nếu chỉ kiểm kết quả cuối thì một lỗi "chuyển tiền
 * ngay rồi mới tạo đề nghị" vẫn lọt.
 *
 * Hạn mức test (application-test.yml, trùng mặc định production):
 *   - adjust-max-per-transaction = 1000  -> hơn 1000 phải qua 4 mắt
 *   - adjust-daily-max-per-admin = 10000 -> tổng mỗi admin mỗi ngày
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminMakerCheckerTest {

    private static final String PASSWORD = "MatKhau@12345";

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    UserRepository userRepository;

    @Autowired
    WalletRepository walletRepository;

    @Autowired
    WalletTransactionRepository transactionRepository;

    @Autowired
    AdminApprovalRequestRepository approvalRepository;

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

    private String staffBearer(UserRole role) throws Exception {
        String username = unique("mc");
        registerAndLogin(username);
        User user = userRepository.findByUsername(username).orElseThrow();
        user.setRole(role);
        userRepository.save(user);
        return "Bearer " + login(username).get("accessToken").asText();
    }

    /** Player ví $200. */
    private UUID fundedPlayerId() throws Exception {
        String username = unique("mctarget");
        String bearer = "Bearer " + registerAndLogin(username).get("accessToken").asText();
        mockMvc.perform(post("/api/v1/wallet/deposits")
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"amount":"200"}
                                """))
                .andExpect(status().isCreated());
        return userRepository.findByUsername(username).orElseThrow().getId();
    }

    private BigDecimal balanceOf(UUID userId) {
        return walletRepository.findByUserId(userId)
                .map(wallet -> wallet.getBalance())
                .orElse(BigDecimal.ZERO);
    }

    private long adjustmentRows(UUID userId) {
        return walletRepository.findByUserId(userId)
                .map(wallet -> transactionRepository.countByWalletIdAndRefType(
                        wallet.getId(), WalletRefType.ADJUSTMENT))
                .orElse(0L);
    }

    /** Gửi yêu cầu điều chỉnh, trả về response body. */
    private JsonNode adjust(String bearer, UUID userId, String direction, String amount,
                            int expectedStatus) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/admin/users/" + userId + "/wallet/adjust")
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"direction":"%s","amount":"%s","reason":"kiem thu han muc"}
                                """.formatted(direction, amount)))
                .andExpect(status().is(expectedStatus))
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    // ===== Dưới hạn mức: thực thi ngay =====

    @Test
    @DisplayName("Dưới trần mỗi lần -> 200 và tiền chuyển NGAY, không tạo đề nghị")
    void underLimitExecutesImmediately() throws Exception {
        String admin = staffBearer(UserRole.ADMIN);
        UUID playerId = fundedPlayerId();

        JsonNode body = adjust(admin, playerId, "CREDIT", "1000", 200);

        assertThat(body.get("balanceAfter").asText()).isEqualTo("1200.00000000");
        assertThat(balanceOf(playerId)).isEqualByComparingTo("1200");
        assertThat(approvalRepository.findAll().stream()
                .filter(request -> request.getTargetUserId().equals(playerId))
                .count()).isZero();
    }

    // ===== Vượt hạn mức: chờ duyệt =====

    @Test
    @DisplayName("Vượt trần mỗi lần -> 202 và TIỀN CHƯA CHUYỂN")
    void overLimitCreatesPendingRequestWithoutMovingMoney() throws Exception {
        String admin = staffBearer(UserRole.ADMIN);
        UUID playerId = fundedPlayerId();

        JsonNode body = adjust(admin, playerId, "CREDIT", "1000.00000001", 202);

        assertThat(body.get("status").asText()).isEqualTo("PENDING");
        assertThat(body.get("checkerId").isNull()).isTrue();
        // Chốt quan trọng nhất của cả test class.
        assertThat(balanceOf(playerId)).isEqualByComparingTo("200");
        assertThat(adjustmentRows(playerId)).isZero();
    }

    @Test
    @DisplayName("Người TẠO đề nghị KHÔNG tự duyệt được, tiền vẫn chưa chuyển")
    void makerCannotApproveOwnRequest() throws Exception {
        String maker = staffBearer(UserRole.ADMIN);
        UUID playerId = fundedPlayerId();
        String requestId = adjust(maker, playerId, "CREDIT", "5000", 202).get("id").asText();

        MvcResult result = mockMvc.perform(post("/api/v1/admin/approvals/" + requestId + "/approve")
                        .header("Authorization", maker))
                .andExpect(status().isBadRequest())
                .andReturn();

        assertThat(objectMapper.readTree(result.getResponse().getContentAsString())
                .get("code").asText()).isEqualTo("CANNOT_APPROVE_OWN_REQUEST");
        assertThat(balanceOf(playerId)).isEqualByComparingTo("200");
    }

    @Test
    @DisplayName("Admin THỨ HAI duyệt -> tiền mới chuyển, sinh ĐÚNG 1 dòng ledger")
    void secondAdminApprovalMovesMoneyExactlyOnce() throws Exception {
        String maker = staffBearer(UserRole.ADMIN);
        String checker = staffBearer(UserRole.FINANCE);
        UUID playerId = fundedPlayerId();
        String requestId = adjust(maker, playerId, "CREDIT", "5000", 202).get("id").asText();

        assertThat(balanceOf(playerId)).isEqualByComparingTo("200");

        MvcResult result = mockMvc.perform(post("/api/v1/admin/approvals/" + requestId + "/approve")
                        .header("Authorization", checker)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"note":"da doi chieu chung tu"}
                                """))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(body.get("status").asText()).isEqualTo("APPROVED");
        assertThat(body.get("checkerId").isNull()).isFalse();

        assertThat(balanceOf(playerId)).isEqualByComparingTo("5200");
        assertThat(adjustmentRows(playerId)).isEqualTo(1);
    }

    @Test
    @DisplayName("Bấm duyệt HAI LẦN không cộng tiền hai lần")
    void approvingTwiceDoesNotPayTwice() throws Exception {
        String maker = staffBearer(UserRole.ADMIN);
        String checker = staffBearer(UserRole.FINANCE);
        UUID playerId = fundedPlayerId();
        String requestId = adjust(maker, playerId, "CREDIT", "3000", 202).get("id").asText();

        mockMvc.perform(post("/api/v1/admin/approvals/" + requestId + "/approve")
                        .header("Authorization", checker))
                .andExpect(status().isOk());
        BigDecimal afterFirst = balanceOf(playerId);

        // Lần hai: UPDATE điều kiện nguyên tử không khớp PENDING nữa -> 400.
        MvcResult second = mockMvc.perform(post("/api/v1/admin/approvals/" + requestId + "/approve")
                        .header("Authorization", checker))
                .andExpect(status().isBadRequest())
                .andReturn();

        assertThat(objectMapper.readTree(second.getResponse().getContentAsString())
                .get("code").asText()).isEqualTo("APPROVAL_ALREADY_DECIDED");
        assertThat(balanceOf(playerId)).isEqualByComparingTo(afterFirst);
        assertThat(adjustmentRows(playerId)).isEqualTo(1);
    }

    @Test
    @DisplayName("Từ chối đề nghị -> KHÔNG chuyển tiền, không sinh dòng ledger")
    void rejectionMovesNoMoney() throws Exception {
        String maker = staffBearer(UserRole.ADMIN);
        String checker = staffBearer(UserRole.FINANCE);
        UUID playerId = fundedPlayerId();
        String requestId = adjust(maker, playerId, "CREDIT", "9000", 202).get("id").asText();

        MvcResult result = mockMvc.perform(post("/api/v1/admin/approvals/" + requestId + "/reject")
                        .header("Authorization", checker)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"note":"khong co chung tu kem theo"}
                                """))
                .andExpect(status().isOk())
                .andReturn();

        assertThat(objectMapper.readTree(result.getResponse().getContentAsString())
                .get("status").asText()).isEqualTo("REJECTED");
        assertThat(balanceOf(playerId)).isEqualByComparingTo("200");
        assertThat(adjustmentRows(playerId)).isZero();
    }

    @Test
    @DisplayName("Đề nghị đã từ chối KHÔNG duyệt lại được")
    void rejectedRequestCannotBeApprovedLater() throws Exception {
        String maker = staffBearer(UserRole.ADMIN);
        String checker = staffBearer(UserRole.FINANCE);
        UUID playerId = fundedPlayerId();
        String requestId = adjust(maker, playerId, "CREDIT", "2000", 202).get("id").asText();

        mockMvc.perform(post("/api/v1/admin/approvals/" + requestId + "/reject")
                        .header("Authorization", checker))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/admin/approvals/" + requestId + "/approve")
                        .header("Authorization", checker))
                .andExpect(status().isBadRequest());

        assertThat(balanceOf(playerId)).isEqualByComparingTo("200");
    }

    @Test
    @DisplayName("Đề nghị không tồn tại -> 404")
    void unknownRequestReturnsNotFound() throws Exception {
        mockMvc.perform(post("/api/v1/admin/approvals/" + UUID.randomUUID() + "/approve")
                        .header("Authorization", staffBearer(UserRole.ADMIN)))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Hàng đợi lọc được theo status PENDING")
    void queueFiltersPending() throws Exception {
        String maker = staffBearer(UserRole.ADMIN);
        UUID playerId = fundedPlayerId();
        adjust(maker, playerId, "CREDIT", "4000", 202);

        MvcResult result = mockMvc.perform(get("/api/v1/admin/approvals")
                        .header("Authorization", maker)
                        .param("status", "PENDING")
                        .param("size", "100"))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode content = objectMapper.readTree(result.getResponse().getContentAsString())
                .get("content");
        assertThat(content.isArray()).isTrue();
        content.forEach(row -> assertThat(row.get("status").asText()).isEqualTo("PENDING"));
    }

    // ===== Trần ngày =====

    @Test
    @DisplayName("Vượt TRẦN NGÀY bị chặn dù từng lần đều dưới trần mỗi lần")
    void dailyLimitBlocksSalamiSlicing() throws Exception {
        String admin = staffBearer(UserRole.ADMIN);
        UUID playerId = fundedPlayerId();

        // 10 lần x 1000 = 10000 = đúng bằng trần ngày -> vẫn cho.
        for (int i = 0; i < 10; i++) {
            adjust(admin, playerId, "CREDIT", "1000", 200);
        }
        BigDecimal afterLimit = balanceOf(playerId);

        // Lần thứ 11 dù chỉ $1 cũng vượt tổng ngày -> chặn.
        MvcResult result = mockMvc.perform(post("/api/v1/admin/users/" + playerId + "/wallet/adjust")
                        .header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"direction":"CREDIT","amount":"1","reason":"vuot tran ngay"}
                                """))
                .andExpect(status().isBadRequest())
                .andReturn();

        assertThat(objectMapper.readTree(result.getResponse().getContentAsString())
                .get("code").asText()).isEqualTo("ADMIN_LIMIT_EXCEEDED");
        assertThat(balanceOf(playerId)).isEqualByComparingTo(afterLimit);
    }

    @Test
    @DisplayName("Trần ngày tính RIÊNG từng admin, không dùng chung")
    void dailyLimitIsPerAdmin() throws Exception {
        String first = staffBearer(UserRole.ADMIN);
        String second = staffBearer(UserRole.FINANCE);
        UUID playerId = fundedPlayerId();

        for (int i = 0; i < 10; i++) {
            adjust(first, playerId, "CREDIT", "1000", 200);
        }
        // Admin thứ nhất đã hết hạn mức...
        adjust(first, playerId, "CREDIT", "1", 400);
        // ...nhưng admin thứ hai vẫn còn nguyên hạn mức của mình.
        adjust(second, playerId, "CREDIT", "1000", 200);

        assertThat(balanceOf(playerId)).isEqualByComparingTo("11200");
    }
}
