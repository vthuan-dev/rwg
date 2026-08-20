package com.rwg.identity;

import com.rwg.config.RwgLocaleResolver;
import com.rwg.identity.domain.User;
import com.rwg.identity.repository.UserRepository;
import com.rwg.identity.service.UserLocaleService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Test i18n end-to-end trên H2 (chặng 2 - Phase a):
 * - Message validation/service đổi theo Accept-Language (en/vi/zh/ja).
 * - PATCH /api/v1/users/me/locale: đổi + validate trong {en,vi,zh,ja}.
 * - RwgLocaleResolver fallback về locale lưu trong users khi Accept-Language
 *   không khớp locale hỗ trợ.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class I18nApiTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    UserRepository userRepository;

    @Autowired
    UserLocaleService userLocaleService;

    private static final String PASSWORD = "MatKhau@12345";

    private String unique(String prefix) {
        return prefix + UUID.randomUUID().toString().substring(0, 8);
    }

    private String registerAndLogin(String username) throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"%s","email":"%s@example.com","password":"%s"}
                                """.formatted(username, username, PASSWORD)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.locale").value("en")); // locale mặc định khi đăng ký
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"identifier":"%s","password":"%s"}
                                """.formatted(username, PASSWORD)))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode tokens = objectMapper.readTree(result.getResponse().getContentAsString());
        return tokens.get("accessToken").asText();
    }

    /** Body đăng ký với username "ab" -> trượt @Size(min=3) của username. */
    private static final String INVALID_REGISTER_BODY = """
            {"username":"ab","email":"i18n@example.com","password":"MatKhau@12345"}
            """;

    @Test
    void validationMessageFollowsAcceptLanguage() throws Exception {
        // en (mặc định)
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(INVALID_REGISTER_BODY))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.username").value("username must be 3-32 characters"));

        // vi
        mockMvc.perform(post("/api/v1/auth/register")
                        .locale(Locale.forLanguageTag("vi"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(INVALID_REGISTER_BODY))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.username").value("username phải từ 3-32 ký tự"))
                .andExpect(jsonPath("$.message").value("Dữ liệu không hợp lệ"));

        // zh
        mockMvc.perform(post("/api/v1/auth/register")
                        .locale(Locale.SIMPLIFIED_CHINESE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(INVALID_REGISTER_BODY))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.username").value("用户名必须为 3-32 个字符"));

        // ja
        mockMvc.perform(post("/api/v1/auth/register")
                        .locale(Locale.JAPANESE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(INVALID_REGISTER_BODY))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.username").value("ユーザー名は3〜32文字でなければなりません"));
    }

    @Test
    void serviceMessageFollowsAcceptLanguage() throws Exception {
        String username = unique("i18ndup");
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"%s","email":"%s@example.com","password":"%s"}
                                """.formatted(username, username, PASSWORD)))
                .andExpect(status().isCreated());

        String dupBody = """
                {"username":"%s","email":"%s@example.com","password":"%s"}
                """.formatted(username, unique("othermail"), PASSWORD);

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(dupBody))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Registration information already exists"));

        mockMvc.perform(post("/api/v1/auth/register")
                        .locale(Locale.forLanguageTag("vi"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(dupBody))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Thông tin đăng ký đã tồn tại"));

        mockMvc.perform(post("/api/v1/auth/register")
                        .locale(Locale.SIMPLIFIED_CHINESE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(dupBody))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("注册信息已存在"));

        mockMvc.perform(post("/api/v1/auth/register")
                        .locale(Locale.JAPANESE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(dupBody))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("登録情報は既に存在します"));
    }

    @Test
    void patchLocaleChangesLanguageAndValidatesValueSet() throws Exception {
        String bearer = "Bearer " + registerAndLogin(unique("i18nuser"));

        // Đổi sang vi -> UserResponse trả locale mới
        mockMvc.perform(patch("/api/v1/users/me/locale")
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"locale":"vi"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.locale").value("vi"));

        // GET /me phản ánh locale đã lưu
        mockMvc.perform(get("/api/v1/users/me").header("Authorization", bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.locale").value("vi"));

        // Locale ngoài tập hỗ trợ -> 400 VALIDATION_ERROR
        mockMvc.perform(patch("/api/v1/users/me/locale")
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"locale":"fr"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        // Thiếu locale -> 400
        mockMvc.perform(patch("/api/v1/users/me/locale")
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"locale":""}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        // Chưa đăng nhập -> 401
        mockMvc.perform(patch("/api/v1/users/me/locale")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"locale":"vi"}
                                """))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void resolverFallsBackToStoredUserLocaleWhenAcceptLanguageMismatch() {
        // Tạo user thẳng trong DB với locale đã lưu = zh (bypass API để kiểm soát locale).
        String username = unique("i18nresolver");
        User user = userRepository.save(new User(username, username + "@example.com", "hash-gia"));
        user.setLocale("zh");
        userRepository.save(user);

        RwgLocaleResolver resolver = new RwgLocaleResolver(userLocaleService);

        // Giả lập user đã xác thực: principal là Jwt với sub = userId.
        Jwt jwt = Jwt.withTokenValue("token-gia")
                .header("alg", "HS256")
                .subject(user.getId().toString())
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(300))
                .build();
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(jwt, null, List.of()));
        try {
            // Accept-Language = fr (không hỗ trợ) -> resolver KHÔNG match header,
            // phải fallback về locale user đã lưu trong DB/cache = zh.
            // (setPreferredLocales thay danh sách mặc định [en] của MockHttpServletRequest.)
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.setPreferredLocales(List.of(Locale.FRENCH));
            assertThat(resolver.resolveLocale(request)).isEqualTo(Locale.SIMPLIFIED_CHINESE);

            // Accept-Language hỗ trợ -> ưu tiên header dù locale lưu trong DB là zh.
            MockHttpServletRequest requestVi = new MockHttpServletRequest();
            requestVi.setPreferredLocales(List.of(Locale.forLanguageTag("vi")));
            assertThat(resolver.resolveLocale(requestVi)).isEqualTo(Locale.forLanguageTag("vi"));

            // Không xác thực + Accept-Language không hỗ trợ -> mặc định en.
            SecurityContextHolder.clearContext();
            MockHttpServletRequest requestAnon = new MockHttpServletRequest();
            requestAnon.setPreferredLocales(List.of(Locale.FRENCH));
            assertThat(resolver.resolveLocale(requestAnon)).isEqualTo(Locale.ENGLISH);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }
}
