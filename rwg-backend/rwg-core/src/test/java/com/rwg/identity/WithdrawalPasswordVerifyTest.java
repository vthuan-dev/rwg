package com.rwg.identity;

import com.rwg.identity.domain.User;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Kiểm tra endpoint kiểm ngầm mật khẩu rút tiền
 * ({@code POST /api/v1/users/me/withdrawal-password/verify}).
 *
 * Điểm quan trọng nhất được kiểm ở đây KHÔNG phải chuyện đúng/sai mật khẩu, mà là endpoint này
 * DÙNG CHUNG bộ đếm chống dò với {@code POST /api/v1/wallet/withdrawals}. Nếu hai đường có hai
 * bộ đếm riêng thì kẻ tấn công đã chiếm được phiên đăng nhập có gấp đôi số lượt thử mã PIN.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class WithdrawalPasswordVerifyTest {

    private static final String PASSWORD = "MatKhau@12345";
    private static final String WITHDRAWAL_PASSWORD = "123456";
    private static final String WRONG_PASSWORD = "999999";

    /** Khớp `rwg.rate-limit.lock-threshold` trong application-test.yml. */
    private static final int LOCK_THRESHOLD = 10;

    private static final String VERIFY_URL = "/api/v1/users/me/withdrawal-password/verify";

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    UserRepository userRepository;

    private String unique(String prefix) {
        return prefix + UUID.randomUUID().toString().substring(0, 8);
    }

    /** Đăng ký người chơi CÓ đặt mật khẩu rút tiền ngay từ đầu. */
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

    /** Đăng ký người chơi KHÔNG đặt mật khẩu rút tiền. */
    private String registerWithoutWithdrawalPassword(String prefix) throws Exception {
        String username = unique(prefix);
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"%s","password":"%s"}
                                """.formatted(username, PASSWORD)))
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

    private MvcResult verify(String bearer, String password) throws Exception {
        return mockMvc.perform(post(VERIFY_URL)
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"withdrawalPassword":"%s"}
                                """.formatted(password)))
                .andReturn();
    }

    @Test
    @DisplayName("Mật khẩu rút đúng -> valid=true")
    void verifyCorrectPassword() throws Exception {
        String bearer = playerBearer(register("verifyok"));

        mockMvc.perform(post(VERIFY_URL)
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"withdrawalPassword":"%s"}
                                """.formatted(WITHDRAWAL_PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(true));
    }

    @Test
    @DisplayName("Mật khẩu rút sai -> 200 kèm valid=false (KHÔNG phải 401) và số lượt còn lại giảm")
    void verifyWrongPasswordReturnsOkWithFalse() throws Exception {
        String bearer = playerBearer(register("verifybad"));

        // Trả 200 chứ không 401: nếu trả 401 thì lớp gọi API chung của frontend sẽ hiểu là phiên
        // hết hạn và đẩy người chơi về trang đăng nhập ngay giữa lúc họ đang gõ mật khẩu.
        MvcResult result = mockMvc.perform(post(VERIFY_URL)
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"withdrawalPassword":"%s"}
                                """.formatted(WRONG_PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(false))
                .andReturn();

        long remaining = objectMapper.readTree(result.getResponse().getContentAsString())
                .get("attemptsRemaining").asLong();
        assertThat(remaining).isLessThan(LOCK_THRESHOLD);
    }

    @Test
    @DisplayName("Chưa đặt mật khẩu rút -> WITHDRAWAL_PASSWORD_NOT_SET, không báo 'sai mật khẩu'")
    void verifyWhenPasswordNotSet() throws Exception {
        String bearer = playerBearer(registerWithoutWithdrawalPassword("verifynopwd"));

        mockMvc.perform(post(VERIFY_URL)
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"withdrawalPassword":"%s"}
                                """.formatted(WITHDRAWAL_PASSWORD)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("WITHDRAWAL_PASSWORD_NOT_SET"));
    }

    @Test
    @DisplayName("Mật khẩu rỗng -> 400 validation, không tính là một lần thử sai")
    void verifyBlankPasswordIsValidationError() throws Exception {
        String bearer = playerBearer(register("verifyblank"));

        mockMvc.perform(post(VERIFY_URL)
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"withdrawalPassword":""}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Gõ sai qua endpoint kiểm rồi tạo lệnh rút: lệnh rút CŨNG bị khóa "
            + "-> hai đường dùng chung một bộ đếm chống dò")
    void verifyAndWithdrawShareTheSameRateLimitBucket() throws Exception {
        String username = register("verifyshared");
        String bearer = playerBearer(username);
        UUID userId = userRepository.findByUsername(username).orElseThrow().getId();
        assertThat(userId).isNotNull();

        // Đốt hết ngân sách thử BẰNG endpoint kiểm. Vòng lặp dừng ngay khi bị khóa để không phụ
        // thuộc vào việc lần thứ bao nhiêu mới chạm ngưỡng.
        boolean lockedByVerify = false;
        for (int i = 0; i < LOCK_THRESHOLD + 2 && !lockedByVerify; i++) {
            int statusCode = verify(bearer, WRONG_PASSWORD).getResponse().getStatus();
            lockedByVerify = statusCode == 429 || statusCode == 423;
        }
        assertThat(lockedByVerify)
                .as("endpoint kiểm phải bị khóa sau khi gõ sai vượt ngưỡng")
                .isTrue();

        // Lệnh rút với mật khẩu ĐÚNG cũng phải bị từ chối vì bucket đã cạn. Nếu bước này trả
        // 201 nghĩa là hai endpoint dùng hai bucket riêng — đúng lỗ hổng mà test này canh.
        int withdrawStatus = mockMvc.perform(post("/api/v1/wallet/withdrawals")
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"amount":"20","withdrawalPassword":"%s"}
                                """.formatted(WITHDRAWAL_PASSWORD)))
                .andReturn().getResponse().getStatus();

        assertThat(withdrawStatus)
                .as("lệnh rút phải bị khóa theo vì dùng chung bucket với endpoint kiểm")
                .isIn(429, 423);
    }

    @Test
    @DisplayName("Kiểm đúng mật khẩu sau vài lần sai -> reset bộ đếm, lượt thử trở lại đầy")
    void correctVerifyResetsFailureCounter() throws Exception {
        String bearer = playerBearer(register("verifyreset"));

        verify(bearer, WRONG_PASSWORD);
        verify(bearer, WRONG_PASSWORD);

        // Gõ đúng -> reset. Không reset thì người chơi mang theo số lần sai cũ và có thể bị khóa
        // ở lần rút tiền sau đó dù lỗi đã sửa xong.
        MvcResult ok = mockMvc.perform(post(VERIFY_URL)
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"withdrawalPassword":"%s"}
                                """.formatted(WITHDRAWAL_PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(true))
                .andReturn();

        long remaining = objectMapper.readTree(ok.getResponse().getContentAsString())
                .get("attemptsRemaining").asLong();
        assertThat(remaining).isEqualTo(LOCK_THRESHOLD);
    }

    @Test
    @DisplayName("Không có JWT -> 401, endpoint không phải public")
    void verifyRequiresAuthentication() throws Exception {
        mockMvc.perform(post(VERIFY_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"withdrawalPassword":"%s"}
                                """.formatted(WITHDRAWAL_PASSWORD)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Admin reset mật khẩu rút -> endpoint kiểm chuyển sang báo WITHDRAWAL_PASSWORD_NOT_SET")
    void verifyAfterAdminResetsPassword() throws Exception {
        String username = register("verifyafterreset");
        String bearer = playerBearer(username);

        mockMvc.perform(post(VERIFY_URL)
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"withdrawalPassword":"%s"}
                                """.formatted(WITHDRAWAL_PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(true));

        // Xóa hash trực tiếp — tương đương admin bấm reset mật khẩu rút.
        User user = userRepository.findByUsername(username).orElseThrow();
        user.setWithdrawalPasswordHash(null);
        userRepository.save(user);

        mockMvc.perform(post(VERIFY_URL)
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"withdrawalPassword":"%s"}
                                """.formatted(WITHDRAWAL_PASSWORD)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("WITHDRAWAL_PASSWORD_NOT_SET"));
    }
}
