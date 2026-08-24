package com.rwg.chat;

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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Thẻ duyệt lệnh rút trong luồng chat hỗ trợ.
 *
 * TRỌNG TÂM là bất biến về QUYỀN ĐỌC: thẻ mang nút chuyển tiền, nên việc nó không lọt sang
 * phía người chơi là điều phải có test canh. Một lỗi ở đó không gây exception, không hiện
 * trong log — nó chỉ hiện ra khi có người chơi nhìn thấy nút duyệt lệnh rút của chính mình.
 *
 * Cũng canh việc thẻ KHÔNG làm tăng bộ đếm chưa đọc: viên đỏ báo tin mới phải giữ nghĩa
 * "có người nói với bạn", nếu nó nhảy vì một tin họ không đọc được thì người dùng sẽ học
 * cách bỏ qua nó.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ChatWithdrawalCardTest {

    private static final String PASSWORD = "MatKhau@12345";
    private static final String WITHDRAW_PW = "654321";

    /** Endpoint duyệt/từ chối BẮT BUỘC kèm lý do để ghi nhật ký. */
    private static final String DECISION_BODY = "{\"note\":\"kiem tra tu dong\"}";

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    UserRepository userRepository;

    // ===== helper =====

    private String unique(String prefix) {
        return prefix + UUID.randomUUID().toString().substring(0, 8);
    }

    private JsonNode json(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private String login(String username) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"identifier":"%s","password":"%s"}
                                """.formatted(username, PASSWORD)))
                .andExpect(status().isOk())
                .andReturn();
        return "Bearer " + json(result).get("accessToken").asText();
    }

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

    /** Nhân sự đăng nhập qua cổng quản trị — chat và duyệt rút đều nằm sau /admin. */
    private String staffBearer(UserRole role) throws Exception {
        String username = register("staffwc");
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
        return "Bearer " + json(result).get("accessToken").asText();
    }

    /**
     * Người chơi đủ điều kiện rút: có tiền, có mật khẩu rút, có tài khoản nhận.
     *
     * Thứ tự BẮT BUỘC là mật khẩu rút TRƯỚC tài khoản nhận: liên kết tài khoản đòi xác nhận
     * mật khẩu rút, nên làm ngược sẽ bị từ chối.
     */
    private String fundedPlayer() throws Exception {
        String username = register("wcplayer");
        String bearer = login(username);

        mockMvc.perform(post("/api/v1/wallet/deposits")
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"amount":"100"}
                                """))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/users/me/withdrawal-password")
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"loginPassword":"%s","newWithdrawalPassword":"%s"}
                                """.formatted(PASSWORD, WITHDRAW_PW)))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/wallet/me/bank-accounts")
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"bankCode":"VCB","accountNumber":"0123456789","holderName":"NGUYEN VAN A","withdrawalPassword":"%s"}
                                """.formatted(WITHDRAW_PW)))
                .andExpect(status().isCreated());

        return bearer;
    }

    /** Tạo lệnh rút, trả về mã lệnh. */
    private String requestWithdrawal(String bearer, String amount) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/wallet/withdrawals")
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"amount":"%s","withdrawalPassword":"%s"}
                                """.formatted(amount, WITHDRAW_PW)))
                .andExpect(status().isCreated())
                .andReturn();
        return json(result).get("id").asText();
    }

    private String myConversationId(String bearer) throws Exception {
        return json(mockMvc.perform(get("/api/v1/chat/conversation")
                        .header("Authorization", bearer))
                .andExpect(status().isOk())
                .andReturn())
                .get("id").asText();
    }

    /** Tin nhắn của một luồng, đọc bằng quyền nhân sự. */
    private JsonNode staffMessages(String staff, String conversationId) throws Exception {
        return json(mockMvc.perform(
                        get("/api/v1/admin/chat/conversations/" + conversationId + "/messages")
                                .header("Authorization", staff))
                .andExpect(status().isOk())
                .andReturn());
    }

    /** Thẻ đầu tiên tìm thấy trong một trang tin nhắn; null nếu không có. */
    private JsonNode findCard(JsonNode messages) {
        for (JsonNode message : messages) {
            JsonNode card = message.get("withdrawal");
            if (card != null && !card.isNull()) {
                return card;
            }
        }
        return null;
    }

    // ===== test =====

    @Test
    @DisplayName("Tạo lệnh rút: thẻ duyệt xuất hiện trong luồng chat của nhân sự, kèm số tiền và tài khoản nhận")
    void withdrawalCreatesCardInStaffThread() throws Exception {
        String player = fundedPlayer();
        String orderId = requestWithdrawal(player, "30");

        String conversationId = myConversationId(player);
        String staff = staffBearer(UserRole.ADMIN);

        JsonNode card = findCard(staffMessages(staff, conversationId));

        assertThat(card).isNotNull();
        assertThat(card.get("orderId").asString()).isEqualTo(orderId);
        assertThat(card.get("status").asString()).isEqualTo("PENDING");
        assertThat(new java.math.BigDecimal(card.get("amount").asString()))
                .isEqualByComparingTo("30");
        // 4 số cuối của số tài khoản, để nhân sự nhận diện đúng người mà không cần gọi
        // endpoint reveal (mỗi lần gọi endpoint đó ghi một dòng nhật ký).
        assertThat(card.get("maskedLast4").asString()).isEqualTo("6789");
        // Lệnh còn chờ duyệt thì chưa ai quyết định.
        assertThat(card.get("decidedByUsername").isNull()).isTrue();
    }

    @Test
    @DisplayName("Người chơi KHÔNG đọc được thẻ duyệt trong luồng của chính mình")
    void playerCannotSeeWithdrawalCard() throws Exception {
        String player = fundedPlayer();
        requestWithdrawal(player, "25");

        JsonNode mine = json(mockMvc.perform(get("/api/v1/chat/messages")
                        .header("Authorization", player))
                .andExpect(status().isOk())
                .andReturn());

        // Thẻ là tin DUY NHẤT trong luồng lúc này (người chơi chưa gõ gì), nên nếu lọc
        // theo visible_to bị bỏ sót thì danh sách này sẽ có đúng một phần tử.
        assertThat(mine).isEmpty();
    }

    @Test
    @DisplayName("Thẻ duyệt KHÔNG làm tăng số tin chưa đọc của người chơi")
    void withdrawalCardDoesNotBumpPlayerUnread() throws Exception {
        String player = fundedPlayer();
        requestWithdrawal(player, "20");

        mockMvc.perform(get("/api/v1/chat/unread-count").header("Authorization", player))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.messages").value(0));
    }

    @Test
    @DisplayName("Duyệt lệnh: thẻ đọc lại có trạng thái SETTLED kèm người quyết định và lý do")
    void cardReflectsDecisionAfterApproval() throws Exception {
        String player = fundedPlayer();
        String orderId = requestWithdrawal(player, "40");

        String conversationId = myConversationId(player);
        String staff = staffBearer(UserRole.ADMIN);

        mockMvc.perform(post("/api/v1/admin/withdrawals/" + orderId + "/approve")
                        .header("Authorization", staff)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(DECISION_BODY))
                .andExpect(status().isOk());

        JsonNode card = findCard(staffMessages(staff, conversationId));

        assertThat(card).isNotNull();
        // Trạng thái đọc từ BẢNG LỆNH, không phải chép vào tin nhắn — nên một lệnh xử lý ở
        // trang duyệt rút tiền cũng làm thẻ này đổi theo. Đó là điều test này canh.
        assertThat(card.get("status").asString()).isEqualTo("SETTLED");
        assertThat(card.get("decisionNote").asString()).isEqualTo("kiem tra tu dong");
        assertThat(card.get("decidedByUsername").isNull()).isFalse();
    }

    @Test
    @DisplayName("Từ chối lệnh: thẻ hiện VOIDED (tiền đã hoàn lại ví ở luồng nghiệp vụ)")
    void cardReflectsRejection() throws Exception {
        String player = fundedPlayer();
        String orderId = requestWithdrawal(player, "50");

        String conversationId = myConversationId(player);
        String staff = staffBearer(UserRole.ADMIN);

        mockMvc.perform(post("/api/v1/admin/withdrawals/" + orderId + "/reject")
                        .header("Authorization", staff)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(DECISION_BODY))
                .andExpect(status().isOk());

        JsonNode card = findCard(staffMessages(staff, conversationId));

        assertThat(card).isNotNull();
        assertThat(card.get("status").asString()).isEqualTo("VOIDED");
    }

    @Test
    @DisplayName("Vai trò RISK đọc được thẻ nhưng KHÔNG duyệt được (403)")
    void riskCanReadCardButCannotApprove() throws Exception {
        String player = fundedPlayer();
        String orderId = requestWithdrawal(player, "22");

        String conversationId = myConversationId(player);
        String risk = staffBearer(UserRole.RISK);

        // Đọc được: đánh giá gian lận cần thấy hoạt động rút tiền.
        assertThat(findCard(staffMessages(risk, conversationId))).isNotNull();

        // Nhưng không chạm được vào tiền — matcher trong SecurityConfig chỉ mở
        // POST /admin/withdrawals/*/approve cho ADMIN và FINANCE.
        mockMvc.perform(post("/api/v1/admin/withdrawals/" + orderId + "/approve")
                        .header("Authorization", risk)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(DECISION_BODY))
                .andExpect(status().isForbidden());
    }
}
