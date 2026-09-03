package com.rwg.identity;

import com.rwg.config.SecurityProperties;
import com.rwg.identity.domain.UserRole;
import com.rwg.identity.repository.UserRepository;
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

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Quy tắc "một tài khoản, một phiên": đăng nhập ở thiết bị mới thì thiết bị cũ mất quyền.
 *
 * Bộ test chạy với {@code rwg.redis.enabled: false} nên {@code InMemoryActiveSessionStore}
 * được dùng. Đó là bản hiện thực THẬT (không phải no-op), và cả việc ghi lẫn việc đọc đều
 * xảy ra trong cùng tiến trình test, nên hành vi nghiệp vụ kiểm được đầy đủ mà không cần
 * Redis đang chạy.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SingleSessionTest {

    private static final String PASSWORD = "MatKhau@12345";
    private static final String WITHDRAWAL_PASSWORD = "123456";

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    UserRepository userRepository;

    @Autowired
    SecurityProperties securityProperties;

    private String unique(String prefix) {
        return prefix + UUID.randomUUID().toString().substring(0, 8);
    }

    private String register(String prefix) throws Exception {
        String username = unique(prefix);
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"%s","password":"%s","withdrawalPassword":"%s"}
                                """.formatted(username, PASSWORD, WITHDRAWAL_PASSWORD)))
                .andExpect(status().isCreated());
        return username;
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

    /** Phần payload của JWT, giải mã base64url. */
    private JsonNode claimsOf(String accessToken) {
        String payload = accessToken.split("\\.")[1];
        byte[] decoded = java.util.Base64.getUrlDecoder().decode(payload);
        return objectMapper.readTree(new String(decoded, java.nio.charset.StandardCharsets.UTF_8));
    }

    @Test
    @DisplayName("Cấu hình test BẬT quy tắc một phiên — nếu tắt thì mọi test dưới đây vô nghĩa")
    void singleSessionIsActiveInTestProfile() {
        // Không có khẳng định này, một lần đổi application-test.yml thành false sẽ làm toàn bộ
        // các test dưới đây xanh vì lý do sai: chúng chỉ kiểm được rằng "không có gì bị chặn".
        assertThat(securityProperties.singleSessionActive()).isTrue();
    }

    @Test
    @DisplayName("Đăng nhập máy thứ hai làm token máy thứ nhất mất hiệu lực")
    void secondLoginInvalidatesFirstToken() throws Exception {
        String username = register("ss");

        String firstToken = login(username).get("accessToken").asText();

        // Máy đầu dùng được bình thường trước khi có máy thứ hai.
        mockMvc.perform(get("/api/v1/users/me")
                        .header("Authorization", "Bearer " + firstToken))
                .andExpect(status().isOk());

        String secondToken = login(username).get("accessToken").asText();

        // Máy đầu mất quyền.
        mockMvc.perform(get("/api/v1/users/me")
                        .header("Authorization", "Bearer " + firstToken))
                .andExpect(status().isUnauthorized());

        // MÁY MỚI PHẢI DÙNG ĐƯỢC NGAY. Đây là khẳng định quan trọng nhất của cả bộ test:
        // cách hiện thực bằng `SessionRevocationStore.revokeBefore` sẽ đỏ ở đúng dòng này, vì
        // mốc thu hồi kèm biên lệch đồng hồ 5 giây chặn luôn token vừa phát.
        mockMvc.perform(get("/api/v1/users/me")
                        .header("Authorization", "Bearer " + secondToken))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Refresh token của máy cũ không dùng được sau khi máy mới đăng nhập")
    void oldRefreshTokenIsRejected() throws Exception {
        String username = register("ss");

        String firstRefresh = login(username).get("refreshToken").asText();
        login(username);

        // Không có lớp này thì máy cũ chỉ cần gia hạn là có access token mới hợp lệ — quy tắc
        // một phiên chỉ trì hoãn được tối đa một vòng access token.
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"refreshToken":"%s"}
                                """.formatted(firstRefresh)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Gia hạn phiên HIỆN HÀNH vẫn hoạt động, và token mới giữ nguyên định danh phiên")
    void refreshOfCurrentSessionKeepsWorking() throws Exception {
        String username = register("ss");
        JsonNode tokens = login(username);

        String sessionBefore = claimsOf(tokens.get("accessToken").asText()).get("sid").asText();

        MvcResult result = mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"refreshToken":"%s"}
                                """.formatted(tokens.get("refreshToken").asText())))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode rotated = objectMapper.readTree(result.getResponse().getContentAsString());
        String rotatedAccess = rotated.get("accessToken").asText();

        // Định danh phiên phải KHÔNG đổi qua gia hạn: nó là familyId của chuỗi rotation. Nếu
        // đổi thì mỗi lần gia hạn lại tự đá chính mình ra.
        assertThat(claimsOf(rotatedAccess).get("sid").asText()).isEqualTo(sessionBefore);

        mockMvc.perform(get("/api/v1/users/me")
                        .header("Authorization", "Bearer " + rotatedAccess))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Token của KHÁCH mang claim phiên; token của NHÂN SỰ thì không")
    void onlyPlayerTokensCarrySessionClaim() throws Exception {
        String player = register("ss");
        assertThat(claimsOf(login(player).get("accessToken").asText()).has("sid")).isTrue();

        String staffName = register("staff");
        var staff = userRepository.findByUsername(staffName).orElseThrow();
        staff.setRole(UserRole.ADMIN);
        userRepository.save(staff);

        MvcResult result = mockMvc.perform(post("/api/v1/admin/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"identifier":"%s","password":"%s"}
                                """.formatted(staffName, PASSWORD)))
                .andExpect(status().isOk())
                .andReturn();

        String staffToken = objectMapper.readTree(result.getResponse().getContentAsString())
                .get("accessToken").asText();

        // Nhân sự mở backoffice trên nhiều máy là bình thường trong vận hành — ép một phiên ở
        // đó chỉ khiến họ đá nhau ra giữa lúc đang xử lý lệnh nạp/rút.
        assertThat(claimsOf(staffToken).has("sid")).isFalse();
    }

    @Test
    @DisplayName("Nhân sự đăng nhập hai lần thì CẢ HAI token đều còn dùng được")
    void staffCanHoldTwoSessions() throws Exception {
        String staffName = register("staff");
        var staff = userRepository.findByUsername(staffName).orElseThrow();
        staff.setRole(UserRole.ADMIN);
        userRepository.save(staff);

        String first = staffBearer(staffName);
        String second = staffBearer(staffName);

        mockMvc.perform(get("/api/v1/admin/users").header("Authorization", first).param("size", "1"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/admin/users").header("Authorization", second).param("size", "1"))
                .andExpect(status().isOk());
    }

    private String staffBearer(String username) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/admin/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"identifier":"%s","password":"%s"}
                                """.formatted(username, PASSWORD)))
                .andExpect(status().isOk())
                .andReturn();
        return "Bearer " + objectMapper.readTree(result.getResponse().getContentAsString())
                .get("accessToken").asText();
    }

    @Test
    @DisplayName("Phiên hỗ trợ khách quên mật khẩu KHÔNG đá người đang đăng nhập ra")
    void guestSupportSessionDoesNotEvict() throws Exception {
        String username = register("ss");
        String playerToken = login(username).get("accessToken").asText();

        // Điểm cuối này mở được CHỈ bằng tên đăng nhập. Nếu nó chốt phiên thì bất kỳ ai biết
        // tên đăng nhập của người khác đều đá được người đó khỏi ván đang chơi, lặp lại liên
        // tục — thành một cách chặn người chơi truy cập mà không cần mật khẩu.
        mockMvc.perform(post("/api/v1/auth/guest-support")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"%s"}
                                """.formatted(username)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/users/me")
                        .header("Authorization", "Bearer " + playerToken))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Hai tài khoản KHÁC NHAU đăng nhập không ảnh hưởng nhau")
    void differentAccountsAreIndependent() throws Exception {
        String first = register("ss");
        String firstToken = login(first).get("accessToken").asText();

        String second = register("ss");
        login(second);

        // Khoá phiên lưu theo userId. Nếu vô tình dùng một khoá dùng chung thì test này đỏ,
        // và trên production nó sẽ biểu hiện thành "cứ có ai đăng nhập là mọi người bị out".
        mockMvc.perform(get("/api/v1/users/me")
                        .header("Authorization", "Bearer " + firstToken))
                .andExpect(status().isOk());
    }
}
