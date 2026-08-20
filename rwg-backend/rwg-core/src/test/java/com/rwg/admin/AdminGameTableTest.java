package com.rwg.admin;

import com.rwg.game.domain.GameTable;
import com.rwg.game.domain.GameTableStatus;
import com.rwg.game.repository.GameTableRepository;
import com.rwg.identity.domain.User;
import com.rwg.identity.domain.UserRole;
import com.rwg.identity.repository.AuditLogRepository;
import com.rwg.identity.repository.UserRepository;
import com.rwg.identity.service.AuditTrailService;
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
 * Quản trị bàn chơi (chặng 6): bật/tắt bàn và đổi hạn mức cược.
 *
 * Trước chặng này không có API nào cho game ở khu quản trị — bàn lỗi chỉ tắt được
 * bằng cách sửa DB tay hoặc khởi động lại app.
 *
 * Test tập trung vào HỆ QUẢ THẬT (bàn tắt thì không cược được nữa, hạn mức mới có
 * hiệu lực ngay) chứ không chỉ kiểm mã HTTP.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminGameTableTest {

    private static final String PASSWORD = "MatKhau@12345";

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    UserRepository userRepository;

    @Autowired
    GameTableRepository tableRepository;

    @Autowired
    AuditLogRepository auditLogRepository;

    private String unique(String prefix) {
        return prefix + UUID.randomUUID().toString().substring(0, 8);
    }

    private String register(String username) throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"%s","email":"%s@example.com","password":"%s"}
                                """.formatted(username, username, PASSWORD)))
                .andExpect(status().isCreated());
        return login(username);
    }

    private String login(String username) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"identifier":"%s","password":"%s"}
                                """.formatted(username, PASSWORD)))
                .andExpect(status().isOk())
                .andReturn();
        return "Bearer " + objectMapper.readTree(result.getResponse().getContentAsString())
                .get("accessToken").asText();
    }

    private String staffBearer(UserRole role) throws Exception {
        String username = unique("gadm");
        register(username);
        User user = userRepository.findByUsername(username).orElseThrow();
        user.setRole(role);
        userRepository.save(user);
        // Login LẠI: token cũ phát hành trước khi có vai trò mới.
        return login(username);
    }

    /** Bàn dùng riêng cho từng test để không nhiễm bàn seed của test khác. */
    private GameTable newTable() {
        GameTable table = new GameTable(UUID.randomUUID(), "ROULETTE",
                "{\"en\":\"Test Table\",\"vi\":\"Ban thu\",\"zh\":\"Test\",\"ja\":\"Test\"}",
                new BigDecimal("1"), new BigDecimal("1000"));
        return tableRepository.saveAndFlush(table);
    }

    private JsonNode patchJson(String path, String bearer, String body, int expectedStatus)
            throws Exception {
        MvcResult result = mockMvc.perform(patch(path)
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().is(expectedStatus))
                .andReturn();
        String content = result.getResponse().getContentAsString();
        return content.isBlank() ? null : objectMapper.readTree(content);
    }

    // ===== Bật/tắt bàn =====

    @Test
    @DisplayName("Tắt bàn -> trạng thái DISABLED trong DB")
    void disableTablePersists() throws Exception {
        String admin = staffBearer(UserRole.ADMIN);
        GameTable table = newTable();

        JsonNode body = patchJson("/api/v1/admin/games/tables/" + table.getId() + "/status",
                admin, """
                        {"status":"DISABLED","reason":"ban loi, tam dong de kiem tra"}
                        """, 200);

        assertThat(body.get("status").asText()).isEqualTo("DISABLED");
        assertThat(tableRepository.findById(table.getId()).orElseThrow().getStatus())
                .isEqualTo(GameTableStatus.DISABLED);
    }

    @Test
    @DisplayName("Bàn đã tắt thì người chơi KHÔNG đặt cược được nữa")
    void disabledTableRejectsBets() throws Exception {
        String admin = staffBearer(UserRole.ADMIN);
        String player = register(unique("gpl"));
        GameTable table = newTable();

        patchJson("/api/v1/admin/games/tables/" + table.getId() + "/status", admin, """
                {"status":"DISABLED","reason":"dong ban"}
                """, 200);

        // Đây là hệ quả thật của việc tắt bàn — không chỉ là một cột trong DB.
        mockMvc.perform(post("/api/v1/games/tables/" + table.getId() + "/bets")
                        .header("Authorization", player)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"betType":"RED","selection":"RED","stake":"10","seq":1}
                                """))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Bàn đã tắt KHÔNG còn trong danh sách người chơi, nhưng VẪN có ở danh sách admin")
    void disabledTableHiddenFromPlayersButVisibleToAdmin() throws Exception {
        String admin = staffBearer(UserRole.ADMIN);
        String player = register(unique("gvis"));
        GameTable table = newTable();

        patchJson("/api/v1/admin/games/tables/" + table.getId() + "/status", admin, """
                {"status":"DISABLED","reason":"dong ban"}
                """, 200);

        MvcResult playerView = mockMvc.perform(get("/api/v1/games/tables")
                        .header("Authorization", player))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(playerView.getResponse().getContentAsString())
                .doesNotContain(table.getId().toString());

        // Admin PHẢI thấy bàn đã tắt, nếu không thì tắt rồi sẽ không tìm lại để bật.
        MvcResult adminView = mockMvc.perform(get("/api/v1/admin/games/tables")
                        .header("Authorization", admin))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(adminView.getResponse().getContentAsString())
                .contains(table.getId().toString());
    }

    @Test
    @DisplayName("Bật lại bàn -> trở về ACTIVE")
    void reEnableTableRestoresIt() throws Exception {
        String admin = staffBearer(UserRole.ADMIN);
        GameTable table = newTable();
        String path = "/api/v1/admin/games/tables/" + table.getId() + "/status";

        patchJson(path, admin, """
                {"status":"DISABLED","reason":"dong tam"}
                """, 200);
        patchJson(path, admin, """
                {"status":"ACTIVE","reason":"da sua xong, mo lai"}
                """, 200);

        assertThat(tableRepository.findById(table.getId()).orElseThrow().getStatus())
                .isEqualTo(GameTableStatus.ACTIVE);
    }

    @Test
    @DisplayName("Đặt lại đúng trạng thái đang có -> 400 (tránh ghi audit vô nghĩa)")
    void sameStatusRejected() throws Exception {
        String admin = staffBearer(UserRole.ADMIN);
        GameTable table = newTable();

        JsonNode error = patchJson("/api/v1/admin/games/tables/" + table.getId() + "/status",
                admin, """
                        {"status":"ACTIVE","reason":"ban da ACTIVE roi"}
                        """, 400);

        assertThat(error.get("code").asText()).isEqualTo("INVALID_STATUS_TRANSITION");
    }

    @Test
    @DisplayName("Thiếu lý do -> 400")
    void reasonIsRequired() throws Exception {
        String admin = staffBearer(UserRole.ADMIN);
        GameTable table = newTable();

        patchJson("/api/v1/admin/games/tables/" + table.getId() + "/status", admin, """
                {"status":"DISABLED"}
                """, 400);
    }

    @Test
    @DisplayName("Bàn không tồn tại -> 404")
    void unknownTableReturnsNotFound() throws Exception {
        patchJson("/api/v1/admin/games/tables/" + UUID.randomUUID() + "/status",
                staffBearer(UserRole.ADMIN), """
                        {"status":"DISABLED","reason":"ban khong ton tai"}
                        """, 404);
    }

    @Test
    @DisplayName("Thao tác tắt bàn để lại vết audit")
    void statusChangeIsAudited() throws Exception {
        String admin = staffBearer(UserRole.ADMIN);
        GameTable table = newTable();
        long before = auditLogRepository.countByAction(AuditTrailService.ADMIN_TABLE_STATUS_CHANGED);

        patchJson("/api/v1/admin/games/tables/" + table.getId() + "/status", admin, """
                {"status":"DISABLED","reason":"nghi ngo ket qua bat thuong"}
                """, 200);

        assertThat(auditLogRepository.countByAction(AuditTrailService.ADMIN_TABLE_STATUS_CHANGED))
                .isEqualTo(before + 1);
        assertThat(auditLogRepository.findByAction(AuditTrailService.ADMIN_TABLE_STATUS_CHANGED)
                .stream()
                .anyMatch(entry -> table.getId().toString().equals(entry.getTargetId())))
                .isTrue();
    }

    // ===== Hạn mức cược =====

    @Test
    @DisplayName("Đổi hạn mức -> ghi vào DB")
    void newLimitsTakeEffect() throws Exception {
        String admin = staffBearer(UserRole.ADMIN);
        GameTable table = newTable();

        patchJson("/api/v1/admin/games/tables/" + table.getId() + "/limits", admin, """
                {"minBet":"5","maxBet":"50","reason":"giam han muc de kiem soat rui ro"}
                """, 200);

        GameTable updated = tableRepository.findById(table.getId()).orElseThrow();
        assertThat(updated.getMinBet()).isEqualByComparingTo("5");
        assertThat(updated.getMaxBet()).isEqualByComparingTo("50");
    }

    @Test
    @DisplayName("minBet > maxBet bị chặn và hạn mức cũ KHÔNG bị thay đổi")
    void invalidLimitsRejectedAndNothingChanged() throws Exception {
        String admin = staffBearer(UserRole.ADMIN);
        GameTable table = newTable();

        patchJson("/api/v1/admin/games/tables/" + table.getId() + "/limits", admin, """
                {"minBet":"100","maxBet":"10","reason":"cau hinh sai"}
                """, 400);

        // Chặn nửa vời (ghi min rồi mới phát hiện lỗi) sẽ làm bàn không cược được.
        GameTable unchanged = tableRepository.findById(table.getId()).orElseThrow();
        assertThat(unchanged.getMinBet()).isEqualByComparingTo("1");
        assertThat(unchanged.getMaxBet()).isEqualByComparingTo("1000");
    }

    @Test
    @DisplayName("minBet = 0 hoặc số âm bị chặn")
    void nonPositiveMinBetRejected() throws Exception {
        String admin = staffBearer(UserRole.ADMIN);
        GameTable table = newTable();
        String path = "/api/v1/admin/games/tables/" + table.getId() + "/limits";

        patchJson(path, admin, """
                {"minBet":"0","maxBet":"100","reason":"min bang 0"}
                """, 400);
        // Số âm bị chặn ngay ở tầng validation (regex không cho dấu trừ).
        patchJson(path, admin, """
                {"minBet":"-5","maxBet":"100","reason":"min am"}
                """, 400);
    }

    @Test
    @DisplayName("Hạn mức nhận CHUỖI nên giữ đủ 8 chữ số thập phân")
    void limitsKeepFullPrecision() throws Exception {
        String admin = staffBearer(UserRole.ADMIN);
        GameTable table = newTable();

        patchJson("/api/v1/admin/games/tables/" + table.getId() + "/limits", admin, """
                {"minBet":"0.00000001","maxBet":"99999.99999999","reason":"kiem tra do chinh xac"}
                """, 200);

        GameTable updated = tableRepository.findById(table.getId()).orElseThrow();
        assertThat(updated.getMinBet()).isEqualByComparingTo("0.00000001");
        assertThat(updated.getMaxBet()).isEqualByComparingTo("99999.99999999");
    }

    // ===== Phân quyền =====

    @Test
    @DisplayName("RISK tắt được bàn nhưng KHÔNG đổi được hạn mức")
    void riskCanDisableButNotChangeLimits() throws Exception {
        String risk = staffBearer(UserRole.RISK);
        GameTable table = newTable();

        patchJson("/api/v1/admin/games/tables/" + table.getId() + "/status", risk, """
                {"status":"DISABLED","reason":"phat hien ket qua bat thuong"}
                """, 200);

        // Nâng maxBet là một đường rút tiền không cần chạm ví -> chỉ ADMIN.
        patchJson("/api/v1/admin/games/tables/" + table.getId() + "/limits", risk, """
                {"minBet":"1","maxBet":"99999999","reason":"risk thu nang han muc"}
                """, 403);
    }

    @Test
    @DisplayName("SUPPORT và FINANCE KHÔNG đổi được hạn mức cược")
    void supportAndFinanceCannotChangeLimits() throws Exception {
        GameTable table = newTable();
        String path = "/api/v1/admin/games/tables/" + table.getId() + "/limits";
        String body = """
                {"minBet":"1","maxBet":"99999999","reason":"thu nang han muc"}
                """;

        patchJson(path, staffBearer(UserRole.SUPPORT), body, 403);
        patchJson(path, staffBearer(UserRole.FINANCE), body, 403);
    }

    @Test
    @DisplayName("SUPPORT KHÔNG tắt được bàn")
    void supportCannotDisableTable() throws Exception {
        GameTable table = newTable();

        patchJson("/api/v1/admin/games/tables/" + table.getId() + "/status",
                staffBearer(UserRole.SUPPORT), """
                        {"status":"DISABLED","reason":"support thu tat ban"}
                        """, 403);
    }

    @Test
    @DisplayName("Người chơi bị chặn toàn bộ khu quản trị bàn")
    void playerBlocked() throws Exception {
        String player = register(unique("gblock"));
        GameTable table = newTable();

        mockMvc.perform(get("/api/v1/admin/games/tables").header("Authorization", player))
                .andExpect(status().isForbidden());
        patchJson("/api/v1/admin/games/tables/" + table.getId() + "/status", player, """
                {"status":"DISABLED","reason":"player thu tat ban"}
                """, 403);
    }
}
