package com.rwg.presence;

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
import tools.jackson.databind.ObjectMapper;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Kiểm tra điểm cuối trạng thái có mặt của khu quản trị.
 *
 * Trọng tâm là những bất biến mà một lỗi ở đó sẽ âm thầm gây hại:
 * - Phải yêu cầu đăng nhập: đây là thông tin về hành vi của người chơi, không được để lộ
 *   cho bất kỳ ai biết id của họ.
 * - Phải trả về ĐỦ mọi id được hỏi, kể cả người chưa có mốc nào — bỏ bớt sẽ buộc giao diện
 *   suy luận theo một quy ước ngầm.
 *
 * Bộ test đặt {@code rwg.redis.enabled: false}, nên {@code NoOpPresenceStore} được dùng và
 * mọi người hiện offline. Test KHẲNG ĐỊNH điều đó chứ không lảng tránh: nhờ vậy bộ test
 * không bao giờ phụ thuộc vào một Redis đang chạy, và nếu ai đó đổi bản hiện thực mặc định
 * thì test này sẽ nói ra.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PresenceApiTest {

    private static final String PASSWORD = "MatKhau@12345";
    private static final String WITHDRAWAL_PASSWORD = "123456";

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    UserRepository userRepository;

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

    /** Bearer của một nhân sự với vai trò cho trước. */
    private String staffBearer(UserRole role) throws Exception {
        String username = register("staff");
        User user = userRepository.findByUsername(username).orElseThrow();
        user.setRole(role);
        userRepository.save(user);

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
    @DisplayName("Chưa đăng nhập KHÔNG tra được ai đang online")
    void presenceRequiresAuthentication() throws Exception {
        // Không có lớp chặn này thì bất kỳ ai biết id của một người chơi cũng theo dõi được
        // họ có đang truy cập hay không.
        mockMvc.perform(get("/api/v1/admin/presence")
                        .param("ids", UUID.randomUUID().toString()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Nhân sự tra được, và kết quả có ĐỦ mọi id được hỏi")
    void staffGetsEntryForEveryRequestedId() throws Exception {
        String support = staffBearer(UserRole.SUPPORT);

        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();

        mockMvc.perform(get("/api/v1/admin/presence")
                        .header("Authorization", support)
                        .param("ids", first + "," + second))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].userId").value(first.toString()))
                .andExpect(jsonPath("$[1].userId").value(second.toString()))
                // Trong profile test Redis bị tắt nên NoOpPresenceStore trả rỗng: mọi người
                // offline. Khẳng định tường minh để bộ test không phụ thuộc Redis thật.
                .andExpect(jsonPath("$[0].online").value(false))
                .andExpect(jsonPath("$[0].lastSeenAt").doesNotExist());
    }

    @Test
    @DisplayName("Danh sách người dùng của khu quản trị mang theo trạng thái có mặt")
    void adminUserListCarriesPresenceFields() throws Exception {
        String admin = staffBearer(UserRole.ADMIN);
        register("player");

        // Hai trường này phải có mặt trong CHÍNH danh sách, không chỉ ở điểm cuối làm mới:
        // lần vẽ bảng đầu tiên xảy ra trước khi bộ đếm 30 giây kịp chạy, nên thiếu chúng là
        // cột hiện "chưa rõ" cho mọi người trong nửa phút đầu.
        mockMvc.perform(get("/api/v1/admin/users")
                        .header("Authorization", admin)
                        .param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].online").exists());
    }
}
