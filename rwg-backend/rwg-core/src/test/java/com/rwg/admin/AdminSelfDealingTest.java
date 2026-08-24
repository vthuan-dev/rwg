package com.rwg.admin;

import com.rwg.identity.domain.User;
import com.rwg.identity.domain.UserRole;
import com.rwg.identity.repository.AuditLogRepository;
import com.rwg.identity.repository.UserRepository;
import com.rwg.identity.service.AuditTrailService;
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
 * Chặn ADMIN TỰ GIAO DỊCH — lỗ hổng nghiêm trọng nhất của khu quản trị trước chặng 5.
 *
 * Trước đây chỉ có 2 vai trò PLAYER/ADMIN, nên MỌI admin đều đồng thời:
 *   1. tự cộng tiền vào ví mình (POST /admin/users/{id}/wallet/adjust)
 *   2. tự duyệt lệnh rút của mình (POST /admin/withdrawals/{id}/approve)
 * -> một người có thể chuyển tiền ra khỏi sàn trong 2 request. Audit log chỉ ghi vết
 * SAU KHI mất tiền.
 *
 * Các test dưới đây khẳng định KHÔNG CHỈ trả mã lỗi, mà SỐ DƯ VÀ TRẠNG THÁI LỆNH
 * KHÔNG ĐỔI — nếu chỉ kiểm mã lỗi thì một lần chặn hỏng (ném lỗi sau khi đã ghi tiền)
 * vẫn lọt test.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminSelfDealingTest {

    private static final String PASSWORD = "MatKhau@12345";
    private static final String WITHDRAW_PW = "RutTien@98765";

    /**
     * Body lý do cho thao tác duyệt/từ chối lệnh rút — endpoint bắt buộc có.
     *
     * Các test ở đây kiểm chốt chặn TỰ DUYỆT, nên body phải hợp lệ: nếu body sai, phản hồi 400
     * sẽ đến từ lỗi kiểm tra dữ liệu và test vẫn xanh mà không hề chạm tới chốt cần kiểm.
     */
    private static final String DECISION_BODY = "{\"note\":\"kiem tra chan tu duyet\"}";

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    UserRepository userRepository;

    @Autowired
    AuditLogRepository auditLogRepository;

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

    private void promote(String username, UserRole role) {
        User user = userRepository.findByUsername(username).orElseThrow();
        user.setRole(role);
        userRepository.save(user);
    }

    /** Admin có ví đã nạp sẵn $500 — để thử tự cộng/tự trừ chính ví mình. */
    private Staff fundedAdmin(UserRole role) throws Exception {
        String username = unique("selfdeal");
        String bearer = "Bearer " + registerAndLogin(username).get("accessToken").asText();
        deposit(bearer, "500");
        promote(username, role);
        // Login LẠI: token cũ phát hành trước khi có role nên không mang claim mới.
        String adminBearer = "Bearer " + login(username).get("accessToken").asText();
        return new Staff(userRepository.findByUsername(username).orElseThrow().getId(),
                username, adminBearer);
    }

    private void deposit(String bearer, String amount) throws Exception {
        mockMvc.perform(post("/api/v1/wallet/deposits")
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"amount":"%s"}
                                """.formatted(amount)))
                .andExpect(status().isCreated());
    }

    private BigDecimal balanceOf(String bearer) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/wallet/me").header("Authorization", bearer))
                .andExpect(status().isOk())
                .andReturn();
        return new BigDecimal(objectMapper.readTree(result.getResponse().getContentAsString())
                .get("balance").asText());
    }

    private record Staff(UUID id, String username, String bearer) {
    }

    // ===== Tự điều chỉnh ví =====

    @Test
    @DisplayName("Admin KHÔNG tự cộng tiền vào ví mình được, số dư KHÔNG đổi")
    void adminCannotCreditOwnWallet() throws Exception {
        Staff admin = fundedAdmin(UserRole.ADMIN);

        MvcResult result = mockMvc.perform(post("/api/v1/admin/users/" + admin.id() + "/wallet/adjust")
                        .header("Authorization", admin.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"direction":"CREDIT","amount":"900","reason":"tự cộng tiền cho mình"}
                                """))
                .andExpect(status().isBadRequest())
                .andReturn();

        assertThat(objectMapper.readTree(result.getResponse().getContentAsString())
                .get("code").asText()).isEqualTo("CANNOT_MODIFY_SELF");
        // Chốt quan trọng nhất: tiền KHÔNG hề nhúc nhích.
        assertThat(balanceOf(admin.bearer())).isEqualByComparingTo("500");
    }

    @Test
    @DisplayName("Admin KHÔNG tự trừ ví mình được (cũng là thao tác tài chính trên chính mình)")
    void adminCannotDebitOwnWallet() throws Exception {
        Staff admin = fundedAdmin(UserRole.ADMIN);

        mockMvc.perform(post("/api/v1/admin/users/" + admin.id() + "/wallet/adjust")
                        .header("Authorization", admin.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"direction":"DEBIT","amount":"10","reason":"tự trừ"}
                                """))
                .andExpect(status().isBadRequest());

        assertThat(balanceOf(admin.bearer())).isEqualByComparingTo("500");
    }

    @Test
    @DisplayName("Lần tự giao dịch bị chặn ĐƯỢC GHI AUDIT để điều tra")
    void blockedSelfDealingIsAudited() throws Exception {
        Staff admin = fundedAdmin(UserRole.ADMIN);
        long before = auditLogRepository.countByAction(
                AuditTrailService.ADMIN_SELF_DEALING_BLOCKED);

        mockMvc.perform(post("/api/v1/admin/users/" + admin.id() + "/wallet/adjust")
                        .header("Authorization", admin.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"direction":"CREDIT","amount":"900","reason":"thử lách"}
                                """))
                .andExpect(status().isBadRequest());

        // audit.record chạy REQUIRES_NEW nên vết vẫn còn dù transaction chính rollback.
        assertThat(auditLogRepository.countByAction(AuditTrailService.ADMIN_SELF_DEALING_BLOCKED))
                .isEqualTo(before + 1);
    }

    @Test
    @DisplayName("Admin VẪN điều chỉnh được ví người khác (không chặn quá tay)")
    void adminCanStillAdjustOtherWallets() throws Exception {
        Staff admin = fundedAdmin(UserRole.ADMIN);
        String playerName = unique("victim");
        String playerBearer = "Bearer " + registerAndLogin(playerName).get("accessToken").asText();
        deposit(playerBearer, "100");
        UUID playerId = userRepository.findByUsername(playerName).orElseThrow().getId();

        mockMvc.perform(post("/api/v1/admin/users/" + playerId + "/wallet/adjust")
                        .header("Authorization", admin.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"direction":"CREDIT","amount":"50","reason":"hoàn tiền sự cố"}
                                """))
                .andExpect(status().isOk());

        assertThat(balanceOf(playerBearer)).isEqualByComparingTo("150");
    }

    // ===== Tự duyệt lệnh rút =====

    @Test
    @DisplayName("Admin KHÔNG tự duyệt lệnh rút của mình được, lệnh vẫn PENDING")
    void adminCannotApproveOwnWithdrawal() throws Exception {
        Staff admin = fundedAdmin(UserRole.ADMIN);
        String orderId = createOwnWithdrawal(admin);

        MvcResult result = mockMvc.perform(post("/api/v1/admin/withdrawals/" + orderId + "/approve")
                        .header("Authorization", admin.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(DECISION_BODY))
                .andExpect(status().isBadRequest())
                .andReturn();

        assertThat(objectMapper.readTree(result.getResponse().getContentAsString())
                .get("code").asText()).isEqualTo("CANNOT_APPROVE_OWN_REQUEST");
        assertThat(statusOfOrder(admin.bearer(), orderId)).isEqualTo("PENDING");
    }

    @Test
    @DisplayName("Admin KHÔNG tự từ chối lệnh rút của mình được (từ chối hoàn tiền về ví)")
    void adminCannotRejectOwnWithdrawal() throws Exception {
        Staff admin = fundedAdmin(UserRole.ADMIN);
        String orderId = createOwnWithdrawal(admin);
        BigDecimal balanceBefore = balanceOf(admin.bearer());

        mockMvc.perform(post("/api/v1/admin/withdrawals/" + orderId + "/reject")
                        .header("Authorization", admin.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(DECISION_BODY))
                .andExpect(status().isBadRequest());

        assertThat(statusOfOrder(admin.bearer(), orderId)).isEqualTo("PENDING");
        // Không được hoàn tiền: nếu lọt, admin tự tạo lệnh rồi tự hoàn để lách trần ngày.
        assertThat(balanceOf(admin.bearer())).isEqualByComparingTo(balanceBefore);
    }

    @Test
    @DisplayName("Admin KHÁC vẫn duyệt được lệnh rút đó (chỉ chặn tự duyệt)")
    void anotherAdminCanApproveIt() throws Exception {
        Staff owner = fundedAdmin(UserRole.ADMIN);
        String orderId = createOwnWithdrawal(owner);

        String otherName = unique("otheradm");
        registerAndLogin(otherName);
        promote(otherName, UserRole.ADMIN);
        String other = "Bearer " + login(otherName).get("accessToken").asText();

        mockMvc.perform(post("/api/v1/admin/withdrawals/" + orderId + "/approve")
                        .header("Authorization", other)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(DECISION_BODY))
                .andExpect(status().isOk());

        assertThat(statusOfOrder(owner.bearer(), orderId)).isEqualTo("SETTLED");
    }

    // ===== helpers =====

    /** Tạo lệnh rút $50 cho chính staff đó, trả về orderId. */
    private String createOwnWithdrawal(Staff staff) throws Exception {
        mockMvc.perform(post("/api/v1/users/me/withdrawal-password")
                        .header("Authorization", staff.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"loginPassword":"%s","newWithdrawalPassword":"%s"}
                                """.formatted(PASSWORD, WITHDRAW_PW)))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/wallet/me/bank-accounts")
                        .header("Authorization", staff.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"bankCode":"VCB","accountNumber":"0123456789","holderName":"NGUYEN VAN A","withdrawalPassword":"%s"}
                                """.formatted(WITHDRAW_PW)))
                .andExpect(status().isCreated());
        MvcResult result = mockMvc.perform(post("/api/v1/wallet/withdrawals")
                        .header("Authorization", staff.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"amount":"50","withdrawalPassword":"%s"}
                                """.formatted(WITHDRAW_PW)))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText();
    }

    private String statusOfOrder(String bearer, String orderId) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/admin/withdrawals")
                        .header("Authorization", bearer)
                        .param("size", "100"))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode content = objectMapper.readTree(result.getResponse().getContentAsString())
                .get("content");
        for (JsonNode row : content) {
            if (orderId.equals(row.get("id").asText())) {
                return row.get("status").asText();
            }
        }
        throw new AssertionError("Không tìm thấy lệnh rút " + orderId + " trong danh sách admin");
    }
}
