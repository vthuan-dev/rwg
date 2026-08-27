package com.rwg.settings;

import com.rwg.identity.domain.User;
import com.rwg.identity.domain.UserRole;
import com.rwg.identity.repository.AuditLogRepository;
import com.rwg.identity.repository.UserRepository;
import com.rwg.identity.service.AuditTrailService;
import com.rwg.settings.domain.AppSetting;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Kiểm tra nội dung chữ cấu hình được từ khu quản trị.
 *
 * Trọng tâm là những bất biến mà một lỗi ở đó sẽ âm thầm gây hại:
 * - Đường ĐỌC phải công khai — nếu không, bong bóng chào rỗng với khách chưa đăng nhập.
 * - Đường SỬA chỉ ADMIN — nội dung này hiện trước mọi khách, cùng mức ảnh hưởng với banner.
 * - Mỗi lần sửa phải để lại vết audit kèm giá trị CŨ, vì đó là căn cứ khi có khiếu nại.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AppSettingApiTest {

    private static final String PASSWORD = "MatKhau@12345";
    private static final String WITHDRAWAL_PASSWORD = "123456";

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
    @DisplayName("Lời chào khung chat đọc được KHÔNG cần đăng nhập và đã có sẵn nội dung")
    void chatPromoTextIsPublicAndSeeded() throws Exception {
        // KHÔNG gửi header Authorization: khách mở khung chat trước khi đăng nhập vẫn
        // phải thấy lời chào. Bắt đăng nhập ở đây làm bong bóng đầu tiên rỗng.
        mockMvc.perform(get("/api/v1/settings/chat-promo-text"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.key").value(AppSetting.CHAT_PROMO_TEXT))
                // Migration đã seed nội dung, nên KHÔNG bao giờ rỗng. Rỗng nghĩa là
                // migration chưa chạy — một sự cố cần thấy ngay.
                .andExpect(jsonPath("$.value").isNotEmpty());
    }

    @Test
    @DisplayName("ADMIN sửa được lời chào; nội dung mới hiện ngay ở đường công khai")
    void adminCanUpdateAndPublicReflectsIt() throws Exception {
        String admin = staffBearer(UserRole.ADMIN);
        String newText = "Xin chao, day la loi chao moi " + UUID.randomUUID();

        mockMvc.perform(put("/api/v1/admin/settings/chat-promo-text")
                        .header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(java.util.Map.of("value", newText))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.value").value(newText))
                // Tên người sửa được chụp lại: không có nó thì không truy được ai đã đổi
                // nội dung mà khách đang đọc.
                .andExpect(jsonPath("$.updatedByUsername").isNotEmpty());

        // ĐƯỜNG CÔNG KHAI phải trả nội dung MỚI. Đây là điều kiện thực sự quan trọng:
        // lưu được mà khách vẫn thấy bản cũ thì tính năng vô nghĩa.
        mockMvc.perform(get("/api/v1/settings/chat-promo-text"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.value").value(newText));
    }

    @Test
    @DisplayName("Mỗi lần sửa để lại vết audit kèm giá trị cũ")
    void updateLeavesAuditTrailWithOldValue() throws Exception {
        String admin = staffBearer(UserRole.ADMIN);

        String before = objectMapper.readTree(
                        mockMvc.perform(get("/api/v1/settings/chat-promo-text"))
                                .andReturn().getResponse().getContentAsString())
                .get("value").asString();

        String newText = "Noi dung audit " + UUID.randomUUID();
        mockMvc.perform(put("/api/v1/admin/settings/chat-promo-text")
                        .header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(java.util.Map.of("value", newText))))
                .andExpect(status().isOk());

        var logs = auditLogRepository.findAll().stream()
                .filter(l -> AuditTrailService.ADMIN_SETTING_UPDATED.equals(l.getAction()))
                .toList();

        assertThat(logs).as("phải có vết audit cho việc sửa cấu hình").isNotEmpty();

        // GIÁ TRỊ CŨ phải có trong audit: khi nội dung sai gây khiếu nại, câu hỏi đầu
        // tiên luôn là "trước đó nó ghi gì".
        String details = logs.getLast().getDetails();
        assertThat(details).contains("oldValue").contains("newValue");
        assertThat(details).contains(newText.substring(0, 20));
        assertThat(before).isNotBlank();
    }

    @Test
    @DisplayName("SUPPORT KHÔNG sửa được lời chào (chỉ ADMIN)")
    void supportCannotUpdate() throws Exception {
        String support = staffBearer(UserRole.SUPPORT);

        // Nội dung này hiện trước MỌI khách — cùng mức ảnh hưởng với banner trang chủ,
        // nên cùng mức hạn chế. Để rơi vào matcher chung của /api/v1/admin/** thì cả
        // SUPPORT và RISK đều sửa được.
        mockMvc.perform(put("/api/v1/admin/settings/chat-promo-text")
                        .header("Authorization", support)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"value":"SUPPORT khong duoc sua"}
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Lời chào rỗng bị từ chối, không lưu thành nội dung trống")
    void blankValueIsRejected() throws Exception {
        String admin = staffBearer(UserRole.ADMIN);

        // Chuỗi chỉ có khoảng trắng cũng phải bị chặn: @NotBlank lo việc đó. Nếu chỉ
        // dùng @NotNull thì một ô soạn bị xoá sạch sẽ lưu được, và mọi khách mở chat
        // thấy một bong bóng trống.
        mockMvc.perform(put("/api/v1/admin/settings/chat-promo-text")
                        .header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"value":"   "}
                                """))
                .andExpect(status().isBadRequest());
    }
}
