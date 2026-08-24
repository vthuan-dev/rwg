package com.rwg.identity;

import com.rwg.identity.domain.User;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Đăng nhập khu quản trị (POST /api/v1/admin/auth/login).
 *
 * Điểm cần bảo vệ: người chơi KHÔNG vào được cửa backoffice, dù họ biết đúng mật
 * khẩu của chính mình. Trước khi có endpoint này, khu quản trị không có đường đăng
 * nhập nào nên không thể truy cập; sau khi mở đường, phải chắc rằng nó chỉ mở cho
 * nhân sự.
 *
 * Kiểm cả hai chiều: nhân sự vào được VÀ người chơi bị chặn. Chỉ kiểm chiều chặn thì
 * một cấu hình quá tay (chặn hết) vẫn lọt test.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminAuthApiTest {

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

    /** Đăng ký một tài khoản PLAYER mới qua API công khai. */
    private String register(String prefix) throws Exception {
        String username = unique(prefix);
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"%s","password":"%s"}
                                """.formatted(username, PASSWORD)))
                .andExpect(status().isCreated());
        return username;
    }

    /** Đăng ký rồi nâng lên vai trò nhân sự cho trước. */
    private String staff(UserRole role) throws Exception {
        String username = register("staff");
        User user = userRepository.findByUsername(username).orElseThrow();
        user.setRole(role);
        userRepository.save(user);
        return username;
    }

    private MvcResult backofficeLogin(String identifier, String password) throws Exception {
        return mockMvc.perform(post("/api/v1/admin/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"identifier":"%s","password":"%s"}
                                """.formatted(identifier, password)))
                .andReturn();
    }

    @Test
    @DisplayName("ADMIN đăng nhập backoffice thành công và nhận access token")
    void adminCanLoginToBackoffice() throws Exception {
        String username = staff(UserRole.ADMIN);

        MvcResult result = backofficeLogin(username, PASSWORD);

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(body.get("accessToken").asText()).isNotBlank();
        assertThat(body.get("refreshToken").asText()).isNotBlank();
        assertThat(body.get("tokenType").asText()).isEqualTo("Bearer");
    }

    @Test
    @DisplayName("FINANCE, SUPPORT và RISK cũng đăng nhập được backoffice")
    void allStaffRolesCanLoginToBackoffice() throws Exception {
        for (UserRole role : new UserRole[]{UserRole.FINANCE, UserRole.SUPPORT, UserRole.RISK}) {
            String username = staff(role);

            MvcResult result = backofficeLogin(username, PASSWORD);

            assertThat(result.getResponse().getStatus())
                    .as("vai trò %s phải đăng nhập được khu quản trị", role)
                    .isEqualTo(200);
        }
    }

    @Test
    @DisplayName("PLAYER nhập ĐÚNG mật khẩu vẫn bị chặn khỏi backoffice (403)")
    void playerIsForbiddenFromBackoffice() throws Exception {
        String username = register("player");

        MvcResult result = backofficeLogin(username, PASSWORD);

        assertThat(result.getResponse().getStatus()).isEqualTo(403);
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(body.get("code").asText()).isEqualTo("FORBIDDEN");
        // Không được lộ token nào trong response bị từ chối.
        assertThat(body.has("accessToken")).isFalse();
    }

    @Test
    @DisplayName("PLAYER bị chặn backoffice nhưng vẫn đăng nhập bình thường ở cửa người chơi")
    void playerBlockedFromBackofficeStillLogsInAsPlayer() throws Exception {
        String username = register("player");

        assertThat(backofficeLogin(username, PASSWORD).getResponse().getStatus()).isEqualTo(403);

        // Việc bị chặn khỏi khu quản trị KHÔNG được làm hỏng đường đăng nhập người
        // chơi — nếu rate limiter tính lần bị chặn là lần sai mật khẩu thì người chơi
        // sẽ bị khóa tài khoản oan.
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"identifier":"%s","password":"%s"}
                                """.formatted(username, PASSWORD)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Sai mật khẩu ở cửa backoffice trả 401, không phải 403")
    void wrongPasswordReturnsUnauthorized() throws Exception {
        String username = staff(UserRole.ADMIN);

        MvcResult result = backofficeLogin(username, "SaiMatKhau@99999");

        // PHẢI là 401 giống hệt trường hợp tài khoản không tồn tại. Nếu tài khoản nhân
        // sự sai mật khẩu trả mã khác với tài khoản người chơi sai mật khẩu thì kẻ tấn
        // công chỉ cần so mã lỗi là lọc ra được danh sách nhân sự cần nhắm vào.
        assertThat(result.getResponse().getStatus()).isEqualTo(401);
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(body.get("code").asText()).isEqualTo("INVALID_CREDENTIALS");
    }

    @Test
    @DisplayName("Tài khoản không tồn tại trả 401 giống nhân sự sai mật khẩu")
    void unknownAccountReturnsUnauthorized() throws Exception {
        MvcResult result = backofficeLogin(unique("khongtontai"), PASSWORD);

        assertThat(result.getResponse().getStatus()).isEqualTo(401);
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(body.get("code").asText()).isEqualTo("INVALID_CREDENTIALS");
    }
}
