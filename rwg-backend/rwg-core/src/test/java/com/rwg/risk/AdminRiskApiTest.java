package com.rwg.risk;

import com.rwg.identity.domain.User;
import com.rwg.identity.domain.UserRole;
import com.rwg.identity.repository.AuditLogRepository;
import com.rwg.identity.repository.UserRepository;
import com.rwg.identity.service.AuditTrailService;
import com.rwg.risk.domain.AccountLink;
import com.rwg.risk.domain.AccountLinkStatus;
import com.rwg.risk.domain.AccountLinkType;
import com.rwg.risk.repository.AccountLinkRepository;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * API quản trị risk (chặng 7): hàng đợi liên kết, kết luận, nối tay.
 *
 * Trọng tâm là PHÂN QUYỀN và HỆ QUẢ THẬT: kết luận CONFIRMED phải làm liên kết
 * chuyển sang trạng thái giữ tiền, CLEARED phải thôi giữ. Chỉ kiểm mã HTTP là không
 * đủ vì đây là căn cứ giữ tiền của người khác.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminRiskApiTest {

    private static final String PASSWORD = "MatKhau@12345";

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    UserRepository userRepository;

    @Autowired
    AccountLinkRepository linkRepository;

    @Autowired
    AuditLogRepository auditLogRepository;

    private String unique(String prefix) {
        return prefix + UUID.randomUUID().toString().substring(0, 8);
    }

    /**
     * Đăng ký với IP RIÊNG cho từng tài khoản.
     *
     * BẮT BUỘC phải đổi IP: MockMvc mặc định remoteAddr = 127.0.0.1 cho mọi request,
     * nên nếu để nguyên thì detector sẽ coi tất cả tài khoản trong test là một chùm
     * cùng IP và tự nối chúng với nhau — test sẽ đụng UNIQUE khi tự tạo liên kết.
     */
    private String register(String username) throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"%s","password":"%s"}
                                """.formatted(username, PASSWORD))
                        .with(request -> {
                            request.setRemoteAddr(freshIp());
                            return request;
                        }))
                .andExpect(status().isCreated());
        return login(username);
    }

    /** IP không trùng nhau để không sinh chùm IP ngoài ý muốn. */
    private String freshIp() {
        return "198." + (10 + (int) (Math.random() * 200))
                + "." + (1 + (int) (Math.random() * 250))
                + "." + (1 + (int) (Math.random() * 250));
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
        String username = unique("radm");
        register(username);
        User user = userRepository.findByUsername(username).orElseThrow();
        user.setRole(role);
        userRepository.save(user);
        // Login LẠI: token cũ phát hành trước khi có vai trò mới.
        return login(username);
    }

    private User newPlayer() throws Exception {
        String username = unique("rply");
        register(username);
        return userRepository.findByUsername(username).orElseThrow();
    }

    private AccountLink newSuspectedLink(User a, User b) {
        return linkRepository.saveAndFlush(AccountLink.of(a.getId(), b.getId(),
                AccountLinkType.SHARED_DEVICE, "{\"test\":\"1\"}"));
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

    // ===== Đọc =====

    @Test
    @DisplayName("ADMIN xem được hàng đợi liên kết")
    void adminListsLinks() throws Exception {
        newSuspectedLink(newPlayer(), newPlayer());
        String admin = staffBearer(UserRole.ADMIN);

        MvcResult result = mockMvc.perform(get("/api/v1/admin/risk/links?status=SUSPECTED")
                        .header("Authorization", admin))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(body.get("content").size()).isPositive();
        assertThat(body.get("content").get(0).get("status").asText()).isEqualTo("SUSPECTED");
    }

    @Test
    @DisplayName("Hàng đợi trả cờ blocksCommission để người vận hành không phải tự suy")
    void listExposesBlockingFlag() throws Exception {
        newSuspectedLink(newPlayer(), newPlayer());
        String admin = staffBearer(UserRole.ADMIN);

        MvcResult result = mockMvc.perform(get("/api/v1/admin/risk/links?status=SUSPECTED")
                        .header("Authorization", admin))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode first = objectMapper.readTree(result.getResponse().getContentAsString())
                .get("content").get(0);
        // SHARED_DEVICE + SUSPECTED là trường hợp GIỮ tiền.
        assertThat(first.get("blocksCommission").asBoolean()).isTrue();
    }

    @Test
    @DisplayName("Hồ sơ risk của user gộp dấu vết đăng ký và toàn bộ liên kết")
    void userProfileAggregatesSignalAndLinks() throws Exception {
        User player = newPlayer();
        newSuspectedLink(player, newPlayer());
        String admin = staffBearer(UserRole.ADMIN);

        MvcResult result = mockMvc.perform(
                        get("/api/v1/admin/risk/users/" + player.getId())
                                .header("Authorization", admin))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(body.get("username").asText()).isEqualTo(player.getUsername());
        assertThat(body.get("registrationIp").asText()).isNotBlank();
        assertThat(body.get("links").size()).isEqualTo(1);
        assertThat(body.get("commissionBlocked").asBoolean()).isTrue();
    }

    @Test
    @DisplayName("Hồ sơ risk của user không tồn tại -> 404")
    void unknownUserProfileReturns404() throws Exception {
        String admin = staffBearer(UserRole.ADMIN);
        mockMvc.perform(get("/api/v1/admin/risk/users/" + UUID.randomUUID())
                        .header("Authorization", admin))
                .andExpect(status().isNotFound());
    }

    // ===== Kết luận =====

    @Test
    @DisplayName("RISK xác nhận liên kết -> chuyển CONFIRMED và vẫn giữ tiền")
    void riskConfirmsLink() throws Exception {
        AccountLink link = newSuspectedLink(newPlayer(), newPlayer());
        String risk = staffBearer(UserRole.RISK);

        JsonNode body = patchJson("/api/v1/admin/risk/links/" + link.getId(), risk,
                """
                {"status":"CONFIRMED","note":"cung mot nguoi"}
                """, 200);

        assertThat(body.get("status").asText()).isEqualTo("CONFIRMED");
        assertThat(body.get("blocksCommission").asBoolean()).isTrue();
        assertThat(linkRepository.findById(link.getId()).orElseThrow().getStatus())
                .isEqualTo(AccountLinkStatus.CONFIRMED);
    }

    @Test
    @DisplayName("Gỡ oan -> CLEARED và THÔI giữ tiền")
    void clearingStopsBlocking() throws Exception {
        AccountLink link = newSuspectedLink(newPlayer(), newPlayer());
        String admin = staffBearer(UserRole.ADMIN);

        JsonNode body = patchJson("/api/v1/admin/risk/links/" + link.getId(), admin,
                """
                {"status":"CLEARED","note":"hai nguoi khac nhau that"}
                """, 200);

        assertThat(body.get("blocksCommission").asBoolean()).isFalse();
        assertThat(linkRepository.findById(link.getId()).orElseThrow().blocksCommission())
                .isFalse();
    }

    @Test
    @DisplayName("Kết luận có ghi vết người xét duyệt và lý do")
    void reviewRecordsWhoAndWhy() throws Exception {
        AccountLink link = newSuspectedLink(newPlayer(), newPlayer());
        String admin = staffBearer(UserRole.ADMIN);
        long before = auditLogRepository.countByAction(
                AuditTrailService.RISK_ACCOUNT_LINK_REVIEWED);

        patchJson("/api/v1/admin/risk/links/" + link.getId(), admin,
                """
                {"status":"CONFIRMED","note":"cung thiet bi va cung ngan hang"}
                """, 200);

        AccountLink saved = linkRepository.findById(link.getId()).orElseThrow();
        assertThat(saved.getReviewedBy()).isNotNull();
        assertThat(saved.getReviewedAt()).isNotNull();
        assertThat(saved.getNote()).isEqualTo("cung thiet bi va cung ngan hang");
        assertThat(auditLogRepository.countByAction(
                AuditTrailService.RISK_ACCOUNT_LINK_REVIEWED)).isGreaterThan(before);
    }

    @Test
    @DisplayName("Kết luận trùng trạng thái hiện tại -> 400")
    void sameStatusRejected() throws Exception {
        AccountLink link = newSuspectedLink(newPlayer(), newPlayer());
        String admin = staffBearer(UserRole.ADMIN);

        patchJson("/api/v1/admin/risk/links/" + link.getId(), admin,
                """
                {"status":"CONFIRMED","note":"lan dau"}
                """, 200);
        // ErrorCode.INVALID_STATUS_TRANSITION là BAD_REQUEST — dùng lại đúng hằng mà
        // chặng 6 đã dùng cho "bàn đã ở trạng thái này".
        patchJson("/api/v1/admin/risk/links/" + link.getId(), admin,
                """
                {"status":"CONFIRMED","note":"lan hai"}
                """, 400);
    }

    @Test
    @DisplayName("SUSPECTED không phải kết luận hợp lệ -> 400")
    void suspectedIsNotAValidDecision() throws Exception {
        AccountLink link = newSuspectedLink(newPlayer(), newPlayer());
        String admin = staffBearer(UserRole.ADMIN);

        // Một khi người đã xem thì kết luận của người thắng máy — không quay lại được.
        patchJson("/api/v1/admin/risk/links/" + link.getId(), admin,
                """
                {"status":"SUSPECTED","note":"thu quay lai"}
                """, 400);
    }

    @Test
    @DisplayName("Thiếu lý do -> 400 (liên kết là căn cứ giữ tiền, phải truy được vì sao)")
    void noteIsRequired() throws Exception {
        AccountLink link = newSuspectedLink(newPlayer(), newPlayer());
        String admin = staffBearer(UserRole.ADMIN);

        patchJson("/api/v1/admin/risk/links/" + link.getId(), admin,
                """
                {"status":"CLEARED","note":"  "}
                """, 400);
    }

    @Test
    @DisplayName("Kết luận trên liên kết không tồn tại -> 404")
    void reviewUnknownLinkReturns404() throws Exception {
        String admin = staffBearer(UserRole.ADMIN);
        patchJson("/api/v1/admin/risk/links/" + UUID.randomUUID(), admin,
                """
                {"status":"CONFIRMED","note":"khong ton tai"}
                """, 404);
    }

    // ===== Nối tay =====

    @Test
    @DisplayName("Nối tay tạo liên kết MANUAL + CONFIRMED, giữ tiền ngay")
    void manualLinkIsConfirmedImmediately() throws Exception {
        User a = newPlayer();
        User b = newPlayer();
        String admin = staffBearer(UserRole.ADMIN);

        MvcResult result = mockMvc.perform(post("/api/v1/admin/risk/links")
                        .header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"userAId":"%s","userBId":"%s","note":"cung so tai khoan ngan hang"}
                                """.formatted(a.getId(), b.getId())))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(body.get("linkType").asText()).isEqualTo("MANUAL");
        assertThat(body.get("status").asText()).isEqualTo("CONFIRMED");
        assertThat(body.get("blocksCommission").asBoolean()).isTrue();
    }

    @Test
    @DisplayName("Nối tay lưu cặp ĐÃ SẮP XẾP dù truyền thứ tự ngược")
    void manualLinkOrdersPair() throws Exception {
        User a = newPlayer();
        User b = newPlayer();
        // Truyền id LỚN trước để chắc chắn service phải tự sắp xếp.
        User first = a.getId().toString().compareTo(b.getId().toString()) > 0 ? a : b;
        User second = first == a ? b : a;
        String admin = staffBearer(UserRole.ADMIN);

        MvcResult result = mockMvc.perform(post("/api/v1/admin/risk/links")
                        .header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"userAId":"%s","userBId":"%s","note":"thu tu nguoc"}
                                """.formatted(first.getId(), second.getId())))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(body.get("userAId").asText())
                .isLessThan(body.get("userBId").asText());
    }

    @Test
    @DisplayName("Nối tay cặp đã có liên kết -> 409, KHÔNG ghi đè kết luận cũ")
    void duplicateManualLinkRejected() throws Exception {
        User a = newPlayer();
        User b = newPlayer();
        AccountLink existing = newSuspectedLink(a, b);
        String admin = staffBearer(UserRole.ADMIN);

        mockMvc.perform(post("/api/v1/admin/risk/links")
                        .header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"userAId":"%s","userBId":"%s","note":"noi lai"}
                                """.formatted(a.getId(), b.getId())))
                .andExpect(status().isConflict());

        // Liên kết cũ phải còn nguyên trạng thái, không bị biến thành MANUAL.
        AccountLink unchanged = linkRepository.findById(existing.getId()).orElseThrow();
        assertThat(unchanged.getLinkType()).isEqualTo(AccountLinkType.SHARED_DEVICE);
        assertThat(unchanged.getStatus()).isEqualTo(AccountLinkStatus.SUSPECTED);
    }

    @Test
    @DisplayName("Nối một tài khoản với chính nó -> 400")
    void selfLinkRejected() throws Exception {
        User a = newPlayer();
        String admin = staffBearer(UserRole.ADMIN);

        mockMvc.perform(post("/api/v1/admin/risk/links")
                        .header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"userAId":"%s","userBId":"%s","note":"tu noi chinh minh"}
                                """.formatted(a.getId(), a.getId())))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Nối với user không tồn tại -> 404")
    void unknownUserRejected() throws Exception {
        User a = newPlayer();
        String admin = staffBearer(UserRole.ADMIN);

        mockMvc.perform(post("/api/v1/admin/risk/links")
                        .header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"userAId":"%s","userBId":"%s","note":"khong ton tai"}
                                """.formatted(a.getId(), UUID.randomUUID())))
                .andExpect(status().isNotFound());
    }

    // ===== Phân quyền =====

    @Test
    @DisplayName("PLAYER không vào được khu risk -> 403")
    void playerForbidden() throws Exception {
        String player = register(unique("rp"));
        mockMvc.perform(get("/api/v1/admin/risk/links").header("Authorization", player))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("SUPPORT không vào được khu risk -> 403")
    void supportForbidden() throws Exception {
        String support = staffBearer(UserRole.SUPPORT);
        mockMvc.perform(get("/api/v1/admin/risk/links").header("Authorization", support))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("FINANCE không vào được khu risk -> 403")
    void financeForbidden() throws Exception {
        String finance = staffBearer(UserRole.FINANCE);
        mockMvc.perform(get("/api/v1/admin/risk/links").header("Authorization", finance))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Không có token -> 401")
    void anonymousUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/admin/risk/links"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("RISK nối tay được (thao tác này không chuyển tiền)")
    void riskCanCreateManualLink() throws Exception {
        User a = newPlayer();
        User b = newPlayer();
        String risk = staffBearer(UserRole.RISK);

        mockMvc.perform(post("/api/v1/admin/risk/links")
                        .header("Authorization", risk)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"userAId":"%s","userBId":"%s","note":"risk tu noi"}
                                """.formatted(a.getId(), b.getId())))
                .andExpect(status().isOk());
    }
}
