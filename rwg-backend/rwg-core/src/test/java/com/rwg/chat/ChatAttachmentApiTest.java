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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Kiểm tra tích hợp tính năng gửi ảnh trong chat hỗ trợ.
 *
 * Trọng tâm là những chỗ mà một lỗi sẽ âm thầm gây hại thật:
 * - tệp không phải ảnh nhưng đổi tên thành .jpg vẫn bị chặn (xác thực theo NỘI DUNG),
 * - người chơi A không xem được ảnh của người chơi B,
 * - đường dẫn do client tự bịa không vào được tin nhắn,
 * - RISK không gửi được ảnh nhưng VẪN xem được (đúng thiết kế vai trò chỉ đọc).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ChatAttachmentApiTest {

    private static final String PASSWORD = "MatKhau@12345";
    private static final String WITHDRAWAL_PASSWORD = "123456";

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

    /**
     * Một tệp PNG hợp lệ ở mức nhận dạng được.
     *
     * Chỉ cần 8 byte chữ ký PNG rồi phần đệm: {@code MediaStorageService} đọc 12 byte
     * đầu để nhận dạng và KHÔNG giải mã ảnh (giải mã sẽ mở đường cho decompression
     * bomb). Nên không cần dựng một ảnh PNG đúng chuẩn hoàn chỉnh chỉ để test.
     */
    private static byte[] pngBytes(int totalSize) {
        byte[] data = new byte[Math.max(totalSize, 12)];
        byte[] signature = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A};
        System.arraycopy(signature, 0, data, 0, signature.length);
        return data;
    }

    private MockMultipartFile pngFile(String filename, int size) {
        return new MockMultipartFile("file", filename, "image/png", pngBytes(size));
    }

    /** Tải một ảnh lên với tư cách người chơi, trả về nội dung phản hồi. */
    private JsonNode uploadAsPlayer(String bearer, MockMultipartFile file) throws Exception {
        return json(mockMvc.perform(multipart("/api/v1/chat/attachments")
                        .file(file)
                        .header("Authorization", bearer))
                .andExpect(status().isOk())
                .andReturn());
    }

    /** Gửi một tin kèm ảnh, trả về nội dung phản hồi. */
    private MvcResult sendWithAttachment(String bearer, String body, JsonNode uploaded)
            throws Exception {
        return mockMvc.perform(post("/api/v1/chat/messages")
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"body":"%s","attachmentUrl":"%s","attachmentName":"%s","attachmentSize":%d}
                                """.formatted(body, uploaded.get("url").asText(),
                                uploaded.get("name").asText(), uploaded.get("size").asLong())))
                .andReturn();
    }

    /** Tên tệp trần trong một đường dẫn đính kèm. */
    private static String filenameOf(String attachmentUrl) {
        return attachmentUrl.substring(attachmentUrl.lastIndexOf('/') + 1);
    }

    // ===== test =====

    @Test
    @DisplayName("Người chơi gửi ảnh kèm chữ: tin lưu đủ 4 trường đính kèm")
    void playerSendsImageWithText() throws Exception {
        String player = playerBearer(register("attp"));

        JsonNode uploaded = uploadAsPlayer(player, pngFile("bien-lai.png", 2048));
        assertThat(uploaded.get("url").asText()).startsWith("/api/v1/chat/attachments/");
        assertThat(uploaded.get("name").asText()).isEqualTo("bien-lai.png");
        assertThat(uploaded.get("size").asLong()).isEqualTo(2048);
        assertThat(uploaded.get("type").asText()).isEqualTo("IMAGE");

        MvcResult result = sendWithAttachment(player, "Day la bien lai cua toi", uploaded);
        assertThat(result.getResponse().getStatus()).isEqualTo(200);

        JsonNode sent = json(result);
        assertThat(sent.get("body").asText()).isEqualTo("Day la bien lai cua toi");
        assertThat(sent.get("attachmentUrl").asText()).isEqualTo(uploaded.get("url").asText());
        assertThat(sent.get("attachmentType").asText()).isEqualTo("IMAGE");
        assertThat(sent.get("attachmentName").asText()).isEqualTo("bien-lai.png");
        assertThat(sent.get("attachmentSize").asLong()).isEqualTo(2048);
    }

    @Test
    @DisplayName("Gửi ảnh KHÔNG kèm chữ: được phép, và hộp thư hiện khoá chat.preview.image")
    void playerSendsImageWithoutText() throws Exception {
        String player = playerBearer(register("attp"));
        JsonNode uploaded = uploadAsPlayer(player, pngFile("anh.png", 1024));

        // body rỗng hoàn toàn — đây là trường hợp phổ biến nhất của tính năng.
        MvcResult result = mockMvc.perform(post("/api/v1/chat/messages")
                        .header("Authorization", player)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"attachmentUrl":"%s","attachmentName":"anh.png","attachmentSize":1024}
                                """.formatted(uploaded.get("url").asText())))
                .andExpect(status().isOk())
                .andReturn();

        assertThat(json(result).get("attachmentUrl").asText())
                .isEqualTo(uploaded.get("url").asText());

        // Hộp thư quản trị phải hiện KHOÁ DỊCH thay vì một dòng trống.
        String staff = staffBearer(UserRole.SUPPORT);
        JsonNode inbox = json(mockMvc.perform(get("/api/v1/admin/chat/conversations")
                        .header("Authorization", staff))
                .andExpect(status().isOk())
                .andReturn());

        assertThat(inbox.get("content").get(0).get("lastMessagePreview").asText())
                .isEqualTo("chat.preview.image");
    }

    @Test
    @DisplayName("Tin không chữ không ảnh: bị từ chối")
    void emptyMessageRejected() throws Exception {
        String player = playerBearer(register("attp"));

        mockMvc.perform(post("/api/v1/chat/messages")
                        .header("Authorization", player)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Tệp thực thi đổi tên thành .png: bị chặn theo NỘI DUNG, không theo đuôi tệp")
    void executableRenamedToPngRejected() throws Exception {
        String player = playerBearer(register("attp"));

        // "MZ" là chữ ký của tệp thực thi Windows. Đuôi và Content-Type đều khai là ảnh —
        // đúng những gì kẻ tấn công kiểm soát được — nên chỉ có việc đọc byte đầu mới
        // phát hiện ra.
        ByteArrayOutputStream payload = new ByteArrayOutputStream();
        payload.write("MZ".getBytes(StandardCharsets.US_ASCII));
        payload.write(new byte[510]);

        mockMvc.perform(multipart("/api/v1/chat/attachments")
                        .file(new MockMultipartFile("file", "virus.png", "image/png",
                                payload.toByteArray()))
                        .header("Authorization", player))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Đuôi tệp không được phép (.gif): bị chặn")
    void disallowedExtensionRejected() throws Exception {
        String player = playerBearer(register("attp"));

        mockMvc.perform(multipart("/api/v1/chat/attachments")
                        .file(new MockMultipartFile("file", "anh.gif", "image/gif", pngBytes(512)))
                        .header("Authorization", player))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Tệp vượt trần dung lượng: bị chặn kèm mức tối đa trong details")
    void oversizedFileRejected() throws Exception {
        String player = playerBearer(register("attp"));

        // 10MB + 1 byte: vượt đúng một byte để chắc chắn so sánh là > chứ không phải >=.
        MvcResult result = mockMvc.perform(multipart("/api/v1/chat/attachments")
                        .file(pngFile("qua-lon.png", 10 * 1024 * 1024 + 1))
                        .header("Authorization", player))
                .andExpect(status().isBadRequest())
                .andReturn();

        assertThat(json(result).get("details").get("maxSizeBytes").asLong())
                .isEqualTo(10L * 1024 * 1024);
    }

    @Test
    @DisplayName("Client bịa đường dẫn ngoài hệ thống: bị từ chối")
    void forgedAttachmentUrlRejected() throws Exception {
        String player = playerBearer(register("attp"));

        // Không có bước kiểm tra này thì tin nhắn hiện một ảnh từ máy chủ của kẻ tấn
        // công, dưới danh nghĩa nội dung trong hệ thống.
        mockMvc.perform(post("/api/v1/chat/messages")
                        .header("Authorization", player)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"body":"xem anh nay","attachmentUrl":"https://trang-lua-dao.example/a.jpg"}
                                """))
                .andExpect(status().isBadRequest());

        // Đường dẫn có tiền tố đúng nhưng thoát ra ngoài thư mục cũng phải bị chặn.
        mockMvc.perform(post("/api/v1/chat/messages")
                        .header("Authorization", player)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"body":"x","attachmentUrl":"/api/v1/chat/attachments/../../etc/passwd"}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Người chơi xem được ảnh của mình, KHÔNG xem được ảnh của người chơi khác")
    void playerCannotViewAnotherPlayersAttachment() throws Exception {
        String playerA = playerBearer(register("atta"));
        String playerB = playerBearer(register("attb"));

        JsonNode uploaded = uploadAsPlayer(playerA, pngFile("rieng-tu.png", 1024));
        sendWithAttachment(playerA, "anh rieng cua toi", uploaded);

        String path = uploaded.get("url").asText();

        // Chủ sở hữu: xem được.
        mockMvc.perform(get(path).header("Authorization", playerA))
                .andExpect(status().isOk());

        // Người chơi khác: 404 chứ không phải 403 — 403 xác nhận tệp đó tồn tại, và đó
        // là thông tin có giá trị với người đang dò đường dẫn.
        mockMvc.perform(get(path).header("Authorization", playerB))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Ảnh đã tải lên nhưng CHƯA gắn vào tin nào: chính người tải cũng chưa xem được")
    void unattachedImageNotViewableYet() throws Exception {
        String player = playerBearer(register("attp"));
        JsonNode uploaded = uploadAsPlayer(player, pngFile("chua-gui.png", 1024));

        // Quyền xem được suy ra từ TIN NHẮN chứa ảnh, không phải từ việc ai đã tải lên —
        // server không lưu trạng thái "đã upload nhưng chưa gửi" ở đâu cả. Hệ quả này
        // được ghi nhận rõ ở đây để một lần sửa sau không tưởng là lỗi: giao diện hiện
        // ảnh xem trước từ blob cục bộ, nên người dùng không bao giờ chạm vào trường hợp
        // này.
        mockMvc.perform(get(uploaded.get("url").asText()).header("Authorization", player))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Nhân sự xem được ảnh của MỌI luồng, kể cả vai trò RISK chỉ đọc")
    void staffCanViewAnyAttachment() throws Exception {
        String player = playerBearer(register("attp"));
        JsonNode uploaded = uploadAsPlayer(player, pngFile("bien-lai.png", 1024));
        sendWithAttachment(player, "bien lai nap tien", uploaded);

        String path = uploaded.get("url").asText();

        for (UserRole role : new UserRole[]{UserRole.SUPPORT, UserRole.ADMIN, UserRole.RISK}) {
            mockMvc.perform(get(path).header("Authorization", staffBearer(role)))
                    .andExpect(status().isOk());
        }
    }

    @Test
    @DisplayName("RISK KHÔNG tải ảnh lên được (vai trò chỉ đọc), SUPPORT thì được")
    void riskCannotUploadButSupportCan() throws Exception {
        // Vai trò chỉ đọc không được thay mặt sàn gửi nội dung cho người chơi. Endpoint
        // nằm dưới POST /api/v1/admin/chat/** nên matcher sẵn có đã loại RISK — test này
        // giữ cho một lần đổi đường dẫn sau này không âm thầm mở lại quyền đó.
        mockMvc.perform(multipart("/api/v1/admin/chat/attachments")
                        .file(pngFile("huong-dan.png", 1024))
                        .header("Authorization", staffBearer(UserRole.RISK)))
                .andExpect(status().isForbidden());

        mockMvc.perform(multipart("/api/v1/admin/chat/attachments")
                        .file(pngFile("huong-dan.png", 1024))
                        .header("Authorization", staffBearer(UserRole.SUPPORT)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Nhân sự trả lời kèm ảnh: người chơi nhận được đủ thông tin đính kèm")
    void staffRepliesWithImage() throws Exception {
        String username = register("attp");
        String player = playerBearer(username);
        String staff = staffBearer(UserRole.SUPPORT);

        // Người chơi mở luồng trước — nhân sự chỉ trả lời được vào luồng đã tồn tại.
        mockMvc.perform(post("/api/v1/chat/messages")
                        .header("Authorization", player)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"body":"toi can huong dan"}
                                """))
                .andExpect(status().isOk());

        String conversationId = json(mockMvc.perform(get("/api/v1/chat/conversation")
                        .header("Authorization", player))
                .andExpect(status().isOk())
                .andReturn())
                .get("id").asText();

        JsonNode uploaded = json(mockMvc.perform(multipart("/api/v1/admin/chat/attachments")
                        .file(pngFile("huong-dan.png", 4096))
                        .header("Authorization", staff))
                .andExpect(status().isOk())
                .andReturn());

        mockMvc.perform(post("/api/v1/admin/chat/conversations/" + conversationId + "/messages")
                        .header("Authorization", staff)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"body":"Anh xem huong dan nay","attachmentUrl":"%s","attachmentName":"huong-dan.png","attachmentSize":4096}
                                """.formatted(uploaded.get("url").asText())))
                .andExpect(status().isOk());

        JsonNode messages = json(mockMvc.perform(get("/api/v1/chat/messages")
                        .header("Authorization", player))
                .andExpect(status().isOk())
                .andReturn());

        // Tin mới nhất trước (phân trang theo mốc thời gian).
        JsonNode staffMessage = messages.get(0);
        assertThat(staffMessage.get("senderType").asText()).isEqualTo("STAFF");
        assertThat(staffMessage.get("attachmentUrl").asText())
                .isEqualTo(uploaded.get("url").asText());
        assertThat(staffMessage.get("attachmentType").asText()).isEqualTo("IMAGE");

        // Người chơi phải xem được ảnh nhân sự gửi vào luồng CỦA MÌNH.
        mockMvc.perform(get(uploaded.get("url").asText()).header("Authorization", player))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Không có token: không xem được ảnh")
    void anonymousCannotViewAttachment() throws Exception {
        String player = playerBearer(register("attp"));
        JsonNode uploaded = uploadAsPlayer(player, pngFile("anh.png", 1024));
        sendWithAttachment(player, "anh", uploaded);

        mockMvc.perform(get(uploaded.get("url").asText()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Tên tệp bịa không tồn tại: 404, không lộ thông tin gì")
    void unknownFilenameNotFound() throws Exception {
        String player = playerBearer(register("attp"));

        mockMvc.perform(get("/api/v1/chat/attachments/" + UUID.randomUUID() + ".png")
                        .header("Authorization", player))
                .andExpect(status().isNotFound());
    }
}
