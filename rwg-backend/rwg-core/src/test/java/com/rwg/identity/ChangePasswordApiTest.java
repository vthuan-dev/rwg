package com.rwg.identity;

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

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Test đổi mật khẩu đăng nhập (chặng 2 Phase b):
 * - đổi thành công -> refresh token cũ bị THU HỒI (reuse không được).
 * - sai mật khẩu cũ -> 401.
 * - đăng nhập lại bằng mật khẩu mới; mật khẩu cũ hết hiệu lực.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ChangePasswordApiTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    private static final String PASSWORD = "MatKhau@12345";
    private static final String NEW_PASSWORD = "MatKhauMoi@6789";

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
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"identifier":"%s","password":"%s"}
                                """.formatted(username, PASSWORD)))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private MvcResult loginExpect(String username, String password, int expectedStatus) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"identifier":"%s","password":"%s"}
                                """.formatted(username, password)))
                .andExpect(status().is(expectedStatus))
                .andReturn();
    }

    @Test
    void changePasswordSuccessRevokesOldRefreshTokens() throws Exception {
        String username = unique("chpw");
        JsonNode tokens = registerAndLogin(username);
        String bearer = "Bearer " + tokens.get("accessToken").asText();
        String oldRefresh = tokens.get("refreshToken").asText();

        // Đổi mật khẩu: xác nhận bằng mật khẩu cũ.
        mockMvc.perform(post("/api/v1/users/me/password")
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"oldPassword":"%s","newPassword":"%s"}
                                """.formatted(PASSWORD, NEW_PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value(username));

        // Refresh token CŨ không còn dùng được (đã thu hồi hàng loạt).
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"refreshToken":"%s"}
                                """.formatted(oldRefresh)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("REFRESH_TOKEN_INVALID"));

        // Mật khẩu cũ hết hiệu lực; mật khẩu mới đăng nhập được.
        loginExpect(username, PASSWORD, 401);
        loginExpect(username, NEW_PASSWORD, 200);
    }

    @Test
    void wrongOldPasswordReturns401() throws Exception {
        String username = unique("chpwwrong");
        JsonNode tokens = registerAndLogin(username);
        String bearer = "Bearer " + tokens.get("accessToken").asText();

        mockMvc.perform(post("/api/v1/users/me/password")
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"oldPassword":"SaiMatKhau@123","newPassword":"%s"}
                                """.formatted(NEW_PASSWORD)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));

        // Mật khẩu cũ vẫn dùng được (không bị đổi).
        loginExpect(username, PASSWORD, 200);
    }

    @Test
    void newPasswordOver72Utf8BytesReturns400() throws Exception {
        String username = unique("chpwbytes");
        JsonNode tokens = registerAndLogin(username);
        String bearer = "Bearer " + tokens.get("accessToken").asText();

        // 40 ký tự 'ấ' = 120 byte UTF-8 (>72) nhưng chỉ 40 ký tự (qua @Size(max=72)).
        String longPassword = "ấ".repeat(40);
        mockMvc.perform(post("/api/v1/users/me/password")
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"oldPassword":"%s","newPassword":"%s"}
                                """.formatted(PASSWORD, longPassword)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }
}
