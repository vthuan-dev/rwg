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

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Kiểm chứng phần "khi nào khách đăng nhập lần gần nhất" của khu quản trị:
 *
 * <ul>
 *   <li>{@code lastLoginAt} có mặt trong danh sách người dùng, null trước lần đăng nhập
 *       đầu tiên và có giá trị sau đó — đây là thứ bảng quản trị vẽ ra cột.</li>
 *   <li>{@code GET /admin/users/{id}/login-history} dựng lại đúng chuỗi đăng nhập từ
 *       {@code audit_log}, phân biệt thành công với thất bại.</li>
 *   <li>Id không tồn tại trả 404 (không phải danh sách rỗng).</li>
 *   <li>Người chơi KHÔNG đọc được lịch sử của ai.</li>
 *   <li>{@code limit} được tôn trọng.</li>
 * </ul>
 *
 * KHÔNG có test nào ghi thêm dữ liệu đăng nhập bằng tay: toàn bộ dữ liệu ở đây sinh ra từ
 * việc gọi thật {@code /auth/login}. Nếu đường ghi audit của luồng đăng nhập bị gỡ thì các
 * test này phải đỏ — đó chính là điều cần bảo vệ.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminLoginHistoryTest {

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

    private void register(String username) throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"%s","password":"%s"}
                                """.formatted(username, PASSWORD)))
                .andExpect(status().isCreated());
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

    /** Đăng nhập với mật khẩu SAI. Mong đợi 401 — dùng để sinh dòng LOGIN_FAILED. */
    private void loginWithWrongPassword(String username) throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"identifier":"%s","password":"SaiHoanToan@999"}
                                """.formatted(username)))
                .andExpect(status().isUnauthorized());
    }

    /** Đăng ký rồi phong ADMIN trực tiếp trong DB, đăng nhập lại để token có ROLE_ADMIN. */
    private String adminBearer() throws Exception {
        String username = unique("adm");
        register(username);
        login(username);
        User user = userRepository.findByUsername(username).orElseThrow();
        user.setRole(UserRole.ADMIN);
        userRepository.save(user);
        return "Bearer " + login(username).get("accessToken").asText();
    }

    /** Một dòng trong danh sách người dùng của khu quản trị, tìm theo username. */
    private JsonNode findUserRow(String adminBearer, String username) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/admin/users")
                        .header("Authorization", adminBearer)
                        .param("keyword", username))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(body.get("totalElements").asLong())
                .as("phải tìm thấy đúng một tài khoản %s", username)
                .isEqualTo(1);
        return body.get("content").get(0);
    }

    private JsonNode loginHistory(String adminBearer, UUID userId, Integer limit) throws Exception {
        var request = get("/api/v1/admin/users/" + userId + "/login-history")
                .header("Authorization", adminBearer);
        if (limit != null) request = request.param("limit", limit.toString());

        MvcResult result = mockMvc.perform(request).andExpect(status().isOk()).andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    /**
     * Cột "đăng nhập gần nhất" trên bảng lấy dữ liệu từ đây, nên nó phải phân biệt được
     * "chưa từng đăng nhập" với "vừa đăng nhập xong". Trả về một mốc giả cho trường hợp đầu
     * sẽ khiến người vận hành tưởng tài khoản đang được dùng.
     */
    @Test
    void lastLoginAtIsNullBeforeFirstLoginAndSetAfter() throws Exception {
        String admin = adminBearer();

        String player = unique("neverlogin");
        register(player);

        JsonNode beforeLogin = findUserRow(admin, player);
        assertThat(beforeLogin.has("lastLoginAt"))
                .as("DTO danh sách PHẢI có trường lastLoginAt — bảng quản trị vẽ cột từ nó")
                .isTrue();
        assertThat(beforeLogin.get("lastLoginAt").isNull())
                .as("tài khoản chỉ mới đăng ký thì chưa có lần đăng nhập nào")
                .isTrue();

        login(player);

        JsonNode afterLogin = findUserRow(admin, player);
        assertThat(afterLogin.hasNonNull("lastLoginAt"))
                .as("đăng nhập rồi thì mốc thời gian phải có mặt")
                .isTrue();
        assertThat(afterLogin.get("lastLoginAt").asText()).isNotBlank();
    }

    /**
     * Lịch sử phải chứa CẢ lần thất bại, mới nhất trước.
     *
     * Chuỗi "sai, sai, rồi đúng" là dấu hiệu đáng chú ý nhất mà người vận hành cần thấy;
     * một lịch sử chỉ ghi lần thành công thì không phân biệt được nó với một lần đăng nhập
     * bình thường.
     */
    @Test
    void loginHistoryRecordsSuccessAndFailure() throws Exception {
        String admin = adminBearer();

        String player = unique("histseq");
        register(player);
        UUID playerId = userRepository.findByUsername(player).orElseThrow().getId();

        loginWithWrongPassword(player);
        login(player);

        JsonNode history = loginHistory(admin, playerId, null);
        assertThat(history.isArray()).isTrue();
        assertThat(history.size())
                .as("một lần sai + một lần đúng = hai dòng")
                .isEqualTo(2);

        // Mới nhất TRƯỚC: lần đăng nhập thành công vừa rồi phải nằm ở đầu.
        JsonNode newest = history.get(0);
        assertThat(newest.get("success").asBoolean()).isTrue();
        assertThat(newest.get("channel").asText()).isEqualTo("PLAYER");
        assertThat(newest.get("at").asText()).isNotBlank();

        JsonNode oldest = history.get(1);
        assertThat(oldest.get("success").asBoolean())
                .as("lần gõ sai mật khẩu phải được ghi là thất bại")
                .isFalse();
    }

    /**
     * Id không tồn tại phải trả 404.
     *
     * Trả mảng rỗng sẽ trộn lẫn "tài khoản này chưa từng đăng nhập" với "không có tài khoản
     * nào như vậy" — hai kết luận khác nhau đối với người đang điều tra một khiếu nại.
     */
    @Test
    void loginHistoryReturns404ForUnknownUser() throws Exception {
        mockMvc.perform(get("/api/v1/admin/users/" + UUID.randomUUID() + "/login-history")
                        .header("Authorization", adminBearer()))
                .andExpect(status().isNotFound());
    }

    /**
     * Người chơi KHÔNG đọc được lịch sử đăng nhập của bất kỳ ai, kể cả của chính mình qua
     * đường này: endpoint nằm dưới /api/v1/admin/** và phơi ra IP.
     */
    @Test
    void playerCannotReadLoginHistory() throws Exception {
        String player = unique("nosee");
        register(player);
        String bearer = "Bearer " + login(player).get("accessToken").asText();
        UUID playerId = userRepository.findByUsername(player).orElseThrow().getId();

        mockMvc.perform(get("/api/v1/admin/users/" + playerId + "/login-history")
                        .header("Authorization", bearer))
                .andExpect(status().isForbidden());
    }

    /** limit phải thật sự cắt số dòng, không chỉ là tham số bị bỏ qua. */
    @Test
    void loginHistoryHonoursLimit() throws Exception {
        String admin = adminBearer();

        String player = unique("histlimit");
        register(player);
        UUID playerId = userRepository.findByUsername(player).orElseThrow().getId();

        login(player);
        login(player);
        login(player);

        assertThat(loginHistory(admin, playerId, null).size())
                .as("ba lần đăng nhập thành công = ba dòng khi không giới hạn")
                .isEqualTo(3);
        assertThat(loginHistory(admin, playerId, 2).size())
                .as("limit=2 phải cắt còn hai dòng")
                .isEqualTo(2);
    }
}
