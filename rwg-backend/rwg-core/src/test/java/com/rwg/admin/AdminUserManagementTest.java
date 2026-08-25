package com.rwg.admin;

import com.rwg.identity.domain.User;
import com.rwg.identity.domain.UserRole;
import com.rwg.identity.domain.UserStatus;
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

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Kiểm chứng nghiệp vụ quản trị người dùng (chặng 3):
 * - Player KHÔNG chạm được /api/v1/admin/** (403).
 * - Khóa user -> user KHÔNG đăng nhập được VÀ refresh token đang giữ bị thu hồi.
 * - Mở lại -> đăng nhập được.
 * - BANNED là trạng thái CUỐI: không mở lại.
 * - Thiếu reason khi khóa -> 400; admin tự sửa mình -> 400.
 * - Đổi role thu hồi session (claim roles cũ đã lệch DB).
 * - Mọi thao tác đều để lại vết trong audit_log.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminUserManagementTest {

    private static final String PASSWORD = "MatKhau@12345";

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

    /** Đăng ký rồi phong ADMIN trực tiếp trong DB, đăng nhập lại để token có ROLE_ADMIN. */
    private AdminActor admin() throws Exception {
        String username = unique("adm");
        registerAndLogin(username);
        User user = userRepository.findByUsername(username).orElseThrow();
        user.setRole(UserRole.ADMIN);
        userRepository.save(user);
        JsonNode tokens = login(username);
        return new AdminActor(user.getId(), "Bearer " + tokens.get("accessToken").asText());
    }

    private record AdminActor(UUID id, String bearer) {
    }

    private void changeStatus(String adminBearer, UUID userId, String status, String reason,
                              int expectedHttpStatus) throws Exception {
        String body = reason == null
                ? """
                {"status":"%s"}
                """.formatted(status)
                : """
                {"status":"%s","reason":"%s"}
                """.formatted(status, reason);
        mockMvc.perform(patch("/api/v1/admin/users/" + userId + "/status")
                        .header("Authorization", adminBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().is(expectedHttpStatus));
    }

    @Test
    void playerCannotAccessAdminUserEndpoints() throws Exception {
        JsonNode tokens = registerAndLogin(unique("plain"));
        String bearer = "Bearer " + tokens.get("accessToken").asText();

        mockMvc.perform(get("/api/v1/admin/users").header("Authorization", bearer))
                .andExpect(status().isForbidden());
        mockMvc.perform(patch("/api/v1/admin/users/" + UUID.randomUUID() + "/status")
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status":"LOCKED","reason":"thử vượt quyền"}
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    void lockingUserBlocksLoginAndRevokesExistingRefreshToken() throws Exception {
        String victim = unique("lockme");
        JsonNode victimTokens = registerAndLogin(victim);
        String victimRefresh = victimTokens.get("refreshToken").asText();
        UUID victimId = userRepository.findByUsername(victim).orElseThrow().getId();

        AdminActor admin = admin();
        changeStatus(admin.bearer(), victimId, "LOCKED", "nghi ngờ gian lận", 200);

        // 1. Không đăng nhập lại được (status != ACTIVE).
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"identifier":"%s","password":"%s"}
                                """.formatted(victim, PASSWORD)))
                .andExpect(status().isUnauthorized());

        // 2. Refresh token ĐANG GIỮ cũng vô hiệu -> không kéo dài được phiên hiện tại.
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"refreshToken":"%s"}
                                """.formatted(victimRefresh)))
                .andExpect(status().is4xxClientError());

        assertThat(userRepository.findById(victimId).orElseThrow().getStatus())
                .isEqualTo(UserStatus.LOCKED);
    }

    @Test
    void unlockingUserRestoresLogin() throws Exception {
        String victim = unique("unlockme");
        registerAndLogin(victim);
        UUID victimId = userRepository.findByUsername(victim).orElseThrow().getId();

        AdminActor admin = admin();
        changeStatus(admin.bearer(), victimId, "LOCKED", "kiểm tra tạm thời", 200);
        changeStatus(admin.bearer(), victimId, "ACTIVE", null, 200);

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"identifier":"%s","password":"%s"}
                                """.formatted(victim, PASSWORD)))
                .andExpect(status().isOk());
    }

    @Test
    void bannedIsFinalAndCannotBeReactivated() throws Exception {
        String victim = unique("banme");
        registerAndLogin(victim);
        UUID victimId = userRepository.findByUsername(victim).orElseThrow().getId();

        AdminActor admin = admin();
        changeStatus(admin.bearer(), victimId, "BANNED", "gian lận đặt cược chéo", 200);

        // Mở lại BANNED -> 400 INVALID_STATUS_TRANSITION, trạng thái KHÔNG đổi.
        mockMvc.perform(patch("/api/v1/admin/users/" + victimId + "/status")
                        .header("Authorization", admin.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status":"ACTIVE"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_STATUS_TRANSITION"));

        assertThat(userRepository.findById(victimId).orElseThrow().getStatus())
                .isEqualTo(UserStatus.BANNED);
    }

    @Test
    void lockingWithoutReasonIsRejected() throws Exception {
        String victim = unique("noreason");
        registerAndLogin(victim);
        UUID victimId = userRepository.findByUsername(victim).orElseThrow().getId();

        AdminActor admin = admin();
        mockMvc.perform(patch("/api/v1/admin/users/" + victimId + "/status")
                        .header("Authorization", admin.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status":"LOCKED"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ADMIN_REASON_REQUIRED"));

        // Trạng thái giữ nguyên ACTIVE.
        assertThat(userRepository.findById(victimId).orElseThrow().getStatus())
                .isEqualTo(UserStatus.ACTIVE);
    }

    @Test
    void adminCannotLockOrDemoteSelf() throws Exception {
        AdminActor admin = admin();

        mockMvc.perform(patch("/api/v1/admin/users/" + admin.id() + "/status")
                        .header("Authorization", admin.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status":"LOCKED","reason":"tự khóa"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CANNOT_MODIFY_SELF"));

        mockMvc.perform(patch("/api/v1/admin/users/" + admin.id() + "/role")
                        .header("Authorization", admin.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"role":"PLAYER"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CANNOT_MODIFY_SELF"));

        assertThat(userRepository.findById(admin.id()).orElseThrow().getRole())
                .isEqualTo(UserRole.ADMIN);
    }

    @Test
    void invalidStatusValueReturnsValidationError() throws Exception {
        String victim = unique("badstatus");
        registerAndLogin(victim);
        UUID victimId = userRepository.findByUsername(victim).orElseThrow().getId();

        AdminActor admin = admin();
        mockMvc.perform(patch("/api/v1/admin/users/" + victimId + "/status")
                        .header("Authorization", admin.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status":"KHONG_TON_TAI","reason":"thử giá trị sai"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void changingRoleRevokesRefreshTokenAndPersistsNewRole() throws Exception {
        String target = unique("promote");
        JsonNode targetTokens = registerAndLogin(target);
        String targetRefresh = targetTokens.get("refreshToken").asText();
        UUID targetId = userRepository.findByUsername(target).orElseThrow().getId();

        AdminActor admin = admin();
        mockMvc.perform(patch("/api/v1/admin/users/" + targetId + "/role")
                        .header("Authorization", admin.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"role":"ADMIN","reason":"bổ nhiệm quản trị"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("ADMIN"));

        // Token cũ mang claim ROLE_PLAYER -> phải bị thu hồi, buộc đăng nhập lại.
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"refreshToken":"%s"}
                                """.formatted(targetRefresh)))
                .andExpect(status().is4xxClientError());

        assertThat(userRepository.findById(targetId).orElseThrow().getRole())
                .isEqualTo(UserRole.ADMIN);
    }

    @Test
    void kycUpdateAndWithdrawalPasswordResetWork() throws Exception {
        String target = unique("kyc");
        JsonNode tokens = registerAndLogin(target);
        String targetBearer = "Bearer " + tokens.get("accessToken").asText();
        UUID targetId = userRepository.findByUsername(target).orElseThrow().getId();

        // User tự đặt mật khẩu rút tiền trước.
        mockMvc.perform(post("/api/v1/users/me/withdrawal-password")
                        .header("Authorization", targetBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"loginPassword":"%s","newWithdrawalPassword":"654321"}
                                """.formatted(PASSWORD)))
                .andExpect(status().isOk());

        AdminActor admin = admin();

        mockMvc.perform(patch("/api/v1/admin/users/" + targetId + "/kyc")
                        .header("Authorization", admin.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"kycLevel":"LEVEL_2","reason":"đã đối chiếu giấy tờ"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.kycLevel").value("LEVEL_2"));

        // Reset -> XÓA hash để user tự đặt lại (admin không đặt thay).
        mockMvc.perform(post("/api/v1/admin/users/" + targetId + "/withdrawal-password/reset")
                        .header("Authorization", admin.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hasWithdrawalPassword").value(false));

        assertThat(userRepository.findById(targetId).orElseThrow().getWithdrawalPasswordHash()).isNull();
    }

    @Test
    void searchFiltersByStatusAndKeyword() throws Exception {
        String target = unique("searchme");
        registerAndLogin(target);
        UUID targetId = userRepository.findByUsername(target).orElseThrow().getId();

        AdminActor admin = admin();
        changeStatus(admin.bearer(), targetId, "LOCKED", "để test filter", 200);

        // Filter keyword + status khớp đúng 1 user vừa khóa.
        MvcResult result = mockMvc.perform(get("/api/v1/admin/users")
                        .header("Authorization", admin.bearer())
                        .param("keyword", target)
                        .param("status", "LOCKED"))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(body.get("totalElements").asLong()).isEqualTo(1);

        JsonNode row = body.get("content").get(0);
        assertThat(row.get("username").asText()).isEqualTo(target);

        // Danh sách phải kèm số dư: bảng quản trị hiện cột này, không phải chỉ ở trang chi tiết.
        // Người vừa đăng ký chưa có giao dịch nào nên số dư là 0, và backend trả "0.00" chứ
        // không null — phía hiển thị không cần phân biệt "chưa có ví" với "số dư bằng không".
        assertThat(row.hasNonNull("balance")).isTrue();
        assertThat(new BigDecimal(row.get("balance").asText())).isEqualByComparingTo("0");
        assertThat(row.get("currency").asText()).isNotBlank();

        // Tiền PHẢI là chuỗi JSON, không phải số: Jackson tuần tự hoá BigDecimal thành số và
        // JavaScript sẽ làm tròn sai ở các số lẻ khi đọc lại.
        assertThat(row.get("balance").isTextual())
                .as("balance phải là chuỗi JSON để không mất chính xác ở client")
                .isTrue();

        // Cùng keyword nhưng status ACTIVE -> không khớp ai.
        MvcResult empty = mockMvc.perform(get("/api/v1/admin/users")
                        .header("Authorization", admin.bearer())
                        .param("keyword", target)
                        .param("status", "ACTIVE"))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(objectMapper.readTree(empty.getResponse().getContentAsString())
                .get("totalElements").asLong()).isZero();
    }

    @Test
    void userDetailReportsOwnFinancialsNotGlobalTotals() throws Exception {
        // User A nạp 100, user B nạp 500. Chi tiết của A phải hiện 100 (KHÔNG phải 600).
        JsonNode aTokens = registerAndLogin(unique("detaila"));
        String aBearer = "Bearer " + aTokens.get("accessToken").asText();
        deposit(aBearer, "100");

        String userB = unique("detailb");
        JsonNode bTokens = registerAndLogin(userB);
        deposit("Bearer " + bTokens.get("accessToken").asText(), "500");

        UUID aId = UUID.fromString(objectMapper.readTree(
                mockMvc.perform(get("/api/v1/users/me").header("Authorization", aBearer))
                        .andExpect(status().isOk())
                        .andReturn().getResponse().getContentAsString())
                .get("id").asText());

        AdminActor admin = admin();
        MvcResult result = mockMvc.perform(get("/api/v1/admin/users/" + aId)
                        .header("Authorization", admin.bearer()))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode detail = objectMapper.readTree(result.getResponse().getContentAsString());

        assertThat(new java.math.BigDecimal(detail.get("walletBalance").asText()))
                .isEqualByComparingTo("100");
        assertThat(new java.math.BigDecimal(detail.get("totalDeposited").asText()))
                .as("tổng nạp của CHÍNH user A, không phải tổng toàn sàn")
                .isEqualByComparingTo("100");
        assertThat(new java.math.BigDecimal(detail.get("totalWithdrawn").asText()))
                .isEqualByComparingTo("0");
    }

    @Test
    void deleteUserCleanAccountHardDeletes() throws Exception {
        String target = unique("clean");
        registerAndLogin(target);
        UUID targetId = userRepository.findByUsername(target).orElseThrow().getId();

        AdminActor admin = admin();

        // 1. Gửi sai PIN -> 400 (bị chặn)
        mockMvc.perform(delete("/api/v1/admin/users/" + targetId)
                        .header("Authorization", admin.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"confirmPin":"999999"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        // 2. Gửi đúng PIN -> 200 HARD_DELETE (xóa khỏi DB vì không có cược/nạp)
        mockMvc.perform(delete("/api/v1/admin/users/" + targetId)
                        .header("Authorization", admin.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"confirmPin":"171204"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.method").value("HARD_DELETE"));

        assertThat(userRepository.findById(targetId)).isEmpty();
    }

    @Test
    void deleteUserWithFinancialTrailSoftDeletes() throws Exception {
        String target = unique("hasfunds");
        JsonNode victimTokens = registerAndLogin(target);
        String targetBearer = "Bearer " + victimTokens.get("accessToken").asText();
        String victimRefresh = victimTokens.get("refreshToken").asText();
        UUID targetId = userRepository.findByUsername(target).orElseThrow().getId();

        // Tạo dấu vết tài chính: nạp tiền vào ví
        deposit(targetBearer, "200");

        AdminActor admin = admin();

        // 1. Đúng PIN -> 200 SOFT_DELETE (chuyển status thành CLOSED do có giao dịch)
        mockMvc.perform(delete("/api/v1/admin/users/" + targetId)
                        .header("Authorization", admin.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"confirmPin":"171204"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.method").value("SOFT_DELETE"));

        // 2. Trạng thái đổi thành CLOSED
        User victim = userRepository.findById(targetId).orElseThrow();
        assertThat(victim.getStatus()).isEqualTo(UserStatus.CLOSED);

        // 3. Đá phiên ngay: refresh token cũ vô hiệu hóa
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"refreshToken":"%s"}
                                """.formatted(victimRefresh)))
                .andExpect(status().is4xxClientError());

        // 4. Không đăng nhập lại được
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"identifier":"%s","password":"%s"}
                                """.formatted(target, PASSWORD)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void deleteUserWrongPinValidation() throws Exception {
        AdminActor admin = admin();
        mockMvc.perform(delete("/api/v1/admin/users/" + UUID.randomUUID())
                        .header("Authorization", admin.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"confirmPin":"  "}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
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
}
