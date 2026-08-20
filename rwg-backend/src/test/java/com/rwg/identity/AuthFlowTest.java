package com.rwg.identity;

import com.rwg.identity.domain.User;
import com.rwg.identity.domain.UserRole;
import com.rwg.identity.repository.AuditLogRepository;
import com.rwg.identity.repository.UserRepository;
import com.rwg.identity.service.AuditTrailService;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration test toàn bộ luồng Auth trên H2 (MODE=MySQL) — KHÔNG cần Docker.
 * Chạy mặc định với `mvn verify` (profile "test", Redis tắt -> fallback in-memory).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class AuthFlowTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    AuditLogRepository auditLogRepository;

    @Autowired
    UserRepository userRepository;

    private static final String PASSWORD = "MatKhau@12345";

    private String unique(String prefix) {
        return prefix + UUID.randomUUID().toString().substring(0, 8);
    }

    private String registerJson(String username, String email) {
        return """
                {"username":"%s","email":"%s","password":"%s"}
                """.formatted(username, email, PASSWORD);
    }

    private String loginJson(String identifier, String password) {
        return """
                {"identifier":"%s","password":"%s"}
                """.formatted(identifier, password);
    }

    private String loginJson(String identifier, String password, String captchaToken) {
        return """
                {"identifier":"%s","password":"%s","captchaToken":"%s"}
                """.formatted(identifier, password, captchaToken);
    }

    private MvcResult register(String username) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerJson(username, username + "@example.com")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.username").value(username))
                .andExpect(jsonPath("$.role").value("PLAYER"))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.hasWithdrawalPassword").value(false))
                .andReturn();
    }

    private JsonNode login(String identifier, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson(identifier, password)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    @Test
    @Order(1)
    void healthEndpointReturns200() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    @Order(2)
    void registerAndLoginSuccess() throws Exception {
        String username = unique("player");
        register(username);
        login(username, PASSWORD);
    }

    @Test
    @Order(3)
    void duplicateUsernameOrEmailReturnsGeneric409() throws Exception {
        String username = unique("dupuser");
        register(username);
        // Trùng username -> 409 CHUNG (không tiết lộ trường nào trong message).
        MvcResult dupUsername = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerJson(username, unique("dupmail") + "@example.com")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"))
                .andExpect(jsonPath("$.traceId").isNotEmpty())
                .andReturn();
        assertThat(objectMapper.readTree(dupUsername.getResponse().getContentAsString())
                .get("message").asText()).doesNotContain("username").doesNotContain("email");

        // Trùng email (username mới) -> cũng 409 với message CHUNG như trên.
        MvcResult dupEmail = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerJson(unique("dupuser2"), username + "@example.com")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"))
                .andReturn();
        assertThat(objectMapper.readTree(dupEmail.getResponse().getContentAsString())
                .get("message").asText()).doesNotContain("username").doesNotContain("email");
    }

    @Test
    @Order(4)
    void registerMissingFieldsReturns400() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"ab","email":"not-an-email","password":"123"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.details").isNotEmpty());
    }

    @Test
    @Order(5)
    void loginWrongPasswordReturns401() throws Exception {
        String username = unique("wrongpw");
        register(username);
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson(username, "SaiMatKhau@123")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
    }

    @Test
    @Order(6)
    void meRequiresJwtAndRejectsBadToken() throws Exception {
        String username = unique("meuser");
        register(username);
        JsonNode tokens = login(username, PASSWORD);

        mockMvc.perform(get("/api/v1/users/me")
                        .header("Authorization", "Bearer " + tokens.get("accessToken").asText()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value(username));

        // Không có token -> 401
        mockMvc.perform(get("/api/v1/users/me"))
                .andExpect(status().isUnauthorized());

        // Token rác -> 401
        mockMvc.perform(get("/api/v1/users/me")
                        .header("Authorization", "Bearer invalid.token.value"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @Order(7)
    void refreshRotationReuseOldTokenRevokesFamily() throws Exception {
        String username = unique("refresh");
        register(username);
        JsonNode first = login(username, PASSWORD);
        String oldRefresh = first.get("refreshToken").asText();

        MvcResult result = mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"refreshToken":"%s"}
                                """.formatted(oldRefresh)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty())
                .andReturn();
        JsonNode second = objectMapper.readTree(result.getResponse().getContentAsString());
        String newRefresh = second.get("refreshToken").asText();
        assertThat(newRefresh).isNotEqualTo(oldRefresh);

        // REUSE: gửi lại token cũ ĐÃ rotate -> 401, đồng thời THU HỒI TOÀN BỘ family.
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"refreshToken":"%s"}
                                """.formatted(oldRefresh)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("REFRESH_TOKEN_INVALID"));

        // Token MỚI (cùng family) cũng không còn dùng được — buộc đăng nhập lại.
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"refreshToken":"%s"}
                                """.formatted(newRefresh)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("REFRESH_TOKEN_INVALID"));
    }

    @Test
    @Order(8)
    void logoutRevokesRefreshToken() throws Exception {
        String username = unique("logout");
        register(username);
        JsonNode tokens = login(username, PASSWORD);
        String refresh = tokens.get("refreshToken").asText();

        mockMvc.perform(post("/api/v1/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"refreshToken":"%s"}
                                """.formatted(refresh)))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"refreshToken":"%s"}
                                """.formatted(refresh)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @Order(9)
    void captchaEnforcedFromFiveFailuresAndLockAfterTen() throws Exception {
        String username = unique("ratelimit");
        register(username);

        // 5 lần sai (chưa cần captcha) -> 401
        for (int i = 1; i <= 5; i++) {
            mockMvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(loginJson(username, "SaiMatKhau@123")))
                    .andExpect(status().isUnauthorized());
        }

        // ENFORCE phía server: KỂ CẢ ĐÚNG MẬT KHẨU mà thiếu captchaToken -> 429.
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson(username, PASSWORD)))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("CAPTCHA_REQUIRED"))
                .andExpect(jsonPath("$.details.captchaRequired").value(true));

        // Có captchaToken hợp lệ (dev stub: khác rỗng) -> được thử tiếp; sai vẫn 401.
        // Thêm 5 lần sai nữa (tổng 10) -> chạm ngưỡng khóa.
        for (int i = 6; i <= 10; i++) {
            mockMvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(loginJson(username, "SaiMatKhau@123", "dev-captcha-token")))
                    .andExpect(status().isUnauthorized());
        }

        // Lần tiếp theo (kể cả đúng mật khẩu + captcha) -> khóa 423.
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson(username, PASSWORD, "dev-captcha-token")))
                .andExpect(status().isLocked())
                .andExpect(jsonPath("$.code").value("ACCOUNT_LOCKED"))
                .andExpect(jsonPath("$.details.retryAfterSeconds").isNumber());
    }

    @Test
    @Order(10)
    void setWithdrawalPasswordRequiresLoginPasswordConfirmation() throws Exception {
        String username = unique("withdraw");
        register(username);
        JsonNode tokens = login(username, PASSWORD);
        String bearer = "Bearer " + tokens.get("accessToken").asText();

        // Sai mật khẩu đăng nhập -> 401
        mockMvc.perform(post("/api/v1/users/me/withdrawal-password")
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"loginPassword":"SaiMatKhau@123","newWithdrawalPassword":"654321"}
                                """))
                .andExpect(status().isUnauthorized());

        // Đúng -> đặt thành công
        mockMvc.perform(post("/api/v1/users/me/withdrawal-password")
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"loginPassword":"%s","newWithdrawalPassword":"654321"}
                                """.formatted(PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hasWithdrawalPassword").value(true));
    }

    @Test
    @Order(11)
    void auditLogRecordsEvents() {
        assertThat(auditLogRepository.countByAction(AuditTrailService.USER_REGISTERED)).isPositive();
        assertThat(auditLogRepository.countByAction(AuditTrailService.LOGIN_SUCCESS)).isPositive();
        assertThat(auditLogRepository.countByAction(AuditTrailService.LOGIN_FAILED)).isPositive();
        assertThat(auditLogRepository.countByAction(AuditTrailService.REFRESH_TOKEN_ROTATED)).isPositive();
        assertThat(auditLogRepository.countByAction(AuditTrailService.WITHDRAWAL_PASSWORD_SET)).isPositive();
    }

    @Test
    @Order(12)
    void adminEndpoint401WhenAnonymousAnd403ForPlayer() throws Exception {
        // Chưa đăng nhập -> 401
        mockMvc.perform(get("/api/v1/admin/users"))
                .andExpect(status().isUnauthorized());

        // PLAYER -> 403
        String username = unique("player");
        register(username);
        JsonNode tokens = login(username, PASSWORD);
        mockMvc.perform(get("/api/v1/admin/users")
                        .header("Authorization", "Bearer " + tokens.get("accessToken").asText()))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/admin/health")
                        .header("Authorization", "Bearer " + tokens.get("accessToken").asText()))
                .andExpect(status().isForbidden());
    }

    @Test
    @Order(13)
    void adminEndpoint200ForAdminRole() throws Exception {
        String username = unique("admin");
        register(username);
        // Phong role ADMIN trực tiếp trong DB (chưa có API quản trị user ở MVP).
        User user = userRepository.findByUsername(username).orElseThrow();
        user.setRole(UserRole.ADMIN);
        userRepository.save(user);

        JsonNode tokens = login(username, PASSWORD);
        String bearer = "Bearer " + tokens.get("accessToken").asText();

        mockMvc.perform(get("/api/v1/admin/health").header("Authorization", bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OK"));

        mockMvc.perform(get("/api/v1/admin/users").header("Authorization", bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.totalElements").isNumber());
    }

    @Test
    @Order(14)
    void registerPasswordOver72Utf8BytesReturns400() throws Exception {
        // 40 ký tự 'ấ' = 120 byte UTF-8 (>72) nhưng chỉ 40 ký tự (vẫn qua @Size(max=72))
        // -> service phải từ chối theo BYTE, không đếm ký tự.
        String longPassword = "ấ".repeat(40);
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"%s","email":"%s@example.com","password":"%s"}
                                """.formatted(unique("longpw"), unique("longpw"), longPassword)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    @Order(15)
    void loginUnknownUserStillReturnsGeneric401() throws Exception {
        // Chống dò user: user không tồn tại vẫn trả INVALID_CREDENTIALS như sai mật khẩu
        // (BCrypt vẫn chạy với hash dummy để cân bằng thời gian).
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson("user_khong_ton_tai_xyz", PASSWORD)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
    }
}
