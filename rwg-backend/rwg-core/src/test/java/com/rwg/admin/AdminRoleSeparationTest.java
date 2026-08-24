package com.rwg.admin;

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

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Ma trận phân quyền của khu quản trị sau khi TÁCH VAI TRÒ (chặng 5).
 *
 * Mục tiêu: nhân sự support (nhóm đông nhất, dễ bị nhắm vào nhất) KHÔNG chạm được
 * tiền, và FINANCE không tự nâng quyền được thành ADMIN.
 *
 * Mỗi test khẳng định CẢ HAI chiều: role bị chặn -> 403, VÀ role được phép -> thành
 * công. Chỉ kiểm chiều chặn thì một cấu hình quá tay (chặn hết) vẫn lọt test.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminRoleSeparationTest {

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

    /** Tạo nhân sự với vai trò cho trước; login LẠI để token mang claim role mới. */
    private String staffBearer(UserRole role) throws Exception {
        String username = unique("staff");
        registerAndLogin(username);
        User user = userRepository.findByUsername(username).orElseThrow();
        user.setRole(role);
        userRepository.save(user);
        return "Bearer " + login(username).get("accessToken").asText();
    }

    /** Player có ví $200 để làm đối tượng của các thao tác tiền. */
    private UUID fundedPlayerId() throws Exception {
        String username = unique("target");
        String bearer = "Bearer " + registerAndLogin(username).get("accessToken").asText();
        mockMvc.perform(post("/api/v1/wallet/deposits")
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"amount":"200"}
                                """))
                .andExpect(status().isCreated());
        return userRepository.findByUsername(username).orElseThrow().getId();
    }

    private BigDecimal balanceOf(UUID userId, String adminBearer) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/admin/users/" + userId + "/wallet")
                        .header("Authorization", adminBearer))
                .andExpect(status().isOk())
                .andReturn();
        return new BigDecimal(objectMapper.readTree(result.getResponse().getContentAsString())
                .get("balance").asText());
    }

    // ===== SUPPORT: xem được, KHÔNG chạm tiền =====

    @Test
    @DisplayName("SUPPORT KHÔNG điều chỉnh được ví (403) và số dư không đổi")
    void supportCannotAdjustWallet() throws Exception {
        String support = staffBearer(UserRole.SUPPORT);
        String admin = staffBearer(UserRole.ADMIN);
        UUID playerId = fundedPlayerId();

        mockMvc.perform(post("/api/v1/admin/users/" + playerId + "/wallet/adjust")
                        .header("Authorization", support)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"direction":"CREDIT","amount":"100","reason":"support thử cộng tiền"}
                                """))
                .andExpect(status().isForbidden());

        assertThat(balanceOf(playerId, admin)).isEqualByComparingTo("200");
    }

    @Test
    @DisplayName("SUPPORT KHÔNG duyệt/từ chối được lệnh rút (403)")
    void supportCannotDecideWithdrawals() throws Exception {
        String support = staffBearer(UserRole.SUPPORT);
        UUID anyOrder = UUID.randomUUID();

        // 403 (chặn ở tầng phân quyền) chứ KHÔNG phải 404 — nghĩa là request không hề
        // đi tới service, nên không có cơ hội tác động dữ liệu.
        //
        // Gửi kèm body HỢP LỆ: nếu để trống, 400 do thiếu lý do sẽ che mất kết quả cần kiểm và
        // test không còn chứng minh được điều gì về phân quyền.
        String decisionBody = "{\"note\":\"kiem tra phan quyen\"}";
        mockMvc.perform(post("/api/v1/admin/withdrawals/" + anyOrder + "/approve")
                        .header("Authorization", support)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(decisionBody))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/admin/withdrawals/" + anyOrder + "/reject")
                        .header("Authorization", support)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(decisionBody))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("SUPPORT VẪN làm được việc thường ngày: xem user, khóa user, xem ví")
    void supportCanStillDoDailyWork() throws Exception {
        String support = staffBearer(UserRole.SUPPORT);
        UUID playerId = fundedPlayerId();

        mockMvc.perform(get("/api/v1/admin/users").header("Authorization", support))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/admin/users/" + playerId).header("Authorization", support))
                .andExpect(status().isOk());
        // Xem ví/ledger là việc tra soát khiếu nại — được phép ĐỌC, chỉ không được GHI.
        mockMvc.perform(get("/api/v1/admin/users/" + playerId + "/wallet")
                        .header("Authorization", support))
                .andExpect(status().isOk());
        mockMvc.perform(patch("/api/v1/admin/users/" + playerId + "/status")
                        .header("Authorization", support)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status":"LOCKED","reason":"nghi ngờ gian lận, chờ xác minh"}
                                """))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("SUPPORT KHÔNG đổi được vai trò người khác (chặn leo thang đặc quyền)")
    void supportCannotChangeRoles() throws Exception {
        String support = staffBearer(UserRole.SUPPORT);
        UUID playerId = fundedPlayerId();

        mockMvc.perform(patch("/api/v1/admin/users/" + playerId + "/role")
                        .header("Authorization", support)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"role":"ADMIN","reason":"tự nâng đồng minh"}
                                """))
                .andExpect(status().isForbidden());
    }

    // ===== FINANCE: chạm tiền được, KHÔNG phân quyền =====

    @Test
    @DisplayName("FINANCE điều chỉnh được ví (dưới hạn mức)")
    void financeCanAdjustWallet() throws Exception {
        String finance = staffBearer(UserRole.FINANCE);
        String admin = staffBearer(UserRole.ADMIN);
        UUID playerId = fundedPlayerId();

        mockMvc.perform(post("/api/v1/admin/users/" + playerId + "/wallet/adjust")
                        .header("Authorization", finance)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"direction":"CREDIT","amount":"100","reason":"hoàn tiền sự cố"}
                                """))
                .andExpect(status().isOk());

        assertThat(balanceOf(playerId, admin)).isEqualByComparingTo("300");
    }

    @Test
    @DisplayName("FINANCE KHÔNG đổi được vai trò — nếu lọt thì việc tách vai trò vô nghĩa")
    void financeCannotEscalateItsOwnPrivileges() throws Exception {
        String finance = staffBearer(UserRole.FINANCE);
        UUID playerId = fundedPlayerId();

        mockMvc.perform(patch("/api/v1/admin/users/" + playerId + "/role")
                        .header("Authorization", finance)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"role":"ADMIN","reason":"tự nâng quyền"}
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("FINANCE KHÔNG đổi được % hoa hồng (ảnh hưởng tiền chi cho mọi đại lý)")
    void financeCannotChangeCommissionRates() throws Exception {
        String finance = staffBearer(UserRole.FINANCE);

        mockMvc.perform(patch("/api/v1/admin/affiliate/config")
                        .header("Authorization", finance)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"level1Rate":"0.9","level2Rate":"0.05"}
                                """))
                .andExpect(status().isForbidden());
    }

    // ===== RISK: chỉ đọc =====

    @Test
    @DisplayName("RISK đọc được báo cáo nhưng KHÔNG ghi được gì")
    void riskIsReadOnly() throws Exception {
        String risk = staffBearer(UserRole.RISK);
        UUID playerId = fundedPlayerId();

        mockMvc.perform(get("/api/v1/admin/dashboard/summary").header("Authorization", risk))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/admin/audit/logs").header("Authorization", risk))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/admin/users/" + playerId + "/wallet/adjust")
                        .header("Authorization", risk)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"direction":"CREDIT","amount":"10","reason":"risk thử ghi"}
                                """))
                .andExpect(status().isForbidden());
        mockMvc.perform(patch("/api/v1/admin/users/" + playerId + "/status")
                        .header("Authorization", risk)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status":"LOCKED","reason":"risk thử khóa"}
                                """))
                .andExpect(status().isForbidden());
    }

    // ===== PLAYER: không vào được khu admin =====

    @Test
    @DisplayName("PLAYER vẫn bị chặn toàn bộ khu quản trị")
    void playerStillBlockedEverywhere() throws Exception {
        String player = "Bearer " + registerAndLogin(unique("plainpl")).get("accessToken").asText();

        mockMvc.perform(get("/api/v1/admin/users").header("Authorization", player))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/admin/dashboard/summary").header("Authorization", player))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/admin/approvals").header("Authorization", player))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("ADMIN giữ nguyên toàn quyền (tài khoản admin hiện có không bị ảnh hưởng)")
    void adminKeepsFullAccess() throws Exception {
        String admin = staffBearer(UserRole.ADMIN);
        UUID playerId = fundedPlayerId();

        mockMvc.perform(post("/api/v1/admin/users/" + playerId + "/wallet/adjust")
                        .header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"direction":"CREDIT","amount":"10","reason":"admin toàn quyền"}
                                """))
                .andExpect(status().isOk());
        mockMvc.perform(patch("/api/v1/admin/users/" + playerId + "/role")
                        .header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"role":"SUPPORT","reason":"tuyển vào nhóm hỗ trợ"}
                                """))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/admin/approvals").header("Authorization", admin))
                .andExpect(status().isOk());
    }
}
