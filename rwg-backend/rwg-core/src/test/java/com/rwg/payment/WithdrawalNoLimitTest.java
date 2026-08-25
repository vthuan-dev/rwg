package com.rwg.payment;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Rút tiền KHÔNG còn trần theo ngày.
 *
 * <h2>VÌ SAO CẦN TEST NÀY</h2>
 * "Bỏ hạn mức" được làm bằng cách để {@code daily-max-amount} rỗng, tức {@code null}
 * trong {@link com.rwg.config.WithdrawalProperties}. Nếu còn chỗ nào gọi
 * {@code dailyMaxAmount()} mà không kiểm {@code null} trước thì sẽ ném
 * {@link NullPointerException} — và nó chỉ xảy ra ĐÚNG LÚC người chơi bấm rút tiền, tức
 * lỗi lộ ra ở chỗ tệ nhất có thể.
 *
 * {@code @TestPropertySource} đặt chuỗi rỗng để mô phỏng đúng {@code
 * RWG_WITHDRAWAL_DAILY_MAX=} ở production, thay vì bỏ hẳn khoá — hai cách này khác nhau
 * và chỉ cách đầu phản ánh cấu hình thật.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "rwg.withdrawal.daily-max-amount=",
        "rwg.withdrawal.min-amount=20"
})
class WithdrawalNoLimitTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    private static final String PASSWORD = "MatKhau@12345";
    private static final String WITHDRAW_PW = "654321";

    private String unique(String prefix) {
        return prefix + UUID.randomUUID().toString().substring(0, 8);
    }

    private String registerAndLogin(String username) throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"%s","password":"%s"}
                                """.formatted(username, PASSWORD)))
                .andExpect(status().isCreated());
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

    /**
     * Nạp tiền qua API deposit (stub auto-success).
     *
     * Gọi nhiều lần khi cần số lớn: mỗi lệnh nạp có trần $50.000
     * ({@code DepositService.MAX_DEPOSIT}) và trần đó KHÔNG bị thay đổi bởi việc bỏ trần
     * rút — hai hạn mức độc lập.
     */
    private void fund(String bearer, String amount) throws Exception {
        mockMvc.perform(post("/api/v1/wallet/deposits")
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"amount":"%s"}
                                """.formatted(amount)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("SUCCESS"));
    }

    private void setWithdrawalPassword(String bearer) throws Exception {
        mockMvc.perform(post("/api/v1/users/me/withdrawal-password")
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"loginPassword":"%s","newWithdrawalPassword":"%s"}
                                """.formatted(PASSWORD, WITHDRAW_PW)))
                .andExpect(status().isOk());
    }

    /** PHẢI gọi sau {@link #setWithdrawalPassword}: liên kết tài khoản nhận tiền đòi xác nhận mật khẩu rút. */
    private void addDefaultBank(String bearer) throws Exception {
        mockMvc.perform(post("/api/v1/wallet/me/bank-accounts")
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"bankCode":"VCB","accountNumber":"0123456789","holderName":"NGUYEN VAN A","withdrawalPassword":"%s"}
                                """.formatted(WITHDRAW_PW)))
                .andExpect(status().isCreated());
    }

    private ResultActions requestWithdrawal(String bearer, String amount) throws Exception {
        return mockMvc.perform(post("/api/v1/wallet/withdrawals")
                .header("Authorization", bearer)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"amount":"%s","withdrawalPassword":"%s"}
                        """.formatted(amount, WITHDRAW_PW)));
    }

    /** Người chơi đã nạp tiền, đặt mật khẩu rút và liên kết tài khoản nhận. */
    private String readyPlayer(String prefix, String funding) throws Exception {
        String bearer = registerAndLogin(unique(prefix));
        fund(bearer, funding);
        setWithdrawalPassword(bearer);
        addDefaultBank(bearer);
        return bearer;
    }

    @Test
    void limitsEndpointReportsNoDailyMax() throws Exception {
        // KHÔNG gửi token: endpoint khai permitAll. Nếu ai đó vô tình đưa nó vào khu cần
        // xác thực thì test đổ ở đây, trước khi giao diện gặp 401 lúc chạy thật.
        MvcResult result = mockMvc.perform(get("/api/v1/payments/limits"))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(json.get("withdrawDailyMax").isNull())
                .as("withdrawDailyMax phải là null khi không áp trần")
                .isTrue();
        assertThat(json.get("withdrawMin").asText()).isEqualTo("20");
        assertThat(json.get("depositMin").asText()).isEqualTo("10");
        assertThat(json.get("depositMax").asText()).isEqualTo("50000");
    }

    @Test
    void withdrawAboveOldCapSucceeds() throws Exception {
        String bearer = readyPlayer("nolimit", "50000");

        // 20.000 — gấp bốn lần trần cũ 5.000. Trước khi sửa, lệnh này nhận 400 với mã
        // validation.withdrawal.amount.max ngay trong parseAmount.
        requestWithdrawal(bearer, "20000")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    void multipleWithdrawalsAboveOldCapSucceed() throws Exception {
        String bearer = readyPlayer("nolimit2", "50000");

        // Ba lệnh 4.000 = 12.000. Từng lệnh dưới trần cũ nhưng TỔNG thì vượt, nên trước
        // khi sửa lệnh thứ hai đã nhận WITHDRAWAL_LIMIT_EXCEEDED.
        for (int i = 0; i < 3; i++) {
            requestWithdrawal(bearer, "4000").andExpect(status().isCreated());
        }
    }

    @Test
    void minimumStillEnforced() throws Exception {
        String bearer = readyPlayer("nolimit3", "1000");

        // Bỏ trần KHÔNG có nghĩa bỏ hết ràng buộc. Mỗi lệnh rút cần admin bấm duyệt, nên
        // cho rút $5 là mở đường tạo hàng nghìn lệnh làm ngập việc của nhân sự.
        requestWithdrawal(bearer, "5").andExpect(status().isBadRequest());
    }

    @Test
    void insufficientBalanceStillBlocked() throws Exception {
        String bearer = readyPlayer("nolimit4", "100");

        // Ràng buộc THẬT sự quan trọng: không ai rút được nhiều hơn số mình có. Bỏ trần
        // ngày không được phép làm suy yếu điều này.
        requestWithdrawal(bearer, "500").andExpect(status().is4xxClientError());
    }
}
