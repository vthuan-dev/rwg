package com.rwg.affiliate;

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
 * API đại lý cho CHÍNH NGƯỜI CHƠI (chặng 6).
 *
 * Trước chặng này, hệ hoa hồng là code chết: người chơi không có cách nào lấy mã giới
 * thiệu của mình nên không ai giới thiệu được ai. Test quan trọng nhất ở đây là
 * {@link #fullReferralFlowWorksEndToEnd()} — chứng minh vòng lặp đã kín.
 *
 * Nhóm test thứ hai kiểm RIÊNG TƯ: API này không được thành kênh xem dữ liệu người khác.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MyAffiliateApiTest {

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

    private void register(String username, String referralCode) throws Exception {
        String body = referralCode == null
                ? """
                  {"username":"%s","email":"%s@example.com","password":"%s"}
                  """.formatted(username, username, PASSWORD)
                : """
                  {"username":"%s","email":"%s@example.com","password":"%s","referralCode":"%s"}
                  """.formatted(username, username, PASSWORD, referralCode);
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated());
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

    private String registerAndLogin(String username) throws Exception {
        register(username, null);
        return login(username);
    }

    private JsonNode getJson(String path, String bearer) throws Exception {
        MvcResult result = mockMvc.perform(get(path).header("Authorization", bearer))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private String myCode(String bearer) throws Exception {
        return getJson("/api/v1/affiliate/me/code", bearer).get("code").asText();
    }

    // ===== Vòng lặp giới thiệu đã kín =====

    @Test
    @DisplayName("Lấy mã -> người khác đăng ký bằng mã -> đại lý thấy tuyến dưới")
    void fullReferralFlowWorksEndToEnd() throws Exception {
        String agentName = unique("agent");
        String agent = registerAndLogin(agentName);

        // 1. Đại lý lấy mã của mình — bước TRƯỚC ĐÂY KHÔNG LÀM ĐƯỢC.
        String code = myCode(agent);
        assertThat(code).hasSize(8);

        // 2. Người mới đăng ký bằng mã đó.
        String memberName = unique("member");
        register(memberName, code);

        // 3. Đại lý thấy tuyến dưới của mình.
        JsonNode downline = getJson("/api/v1/affiliate/me/downline?level=1", agent);
        assertThat(downline.get("totalElements").asInt()).isEqualTo(1);

        JsonNode summary = getJson("/api/v1/affiliate/me/summary", agent);
        assertThat(summary.get("level1Count").asInt()).isEqualTo(1);
        assertThat(summary.get("level2Count").asInt()).isZero();
        assertThat(summary.get("code").asText()).isEqualTo(code);
    }

    @Test
    @DisplayName("Quan hệ 2 cấp hiện đúng ở cả hai đại lý")
    void twoLevelRelationVisibleToBothAgents() throws Exception {
        String topName = unique("top");
        String top = registerAndLogin(topName);
        String midName = unique("mid");
        register(midName, myCode(top));
        String mid = login(midName);

        // Người thứ ba vào tuyến của mid -> top có tuyến dưới cấp 2.
        register(unique("leaf"), myCode(mid));

        JsonNode topSummary = getJson("/api/v1/affiliate/me/summary", top);
        assertThat(topSummary.get("level1Count").asInt()).isEqualTo(1);
        assertThat(topSummary.get("level2Count").asInt()).isEqualTo(1);

        JsonNode midSummary = getJson("/api/v1/affiliate/me/summary", mid);
        assertThat(midSummary.get("level1Count").asInt()).isEqualTo(1);
        assertThat(midSummary.get("level2Count").asInt()).isZero();
    }

    @Test
    @DisplayName("Mã giới thiệu ỔN ĐỊNH: gọi nhiều lần vẫn ra cùng một mã")
    void codeIsStableAcrossCalls() throws Exception {
        String bearer = registerAndLogin(unique("stable"));
        // Nếu mỗi lần gọi sinh mã mới thì mã đã phát cho bạn bè sẽ chết -> mất hoa hồng.
        assertThat(myCode(bearer)).isEqualTo(myCode(bearer));
    }

    @Test
    @DisplayName("Trả kèm đường dẫn đăng ký sẵn để người chơi copy")
    void responseIncludesReadyToShareLink() throws Exception {
        String bearer = registerAndLogin(unique("link"));
        JsonNode body = getJson("/api/v1/affiliate/me/code", bearer);
        assertThat(body.get("registerPath").asText())
                .isEqualTo("/register?ref=" + body.get("code").asText());
    }

    // ===== Riêng tư =====

    @Test
    @DisplayName("Username tuyến dưới bị CHE, không lộ userId")
    void downlineUsernameIsMasked() throws Exception {
        String agent = registerAndLogin(unique("privacy"));
        String memberName = unique("secret");
        register(memberName, myCode(agent));

        JsonNode row = getJson("/api/v1/affiliate/me/downline?level=1", agent)
                .get("content").get(0);

        assertThat(row.get("maskedUsername").asText())
                .isEqualTo(memberName.substring(0, 2) + "***");
        // Tên đầy đủ KHÔNG được xuất hiện ở bất kỳ đâu trong response.
        assertThat(row.toString()).doesNotContain(memberName);
        assertThat(row.has("userId")).isFalse();
    }

    @Test
    @DisplayName("Hoa hồng KHÔNG trả turnover (tổng cược của tuyến dưới là dữ liệu người khác)")
    void commissionsHideDownlineTurnover() throws Exception {
        String bearer = registerAndLogin(unique("noturn"));
        JsonNode body = getJson("/api/v1/affiliate/me/commissions", bearer);

        assertThat(body.get("content").isArray()).isTrue();
        assertThat(body.toString()).doesNotContain("turnover");
        assertThat(body.toString()).doesNotContain("agentId");
    }

    @Test
    @DisplayName("Không có tham số nào cho phép xem dữ liệu người khác")
    void cannotQueryAnotherUsersData() throws Exception {
        String victimName = unique("victim");
        String victim = registerAndLogin(victimName);
        register(unique("victimref"), myCode(victim));
        UUID victimId = userRepository.findByUsername(victimName).orElseThrow().getId();

        String attacker = registerAndLogin(unique("attacker"));

        // Thử nhồi userId/agentId — controller không đọc tham số này nên kết quả vẫn là
        // dữ liệu CỦA CHÍNH kẻ gọi (rỗng), chứ không phải của nạn nhân.
        JsonNode probed = getJson(
                "/api/v1/affiliate/me/downline?level=1&userId=" + victimId + "&agentId=" + victimId,
                attacker);
        assertThat(probed.get("totalElements").asInt()).isZero();

        JsonNode commissions = getJson(
                "/api/v1/affiliate/me/commissions?agentId=" + victimId, attacker);
        assertThat(commissions.get("totalElements").asInt()).isZero();
    }

    @Test
    @DisplayName("Chưa đăng nhập -> 401 trên toàn bộ endpoint")
    void requiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/affiliate/me/code")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/affiliate/me/summary")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/affiliate/me/downline")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/affiliate/me/commissions")).andExpect(status().isUnauthorized());
    }

    // ===== Kiểm tham số =====

    @Test
    @DisplayName("Cấp ngoài 1..2 bị chặn")
    void invalidLevelRejected() throws Exception {
        String bearer = registerAndLogin(unique("level"));

        mockMvc.perform(get("/api/v1/affiliate/me/downline?level=3").header("Authorization", bearer))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/v1/affiliate/me/downline?level=0").header("Authorization", bearer))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Khoảng ngày ngược bị chặn")
    void invalidDateRangeRejected() throws Exception {
        String bearer = registerAndLogin(unique("range"));

        mockMvc.perform(get("/api/v1/affiliate/me/commissions?from=2026-08-20&to=2026-08-01")
                        .header("Authorization", bearer))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Người chơi mới: tổng quan trả 0, không lỗi")
    void newPlayerGetsZeroSummary() throws Exception {
        String bearer = registerAndLogin(unique("fresh"));
        JsonNode summary = getJson("/api/v1/affiliate/me/summary", bearer);

        assertThat(summary.get("level1Count").asInt()).isZero();
        assertThat(summary.get("level2Count").asInt()).isZero();
        assertThat(new java.math.BigDecimal(summary.get("totalCommissionEarned").asText()))
                .isEqualByComparingTo("0");
        // % hiện hành phải có sẵn từ migration để người chơi biết mình được bao nhiêu.
        assertThat(summary.get("level1Rate").asText()).isNotBlank();
    }
}
