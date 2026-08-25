package com.rwg.chat;

import com.rwg.chat.domain.ChatConversationStatus;
import com.rwg.chat.repository.ChatConversationRepository;
import com.rwg.identity.domain.AuditLog;
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

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Kiểm tra tích hợp chat hỗ trợ hai chiều.
 *
 * Trọng tâm là các bất biến mà một lỗi ở đó sẽ âm thầm gây hại: cách ly dữ liệu
 * giữa các người chơi, chống gửi trùng, tính đúng số chưa đọc, và chặn vai trò RISK
 * trả lời thay mặt sàn.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ChatApiTest {

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

    @Autowired
    ChatConversationRepository conversationRepository;

    // ===== helper (theo mẫu PayoutMethodApiTest) =====

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

    private String playerBearer(String username) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"identifier":"%s","password":"%s"}
                                """.formatted(username, PASSWORD)))
                .andExpect(status().isOk())
                .andReturn();
        return bearerOf(result);
    }

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
        return bearerOf(result);
    }

    private String bearerOf(MvcResult result) throws Exception {
        return "Bearer " + objectMapper.readTree(result.getResponse().getContentAsString())
                .get("accessToken").asText();
    }

    private JsonNode json(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    /** Người chơi gửi một tin, trả về nội dung phản hồi. */
    private JsonNode sendAsPlayer(String bearer, String body, String clientMsgId) throws Exception {
        String payload = clientMsgId == null
                ? """
                  {"body":"%s"}
                  """.formatted(body)
                : """
                  {"body":"%s","clientMsgId":"%s"}
                  """.formatted(body, clientMsgId);

        return json(mockMvc.perform(post("/api/v1/chat/messages")
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andReturn());
    }

    /** Id luồng của người chơi đang đăng nhập. */
    private String myConversationId(String bearer) throws Exception {
        return json(mockMvc.perform(get("/api/v1/chat/conversation")
                        .header("Authorization", bearer))
                .andExpect(status().isOk())
                .andReturn())
                .get("id").asText();
    }

    // ===== test =====

    @Test
    @DisplayName("Người chơi gửi tin: luồng được tạo, nhân sự thấy trong hộp thư kèm số chưa đọc")
    void playerMessageAppearsInAdminInbox() throws Exception {
        String username = register("chatp");
        String player = playerBearer(username);

        JsonNode sent = sendAsPlayer(player, "Toi khong rut duoc tien", null);
        assertThat(sent.get("senderType").asText()).isEqualTo("PLAYER");
        assertThat(sent.get("senderUsername").asText()).isEqualTo(username);
        // readAt null = nhân sự chưa xem. Đây là thứ giao diện dùng để vẽ dấu tích.
        assertThat(sent.get("readAt").isNull()).isTrue();

        String staff = staffBearer(UserRole.SUPPORT);
        mockMvc.perform(get("/api/v1/admin/chat/conversations")
                        .header("Authorization", staff)
                        .param("q", username))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].username").value(username))
                .andExpect(jsonPath("$.content[0].unreadCount").value(1))
                .andExpect(jsonPath("$.content[0].status").value("OPEN"))
                // Đoạn xem trước phải có sẵn trong dòng hộp thư — không phải đi tải
                // riêng tin nhắn cho từng dòng.
                .andExpect(jsonPath("$.content[0].lastMessagePreview").value("Toi khong rut duoc tien"))
                // Chưa ai bấm nhận việc nên luồng vẫn nằm trong hàng đợi chung.
                .andExpect(jsonPath("$.content[0].assignedAdminId").isEmpty());
    }

    @Test
    @DisplayName("Nhân sự trả lời: tự nhận luồng, người chơi thấy tin và số chưa đọc của mình")
    void staffReplyAutoAssignsAndReachesPlayer() throws Exception {
        String username = register("chatr");
        String player = playerBearer(username);
        sendAsPlayer(player, "Cho hoi ve tien thuong", null);

        String conversationId = myConversationId(player);
        String staff = staffBearer(UserRole.SUPPORT);

        JsonNode reply = json(mockMvc.perform(
                        post("/api/v1/admin/chat/conversations/" + conversationId + "/messages")
                                .header("Authorization", staff)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {"body":"Chung toi dang kiem tra giup ban"}
                                        """))
                .andExpect(status().isOk())
                .andReturn());
        assertThat(reply.get("senderType").asText()).isEqualTo("STAFF");

        // Trả lời mà chưa bấm "nhận việc" thì luồng vẫn phải rời hàng đợi chung: nếu
        // không, hàng đợi đầy những luồng đã được trả lời nhưng hiện là chưa ai nhận.
        var conversation = conversationRepository.findById(UUID.fromString(conversationId)).orElseThrow();
        assertThat(conversation.getAssignedAdminId()).isNotNull();

        mockMvc.perform(get("/api/v1/chat/unread-count").header("Authorization", player))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.messages").value(1));

        mockMvc.perform(get("/api/v1/chat/messages").header("Authorization", player))
                .andExpect(status().isOk())
                // Mới nhất trước: tin của nhân sự nằm đầu danh sách.
                .andExpect(jsonPath("$[0].senderType").value("STAFF"))
                .andExpect(jsonPath("$[1].senderType").value("PLAYER"));
    }

    @Test
    @DisplayName("Người chơi chỉ đọc được luồng của chính mình, không thấy tin của người khác")
    void playersCannotSeeEachOthersMessages() throws Exception {
        String firstPlayer = playerBearer(register("chatA"));
        sendAsPlayer(firstPlayer, "Tin rieng cua nguoi thu nhat", null);

        String secondPlayer = playerBearer(register("chatB"));

        // Không có tham số conversationId ở API người chơi, nên chỉ cần kiểm tra
        // người thứ hai không nhìn thấy gì của người thứ nhất.
        mockMvc.perform(get("/api/v1/chat/messages").header("Authorization", secondPlayer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));

        assertThat(myConversationId(firstPlayer)).isNotEqualTo(myConversationId(secondPlayer));
    }

    @Test
    @DisplayName("Gửi lại cùng clientMsgId: chỉ tạo MỘT tin (chống gửi trùng khi mạng lỗi)")
    void duplicateClientMsgIdCreatesSingleMessage() throws Exception {
        String player = playerBearer(register("chatd"));
        String clientMsgId = UUID.randomUUID().toString();

        JsonNode first = sendAsPlayer(player, "Tin bi gui hai lan", clientMsgId);
        JsonNode second = sendAsPlayer(player, "Tin bi gui hai lan", clientMsgId);

        // Lần thứ hai phải trả về ĐÚNG bản ghi cũ, không phải một tin mới.
        assertThat(second.get("id").asText()).isEqualTo(first.get("id").asText());

        mockMvc.perform(get("/api/v1/chat/messages").header("Authorization", player))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    @DisplayName("Nhân sự đánh dấu đã đọc: bộ đếm về 0, tin của người chơi có mốc đã xem")
    void staffMarkReadClearsCounterAndStampsMessages() throws Exception {
        String username = register("chatm");
        String player = playerBearer(username);
        sendAsPlayer(player, "Tin thu nhat", null);
        sendAsPlayer(player, "Tin thu hai", null);

        String conversationId = myConversationId(player);
        String staff = staffBearer(UserRole.SUPPORT);

        mockMvc.perform(get("/api/v1/admin/chat/unread-count").header("Authorization", staff))
                .andExpect(status().isOk())
                // Hai tin, một luồng đang chờ: hai con số này diễn tả hai mức cấp bách
                // khác nhau nên phải tách riêng.
                .andExpect(jsonPath("$.messages").value(2))
                .andExpect(jsonPath("$.conversations").value(1));

        mockMvc.perform(post("/api/v1/admin/chat/conversations/" + conversationId + "/read")
                        .header("Authorization", staff))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.updated").value(2));

        var conversation = conversationRepository.findById(UUID.fromString(conversationId)).orElseThrow();
        assertThat(conversation.getUnreadForAdmin()).isZero();

        mockMvc.perform(get("/api/v1/admin/chat/conversations/" + conversationId + "/messages")
                        .header("Authorization", staff))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].readAt").isNotEmpty())
                .andExpect(jsonPath("$[1].readAt").isNotEmpty());
    }

    @Test
    @DisplayName("Đóng luồng rồi người chơi gửi tin mới: luồng tự mở lại")
    void playerMessageReopensClosedConversation() throws Exception {
        String player = playerBearer(register("chatc"));
        sendAsPlayer(player, "Van de ban dau", null);

        String conversationId = myConversationId(player);
        String staff = staffBearer(UserRole.SUPPORT);

        mockMvc.perform(post("/api/v1/admin/chat/conversations/" + conversationId + "/close")
                        .header("Authorization", staff))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CLOSED"));

        sendAsPlayer(player, "Van de van chua xong", null);

        // Nếu không tự mở lại, người chơi gõ vào một luồng đã đóng và không xuất hiện
        // trong hàng đợi — họ nói vào chỗ không ai nghe mà vẫn thấy tin đã gửi đi.
        var conversation = conversationRepository.findById(UUID.fromString(conversationId)).orElseThrow();
        assertThat(conversation.getStatus()).isEqualTo(ChatConversationStatus.OPEN);
    }

    @Test
    @DisplayName("Vai trò RISK (chỉ đọc): đọc được lịch sử nhưng KHÔNG trả lời được")
    void riskRoleCanReadButCannotReply() throws Exception {
        String player = playerBearer(register("chatk"));
        sendAsPlayer(player, "Tin de kiem tra quyen", null);
        String conversationId = myConversationId(player);

        String risk = staffBearer(UserRole.RISK);

        // Đọc được: điều tra gian lận cần đọc nội dung trao đổi.
        mockMvc.perform(get("/api/v1/admin/chat/conversations/" + conversationId + "/messages")
                        .header("Authorization", risk))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));

        // KHÔNG trả lời được: một câu trả lời từ người ngoài bộ phận hỗ trợ vẫn là
        // cam kết của sàn với người chơi.
        mockMvc.perform(post("/api/v1/admin/chat/conversations/" + conversationId + "/messages")
                        .header("Authorization", risk)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"body":"Toi khong duoc phep tra loi"}
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Nhận phụ trách luồng: ghi audit và chèn dòng thông báo hệ thống")
    void assignRecordsAuditAndSystemMessage() throws Exception {
        String player = playerBearer(register("chata"));
        sendAsPlayer(player, "Can nguoi ho tro", null);
        String conversationId = myConversationId(player);

        String staff = staffBearer(UserRole.SUPPORT);
        mockMvc.perform(post("/api/v1/admin/chat/conversations/" + conversationId + "/assign")
                        .header("Authorization", staff))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assignedAdminId").isNotEmpty())
                .andExpect(jsonPath("$.assignedAdminUsername").isNotEmpty());

        List<AuditLog> logs = auditLogRepository.findAll().stream()
                .filter(l -> AuditTrailService.SUPPORT_CHAT_ASSIGNED.equals(l.getAction()))
                .filter(l -> conversationId.equals(l.getTargetId()))
                .toList();
        assertThat(logs).hasSize(1);

        // Dòng SYSTEM nằm đúng vị trí theo thời gian trong luồng, và là khoá dịch
        // (không phải câu đã dịch) để đổi ngôn ngữ thì dòng cũ cũng đổi theo.
        mockMvc.perform(get("/api/v1/chat/messages").header("Authorization", player))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].senderType").value("SYSTEM"))
                .andExpect(jsonPath("$[0].body").value("chat.system.assigned"));

        // Dòng SYSTEM KHÔNG được tính là tin chưa đọc: viên đỏ phải có nghĩa là "có
        // người nói với bạn", nếu nó nhảy vì thông báo tự động thì người dùng sẽ học
        // cách bỏ qua nó.
        mockMvc.perform(get("/api/v1/chat/unread-count").header("Authorization", player))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.messages").value(0));
    }

    @Test
    @DisplayName("Nhận phụ trách hai lần: không tạo thêm dòng thông báo hay bản ghi audit")
    void assignTwiceIsIdempotent() throws Exception {
        String player = playerBearer(register("chati"));
        sendAsPlayer(player, "Tin mo luong", null);
        String conversationId = myConversationId(player);

        String staff = staffBearer(UserRole.SUPPORT);
        for (int i = 0; i < 2; i++) {
            mockMvc.perform(post("/api/v1/admin/chat/conversations/" + conversationId + "/assign")
                            .header("Authorization", staff))
                    .andExpect(status().isOk());
        }

        long assignLogs = auditLogRepository.findAll().stream()
                .filter(l -> AuditTrailService.SUPPORT_CHAT_ASSIGNED.equals(l.getAction()))
                .filter(l -> conversationId.equals(l.getTargetId()))
                .count();
        assertThat(assignLogs).isEqualTo(1);
    }

    @Test
    @DisplayName("Chưa đăng nhập: mọi endpoint chat đều bị chặn")
    void anonymousCannotAccessChat() throws Exception {
        mockMvc.perform(get("/api/v1/chat/conversation")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/chat/messages")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/chat/messages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"body":"khong duoc gui"}
                                """))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Người chơi không được gọi API hộp thư của khu quản trị")
    void playerCannotAccessAdminInbox() throws Exception {
        String player = playerBearer(register("chatx"));
        mockMvc.perform(get("/api/v1/admin/chat/conversations").header("Authorization", player))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Nội dung rỗng bị từ chối ở tầng kiểm tra dữ liệu")
    void blankBodyIsRejected() throws Exception {
        String player = playerBearer(register("chatb"));
        mockMvc.perform(post("/api/v1/chat/messages")
                        .header("Authorization", player)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"body":"   "}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Admin xóa tin nhắn: soft delete thành công, không hiện ở lịch sử chat của admin và player")
    void adminCanDeleteMessagesRealtime() throws Exception {
        String playerUsername = register("chatdel");
        String player = playerBearer(playerUsername);
        JsonNode sentMsg = sendAsPlayer(player, "Tin nhan can xoa", null);
        String msgId = sentMsg.get("id").asText();
        String conversationId = myConversationId(player);

        String staff = staffBearer(UserRole.SUPPORT);

        // 1. Thực hiện xóa tin nhắn
        mockMvc.perform(delete("/api/v1/admin/chat/conversations/" + conversationId + "/messages")
                        .header("Authorization", staff)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"messageIds":["%s"],"confirmPin":"171204"}
                                """.formatted(msgId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deleted").value(1));

        // 2. Player get history -> rỗng vì tin nhắn bị soft-delete
        mockMvc.perform(get("/api/v1/chat/messages").header("Authorization", player))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));

        // 3. Staff get history -> rỗng
        mockMvc.perform(get("/api/v1/admin/chat/conversations/" + conversationId + "/messages")
                        .header("Authorization", staff))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));

        // 4. Audit log có ghi nhận hành động xóa tin nhắn
        List<AuditLog> logs = auditLogRepository.findAll().stream()
                .filter(l -> AuditTrailService.ADMIN_CHAT_MESSAGES_DELETED.equals(l.getAction()))
                .filter(l -> conversationId.equals(l.getTargetId().toString()))
                .toList();
        assertThat(logs).hasSize(1);
    }

        @Test
    @DisplayName("Xóa tin nhắn sai mã PIN bị từ chối")
    void deleteMessagesWrongPin() throws Exception {
        String playerUsername = register("chatwrongpin");
        String player = playerBearer(playerUsername);
        JsonNode sentMsg = sendAsPlayer(player, "Tin nhan de test wrong pin", null);
        String msgId = sentMsg.get("id").asText();
        String conversationId = myConversationId(player);

        String staff = staffBearer(UserRole.SUPPORT);

        mockMvc.perform(delete("/api/v1/admin/chat/conversations/" + conversationId + "/messages")
                        .header("Authorization", staff)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"messageIds":["%s"],"confirmPin":"wrong"}
                                """.formatted(msgId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

@Test
    @DisplayName("Người chơi và vai trò RISK không được quyền xóa tin nhắn")
    void playerAndRiskCannotDeleteMessages() throws Exception {
        String player = playerBearer(register("chatdel2"));
        String conversationId = myConversationId(player);
        String msgId = UUID.randomUUID().toString();

        // Player gọi endpoint xóa tin nhắn -> 403 Forbidden (chỉ admin/staff)
        // Lưu ý: api bắt đầu bằng /api/v1/admin/** nên chỉ admin mới truy cập được qua security config
        mockMvc.perform(delete("/api/v1/admin/chat/conversations/" + conversationId + "/messages")
                        .header("Authorization", player)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"messageIds":["%s"],"confirmPin":"171204"}
                                """.formatted(msgId)))
                .andExpect(status().isForbidden());

        // RISK gọi endpoint xóa -> 403 Forbidden
        // Xem SecurityConfig: DELETE /api/v1/admin/chat/** chỉ cho phép ADMIN/FINANCE/SUPPORT, RISK bị chặn ghi
        // Hãy kiểm tra xem SecurityConfig của dự án có chặn cụ thể DELETE phương thức này không.
        String risk = staffBearer(UserRole.RISK);
        mockMvc.perform(delete("/api/v1/admin/chat/conversations/" + conversationId + "/messages")
                        .header("Authorization", risk)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"messageIds":["%s"],"confirmPin":"171204"}
                                """.formatted(msgId)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Xóa tin nhắn sai định dạng hoặc vượt quá 100 tin nhắn bị từ chối")
    void deleteMessagesValidation() throws Exception {
        String staff = staffBearer(UserRole.SUPPORT);
        String conversationId = UUID.randomUUID().toString();

        // 1. Danh sách trống
        mockMvc.perform(delete("/api/v1/admin/chat/conversations/" + conversationId + "/messages")
                        .header("Authorization", staff)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"messageIds":[],"confirmPin":"171204"}
                                """))
                .andExpect(status().isBadRequest());

        // 2. Vượt quá 100 tin nhắn (dựng list giả lập)
        StringBuilder sb = new StringBuilder();
        sb.append("{\"confirmPin\":\"171204\",\"messageIds\":[");
        for (int i = 0; i < 101; i++) {
            sb.append("\"").append(UUID.randomUUID().toString()).append("\"");
            if (i < 100) sb.append(",");
        }
        sb.append("]}");

        mockMvc.perform(delete("/api/v1/admin/chat/conversations/" + conversationId + "/messages")
                        .header("Authorization", staff)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(sb.toString()))
                .andExpect(status().isBadRequest());
    }
}
