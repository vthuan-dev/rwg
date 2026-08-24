package com.rwg.bank;

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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * MỖI NGƯỜI CHƠI CHỈ MỘT TÀI KHOẢN NGÂN HÀNG, và không tự gỡ được — muốn đổi thì
 * liên hệ CSKH để admin làm hộ.
 *
 * VÌ SAO CÓ LUẬT NÀY: đổi được số tài khoản nhận tiền là chuyển được toàn bộ tiền rút
 * của nạn nhân sang chỗ khác. Đây là thao tác nhạy cảm nhất sau khi ai đó chiếm được
 * phiên đăng nhập đang mở. Để một người thật xác nhận qua chat an toàn hơn là tin vào
 * mật khẩu cấp hai — mật khẩu cũng có thể bị lấy cùng lúc với phiên.
 *
 * Test class này cũng canh MÃ TRẠNG THÁI HTTP của lỗi sai mật khẩu rút tiền. Trước đây
 * chỗ đó trả 401, và frontend hiểu 401 là "phiên hết hạn" nên đăng xuất người dùng —
 * gõ sai mật khẩu thì bị văng ra trang đăng nhập. Xem {@code wrongWithdrawalPasswordIsBadRequestNotUnauthorized}.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class BankAccountRestrictionTest {

    private static final String PASSWORD = "MatKhau@12345";
    private static final String WITHDRAWAL_PASSWORD = "123456";
    private static final String WRONG_WITHDRAWAL_PASSWORD = "999999";

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    UserRepository userRepository;

    // ===== helpers =====

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
        return "Bearer " + objectMapper.readTree(result.getResponse().getContentAsString())
                .get("accessToken").asText();
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
        return "Bearer " + objectMapper.readTree(result.getResponse().getContentAsString())
                .get("accessToken").asText();
    }

    /** Thêm tài khoản bằng đường của NGƯỜI CHƠI, trả về id. */
    private String addBankAsPlayer(String bearer, String accountNumber) throws Exception {
        MvcResult created = mockMvc.perform(post("/api/v1/wallet/me/bank-accounts")
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"bankCode":"VCB","accountNumber":"%s","holderName":"NGUYEN VAN A","withdrawalPassword":"%s"}
                                """.formatted(accountNumber, WITHDRAWAL_PASSWORD)))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asText();
    }

    // ===== luật một tài khoản =====

    @Test
    @DisplayName("Tài khoản THỨ HAI bị chặn 409 — người chơi chỉ liên kết được một")
    void secondBankAccountIsRejected() throws Exception {
        String bearer = playerBearer(register("onebank"));
        addBankAsPlayer(bearer, "0123456789");

        mockMvc.perform(post("/api/v1/wallet/me/bank-accounts")
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"bankCode":"TCB","accountNumber":"9876543210","holderName":"NGUYEN VAN A","withdrawalPassword":"%s"}
                                """.formatted(WITHDRAWAL_PASSWORD)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("BANK_ACCOUNT_ALREADY_LINKED"));
    }

    @Test
    @DisplayName("Người chơi TỰ gỡ bị chặn 409 — chặn ở tầng service, không chỉ ẩn nút")
    void playerCannotRemoveOwnBankAccount() throws Exception {
        String bearer = playerBearer(register("noremove"));
        String bankId = addBankAsPlayer(bearer, "0123456789");

        // Gọi THẲNG endpoint, bỏ qua giao diện. Ẩn nút trên trang là để người dùng khỏi
        // bối rối, không phải biện pháp bảo vệ — ai mở DevTools cũng gọi được đường này.
        mockMvc.perform(delete("/api/v1/wallet/me/bank-accounts/" + bankId)
                        .header("Authorization", bearer))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("BANK_ACCOUNT_REMOVE_FORBIDDEN"));

        // Tài khoản PHẢI còn nguyên: nếu service xoá rồi mới ném lỗi thì người chơi vừa
        // thấy báo lỗi vừa mất tài khoản.
        mockMvc.perform(get("/api/v1/wallet/me/bank-accounts")
                        .header("Authorization", bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    // ===== mã lỗi mật khẩu rút tiền =====

    @Test
    @DisplayName("Sai mật khẩu rút tiền -> 400, KHÔNG PHẢI 401 (401 làm frontend đăng xuất người dùng)")
    void wrongWithdrawalPasswordIsBadRequestNotUnauthorized() throws Exception {
        String bearer = playerBearer(register("wrongpw"));

        mockMvc.perform(post("/api/v1/wallet/me/bank-accounts")
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"bankCode":"VCB","accountNumber":"0123456789","holderName":"NGUYEN VAN A","withdrawalPassword":"%s"}
                                """.formatted(WRONG_WITHDRAWAL_PASSWORD)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("WITHDRAWAL_PASSWORD_MISMATCH"));
    }

    @Test
    @DisplayName("Đã có tài khoản + sai mật khẩu -> báo lỗi MẬT KHẨU, không tiết lộ đã liên kết hay chưa")
    void passwordIsCheckedBeforeExistenceToAvoidLeakingState() throws Exception {
        String bearer = playerBearer(register("orderpw"));
        addBankAsPlayer(bearer, "0123456789");

        // THỨ TỰ KIỂM LÀ MỘT QUYẾT ĐỊNH BẢO MẬT: nếu kiểm "đã có tài khoản" trước, thì
        // bất kỳ ai chiếm được phiên cũng dò được nạn nhân đã liên kết tài khoản hay chưa
        // mà KHÔNG cần biết mật khẩu rút tiền — hai thông báo lỗi khác nhau là một kênh
        // rò rỉ thông tin. Test này khoá thứ tự đó.
        mockMvc.perform(post("/api/v1/wallet/me/bank-accounts")
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"bankCode":"TCB","accountNumber":"9876543210","holderName":"NGUYEN VAN A","withdrawalPassword":"%s"}
                                """.formatted(WRONG_WITHDRAWAL_PASSWORD)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("WITHDRAWAL_PASSWORD_MISMATCH"));
    }

    // ===== admin làm hộ =====

    @Test
    @DisplayName("ADMIN thêm hộ được, KHÔNG cần mật khẩu rút tiền của người chơi")
    void adminCanAddBankAccountForPlayer() throws Exception {
        String username = register("adminadd");
        UUID userId = userRepository.findByUsername(username).orElseThrow().getId();
        String adminBearer = staffBearer(UserRole.ADMIN);

        mockMvc.perform(post("/api/v1/admin/users/" + userId + "/payout-methods")
                        .header("Authorization", adminBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"bankCode":"VCB","accountNumber":"0123456789","holderName":"NGUYEN VAN A","reason":"khach bao sai so qua chat"}
                                """))
                .andExpect(status().isCreated())
                // Response CHỈ có dạng đã che — endpoint này không phải đường thứ hai để
                // xem số đầy đủ mà không ghi audit reveal.
                .andExpect(jsonPath("$.maskedAccountNumber").value("****6789"))
                .andExpect(jsonPath("$.isDefault").value(true));

        // Người chơi thấy tài khoản admin vừa thêm.
        mockMvc.perform(get("/api/v1/wallet/me/bank-accounts")
                        .header("Authorization", playerBearer(username)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    @DisplayName("ADMIN gỡ rồi thêm lại được — đây là đường DUY NHẤT để đổi tài khoản")
    void adminCanRemoveThenAddAgain() throws Exception {
        String username = register("adminswap");
        UUID userId = userRepository.findByUsername(username).orElseThrow().getId();
        String playerBearer = playerBearer(username);
        String bankId = addBankAsPlayer(playerBearer, "0123456789");
        String adminBearer = staffBearer(UserRole.ADMIN);

        mockMvc.perform(delete("/api/v1/admin/users/" + userId + "/payout-methods/" + bankId)
                        .header("Authorization", adminBearer)
                        .param("reason", "khach doi sang ngan hang khac"))
                .andExpect(status().isNoContent());

        // Gỡ xong thì thêm lại được — chứng minh cờ default của bản ghi đã gỡ không còn
        // chặn bản ghi mới, và người chơi không bị kẹt.
        mockMvc.perform(post("/api/v1/admin/users/" + userId + "/payout-methods")
                        .header("Authorization", adminBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"bankCode":"TCB","accountNumber":"9876543210","holderName":"NGUYEN VAN A","reason":"so moi khach cung cap"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.maskedAccountNumber").value("****3210"));

        // Người chơi chỉ còn thấy MỘT tài khoản đang hoạt động — cái mới.
        mockMvc.perform(get("/api/v1/wallet/me/bank-accounts")
                        .header("Authorization", playerBearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].maskedAccountNumber").value("****3210"));
    }

    @Test
    @DisplayName("ADMIN thêm khi ĐÃ có tài khoản -> 409, luật một-tài-khoản là luật của dữ liệu")
    void adminCannotAddSecondAccountEither() throws Exception {
        String username = register("adminsecond");
        UUID userId = userRepository.findByUsername(username).orElseThrow().getId();
        addBankAsPlayer(playerBearer(username), "0123456789");
        String adminBearer = staffBearer(UserRole.ADMIN);

        // Nếu admin thêm được cái thứ hai thì lúc rút tiền có HAI ứng viên default và kết
        // quả phụ thuộc thứ tự trả về của DB — tiền đi sai chỗ một cách không xác định.
        mockMvc.perform(post("/api/v1/admin/users/" + userId + "/payout-methods")
                        .header("Authorization", adminBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"bankCode":"TCB","accountNumber":"9876543210","holderName":"NGUYEN VAN A","reason":"them cai thu hai"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("BANK_ACCOUNT_ALREADY_LINKED"));
    }

    @Test
    @DisplayName("Thiếu lý do -> 400, nhật ký phải trả lời được câu hỏi VÌ SAO")
    void reasonIsRequiredForAdminAdd() throws Exception {
        String username = register("adminnoreason");
        UUID userId = userRepository.findByUsername(username).orElseThrow().getId();
        String adminBearer = staffBearer(UserRole.ADMIN);

        mockMvc.perform(post("/api/v1/admin/users/" + userId + "/payout-methods")
                        .header("Authorization", adminBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"bankCode":"VCB","accountNumber":"0123456789","holderName":"NGUYEN VAN A","reason":""}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("userId không tồn tại -> 404, không tạo tài khoản mồ côi")
    void adminAddForUnknownUserIsNotFound() throws Exception {
        String adminBearer = staffBearer(UserRole.ADMIN);

        // bank_accounts KHÔNG có khoá ngoại sang users, nên gõ sai một ký tự trong UUID sẽ
        // tạo bản ghi mồ côi mà không báo gì. Service phải tự kiểm.
        mockMvc.perform(post("/api/v1/admin/users/" + UUID.randomUUID() + "/payout-methods")
                        .header("Authorization", adminBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"bankCode":"VCB","accountNumber":"0123456789","holderName":"NGUYEN VAN A","reason":"user khong ton tai"}
                                """))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Gỡ qua đường dẫn của user KHÁC -> 404, methodId một mình không đủ")
    void adminRemoveThroughAnotherUserPathIsNotFound() throws Exception {
        String ownerUsername = register("bankowner");
        String bankId = addBankAsPlayer(playerBearer(ownerUsername), "0123456789");

        String otherUsername = register("bankother");
        UUID otherUserId = userRepository.findByUsername(otherUsername).orElseThrow().getId();
        String adminBearer = staffBearer(UserRole.ADMIN);

        // Thiếu bước lọc theo userId thì một methodId bất kỳ gỡ được qua URL của user nào
        // cũng xong — quyền kiểm tra sẽ chỉ là hình thức.
        mockMvc.perform(delete("/api/v1/admin/users/" + otherUserId + "/payout-methods/" + bankId)
                        .header("Authorization", adminBearer)
                        .param("reason", "thu go qua duong dan sai"))
                .andExpect(status().isNotFound());
    }

    // ===== phân quyền =====

    @Test
    @DisplayName("SUPPORT bị chặn 403 khi thêm — đổi tài khoản nhận tiền là quyền nặng nhất")
    void supportCannotAddBankAccount() throws Exception {
        String username = register("supportadd");
        UUID userId = userRepository.findByUsername(username).orElseThrow().getId();
        String supportBearer = staffBearer(UserRole.SUPPORT);

        // SUPPORT là người trực chat và nhận yêu cầu đổi tài khoản từ khách, nên rất dễ
        // nghĩ là nên cho họ quyền này. Nhưng đổi được số tài khoản nhận tiền là chuyển
        // được tiền của người khác vào tài khoản mình — nặng hơn cả xem số đầy đủ.
        mockMvc.perform(post("/api/v1/admin/users/" + userId + "/payout-methods")
                        .header("Authorization", supportBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"bankCode":"VCB","accountNumber":"0123456789","holderName":"NGUYEN VAN A","reason":"support thu them"}
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("SUPPORT bị chặn 403 khi gỡ — matcher DELETE phải đứng trước rule chung")
    void supportCannotRemoveBankAccount() throws Exception {
        String username = register("supportdel");
        UUID userId = userRepository.findByUsername(username).orElseThrow().getId();
        String bankId = addBankAsPlayer(playerBearer(username), "0123456789");
        String supportBearer = staffBearer(UserRole.SUPPORT);

        // Test RIÊNG cho DELETE, không gộp với POST: hai method dùng hai matcher khác nhau
        // trong SecurityConfig. Chỉ kiểm POST thì một matcher DELETE viết thiếu vẫn lọt,
        // và Spring lấy matcher KHỚP ĐẦU TIÊN nên nó sẽ rơi vào rule chung /admin/**
        // cho phép cả bốn vai trò.
        mockMvc.perform(delete("/api/v1/admin/users/" + userId + "/payout-methods/" + bankId)
                        .header("Authorization", supportBearer)
                        .param("reason", "support thu go"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("FINANCE thêm được — cùng nhóm được phép chạm tiền với ADMIN")
    void financeCanAddBankAccount() throws Exception {
        String username = register("financeadd");
        UUID userId = userRepository.findByUsername(username).orElseThrow().getId();
        String financeBearer = staffBearer(UserRole.FINANCE);

        mockMvc.perform(post("/api/v1/admin/users/" + userId + "/payout-methods")
                        .header("Authorization", financeBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"bankCode":"VCB","accountNumber":"0123456789","holderName":"NGUYEN VAN A","reason":"finance them ho"}
                                """))
                .andExpect(status().isCreated());

        assertThat(userId).isNotNull();
    }
}
