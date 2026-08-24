package com.rwg.admin;

import com.rwg.identity.domain.User;
import com.rwg.identity.domain.UserRole;
import com.rwg.identity.repository.UserRepository;
import com.rwg.wallet.domain.WalletRefType;
import com.rwg.wallet.repository.WalletRepository;
import com.rwg.wallet.repository.WalletTransactionRepository;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Điều chỉnh ví thủ công phía quản trị — KHÔNG CÒN HẠN MỨC SỐ TIỀN.
 *
 * Thay cho {@code AdminMakerCheckerTest} đã xoá. Lớp test cũ canh quy trình 4 mắt và
 * hai trần số tiền (trần mỗi lần $1.000 và trần tổng $10.000/admin/ngày); cả ba đã bỏ
 * theo yêu cầu vận hành nên những bất biến đó không còn tồn tại để kiểm.
 *
 * BA BẤT BIẾN CÒN LẠI, và đây là lý do lớp test này tồn tại:
 *
 * 1. SỐ TIỀN LỚN THỰC THI NGAY. Trước đây khoản vượt $1.000 trả 202 và KHÔNG chuyển
 *    tiền. Test dưới đây nạp $50.000 và kiểm số dư thật trong DB — nếu nhánh 4 mắt bị
 *    khôi phục do merge sai, response vẫn là 2xx nhưng số dư sẽ không đổi, và chỉ có
 *    assert vào DB mới bắt được.
 *
 * 2. ĐÚNG MỘT DÒNG LEDGER mỗi lần điều chỉnh. Đây là điều kiện để job đối soát
 *    (SUM(credit) - SUM(debit) vs wallets.balance) không báo lệch vì thao tác admin.
 *
 * 3. ADMIN KHÔNG TỰ ĐIỀU CHỈNH VÍ CỦA MÌNH. Chốt này CỐ TÌNH giữ lại khi bỏ 4 mắt: nó
 *    không cản việc nạp cho khách bao nhiêu cũng được, nhưng ngăn kịch bản một admin tự
 *    cộng tiền cho mình rồi tự rút ra — loại thất thoát khó truy trách nhiệm nhất.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminWalletAdjustTest {

    private static final String PASSWORD = "MatKhau@12345";

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    UserRepository userRepository;

    @Autowired
    WalletRepository walletRepository;

    @Autowired
    WalletTransactionRepository transactionRepository;

    // ===== helper =====

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

    /** Nhân sự với vai trò chỉ định; trả về cặp (bearer, userId) vì test tự giao dịch cần cả hai. */
    private record Staff(String bearer, UUID id) {
    }

    private Staff staff(UserRole role) throws Exception {
        String username = unique("adj");
        registerAndLogin(username);
        User user = userRepository.findByUsername(username).orElseThrow();
        user.setRole(role);
        userRepository.save(user);
        return new Staff("Bearer " + login(username).get("accessToken").asText(), user.getId());
    }

    /** Người chơi có ví $200. */
    private UUID fundedPlayerId() throws Exception {
        String username = unique("adjtarget");
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

    private BigDecimal balanceOf(UUID userId) {
        return walletRepository.findByUserId(userId)
                .map(wallet -> wallet.getBalance())
                .orElse(BigDecimal.ZERO);
    }

    private long adjustmentRows(UUID userId) {
        return walletRepository.findByUserId(userId)
                .map(wallet -> transactionRepository.countByWalletIdAndRefType(
                        wallet.getId(), WalletRefType.ADJUSTMENT))
                .orElse(0L);
    }

    private JsonNode adjust(String bearer, UUID userId, String direction, String amount,
                            int expectedStatus) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/admin/users/" + userId + "/wallet/adjust")
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"direction":"%s","amount":"%s","reason":"kiem thu dieu chinh"}
                                """.formatted(direction, amount)))
                .andExpect(status().is(expectedStatus))
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    // ===== test =====

    @Test
    @DisplayName("Số tiền LỚN vẫn thực thi ngay: 200, tiền chuyển, đúng 1 dòng ledger")
    void largeAmountExecutesImmediately() throws Exception {
        Staff admin = staff(UserRole.ADMIN);
        UUID playerId = fundedPlayerId();

        // $50.000 — gấp 50 lần trần mỗi lần cũ và gấp 5 lần trần ngày cũ.
        JsonNode body = adjust(admin.bearer(), playerId, "CREDIT", "50000", 200);

        assertThat(new BigDecimal(body.get("balanceAfter").asString()))
                .isEqualByComparingTo("50200");
        // Assert vào DB, KHÔNG chỉ vào response: nếu nhánh 4 mắt bị khôi phục thì
        // response vẫn 2xx nhưng số dư sẽ đứng nguyên ở 200.
        assertThat(balanceOf(playerId)).isEqualByComparingTo("50200");
        assertThat(adjustmentRows(playerId)).isEqualTo(1);
    }

    @Test
    @DisplayName("Nạp nhiều lần vượt tổng $10.000 cũ KHÔNG còn bị chặn")
    void noDailyCapAnymore() throws Exception {
        Staff admin = staff(UserRole.ADMIN);
        UUID playerId = fundedPlayerId();

        // 12 x $1.000 = $12.000, vượt trần ngày cũ ($10.000) ngay từ lần thứ 11.
        for (int i = 0; i < 12; i++) {
            adjust(admin.bearer(), playerId, "CREDIT", "1000", 200);
        }

        assertThat(balanceOf(playerId)).isEqualByComparingTo("12200");
        assertThat(adjustmentRows(playerId)).isEqualTo(12);
    }

    @Test
    @DisplayName("FINANCE cũng điều chỉnh được số tiền lớn (không riêng ADMIN)")
    void financeCanAdjustLargeAmount() throws Exception {
        Staff finance = staff(UserRole.FINANCE);
        UUID playerId = fundedPlayerId();

        adjust(finance.bearer(), playerId, "CREDIT", "25000", 200);

        assertThat(balanceOf(playerId)).isEqualByComparingTo("25200");
    }

    @Test
    @DisplayName("Admin KHÔNG điều chỉnh được ví của CHÍNH MÌNH (chốt chặn tự giao dịch)")
    void adminCannotAdjustOwnWallet() throws Exception {
        Staff admin = staff(UserRole.ADMIN);

        JsonNode error = adjust(admin.bearer(), admin.id(), "CREDIT", "100", 400);

        assertThat(error.get("code").asString()).isEqualTo("CANNOT_MODIFY_SELF");
        assertThat(balanceOf(admin.id())).isEqualByComparingTo("0");
        assertThat(adjustmentRows(admin.id())).isZero();
    }

    @Test
    @DisplayName("Trừ tiền vượt số dư vẫn bị chặn (INSUFFICIENT_BALANCE), không âm ví")
    void debitBeyondBalanceStillBlocked() throws Exception {
        Staff admin = staff(UserRole.ADMIN);
        UUID playerId = fundedPlayerId();

        // Bỏ hạn mức là bỏ trần THAO TÁC của admin, KHÔNG phải bỏ ràng buộc số dư
        // không âm — đó là bất biến của ledger, không liên quan quy trình phê duyệt.
        adjust(admin.bearer(), playerId, "DEBIT", "999999", 400);

        assertThat(balanceOf(playerId)).isEqualByComparingTo("200");
        assertThat(adjustmentRows(playerId)).isZero();
    }

    @Test
    @DisplayName("Thiếu lý do -> 400, không chuyển tiền")
    void reasonStillRequired() throws Exception {
        Staff admin = staff(UserRole.ADMIN);
        UUID playerId = fundedPlayerId();

        mockMvc.perform(post("/api/v1/admin/users/" + playerId + "/wallet/adjust")
                        .header("Authorization", admin.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"direction":"CREDIT","amount":"5000","reason":""}
                                """))
                .andExpect(status().isBadRequest());

        assertThat(balanceOf(playerId)).isEqualByComparingTo("200");
    }
}
