package com.rwg.admin;

import com.rwg.identity.domain.User;
import com.rwg.identity.domain.UserRole;
import com.rwg.identity.repository.UserRepository;
import com.rwg.wallet.repository.WalletRepository;
import com.rwg.wallet.repository.WalletTransactionRepository;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Kiểm chứng điều chỉnh số dư THỦ CÔNG của admin (chặng 3).
 *
 * Điểm quan trọng nhất: mọi điều chỉnh PHẢI sinh đúng 1 dòng ledger để job đối soát
 * (SUM(credit) - SUM(debit) == wallets.balance) không lệch. Test
 * {@link #adjustmentKeepsLedgerAndBalanceConsistent()} khẳng định bất biến này.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminWalletAdjustmentTest {

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

    private String adminBearer() throws Exception {
        String username = unique("admwal");
        registerAndLogin(username);
        User admin = userRepository.findByUsername(username).orElseThrow();
        admin.setRole(UserRole.ADMIN);
        userRepository.save(admin);
        return "Bearer " + login(username).get("accessToken").asText();
    }

    /** Tạo player đã nạp sẵn $100, trả về (userId, bearer). */
    private Player fundedPlayer() throws Exception {
        String username = unique("wal");
        JsonNode tokens = registerAndLogin(username);
        String bearer = "Bearer " + tokens.get("accessToken").asText();
        mockMvc.perform(post("/api/v1/wallet/deposits")
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"amount":"100"}
                                """))
                .andExpect(status().isCreated());
        return new Player(userRepository.findByUsername(username).orElseThrow().getId(), bearer);
    }

    private record Player(UUID id, String bearer) {
    }

    private MvcResult adjust(String adminBearer, UUID userId, String direction, String amount,
                             String reason, int expectedStatus) throws Exception {
        String body = reason == null
                ? """
                {"direction":"%s","amount":"%s"}
                """.formatted(direction, amount)
                : """
                {"direction":"%s","amount":"%s","reason":"%s"}
                """.formatted(direction, amount, reason);
        return mockMvc.perform(post("/api/v1/admin/users/" + userId + "/wallet/adjust")
                        .header("Authorization", adminBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().is(expectedStatus))
                .andReturn();
    }

    private BigDecimal playerBalance(String bearer) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/wallet/me").header("Authorization", bearer))
                .andExpect(status().isOk())
                .andReturn();
        return new BigDecimal(objectMapper.readTree(result.getResponse().getContentAsString())
                .get("balance").asText());
    }

    @Test
    void playerCannotAdjustAnyWallet() throws Exception {
        Player player = fundedPlayer();

        // Player dùng token của CHÍNH MÌNH để tự cộng tiền -> 403.
        mockMvc.perform(post("/api/v1/admin/users/" + player.id() + "/wallet/adjust")
                        .header("Authorization", player.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"direction":"CREDIT","amount":"1000000","reason":"tự cộng tiền"}
                                """))
                .andExpect(status().isForbidden());

        assertThat(playerBalance(player.bearer())).isEqualByComparingTo("100");
    }

    @Test
    void creditIncreasesBalanceAndReportsBeforeAfter() throws Exception {
        Player player = fundedPlayer();
        String admin = adminBearer();

        MvcResult result = adjust(admin, player.id(), "CREDIT", "25.5", "hoàn tiền sự cố vòng chơi", 200);
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());

        assertThat(new BigDecimal(body.get("balanceBefore").asText())).isEqualByComparingTo("100");
        assertThat(new BigDecimal(body.get("balanceAfter").asText())).isEqualByComparingTo("125.5");
        assertThat(body.get("direction").asText()).isEqualTo("CREDIT");
        assertThat(playerBalance(player.bearer())).isEqualByComparingTo("125.5");
    }

    @Test
    void debitDecreasesBalance() throws Exception {
        Player player = fundedPlayer();
        String admin = adminBearer();

        adjust(admin, player.id(), "DEBIT", "40", "thu hồi tiền thưởng cấp sai", 200);

        assertThat(playerBalance(player.bearer())).isEqualByComparingTo("60");
    }

    @Test
    void debitBeyondBalanceIsRejectedAndBalanceUnchanged() throws Exception {
        Player player = fundedPlayer();
        String admin = adminBearer();

        // WalletService dùng UPDATE ... WHERE balance >= amt nên tự chặn, không cần check trước.
        MvcResult result = adjust(admin, player.id(), "DEBIT", "100.00000001", "trừ quá số dư", 400);
        assertThat(objectMapper.readTree(result.getResponse().getContentAsString()).get("code").asText())
                .isEqualTo("INSUFFICIENT_BALANCE");

        assertThat(playerBalance(player.bearer())).isEqualByComparingTo("100");
    }

    @Test
    void adjustmentWithoutReasonIsRejected() throws Exception {
        Player player = fundedPlayer();
        String admin = adminBearer();

        // reason là @NotBlank ở DTO -> chặn ngay tầng validation.
        MvcResult result = adjust(admin, player.id(), "CREDIT", "10", null, 400);
        assertThat(objectMapper.readTree(result.getResponse().getContentAsString()).get("code").asText())
                .isEqualTo("VALIDATION_ERROR");

        assertThat(playerBalance(player.bearer())).isEqualByComparingTo("100");
    }

    @Test
    void nonPositiveOrMalformedAmountIsRejected() throws Exception {
        Player player = fundedPlayer();
        String admin = adminBearer();

        adjust(admin, player.id(), "CREDIT", "0", "số tiền bằng 0", 400);
        adjust(admin, player.id(), "CREDIT", "-50", "số tiền âm", 400);
        adjust(admin, player.id(), "CREDIT", "abc", "không phải số", 400);
        adjust(admin, player.id(), "SIDEWAYS", "10", "hướng không hợp lệ", 400);

        assertThat(playerBalance(player.bearer())).isEqualByComparingTo("100");
    }

    @Test
    void adjustingUnknownUserReturnsNotFound() throws Exception {
        String admin = adminBearer();
        adjust(admin, UUID.randomUUID(), "CREDIT", "10", "user không tồn tại", 404);
    }

    @Test
    void adjustmentKeepsLedgerAndBalanceConsistent() throws Exception {
        Player player = fundedPlayer();
        String admin = adminBearer();

        adjust(admin, player.id(), "CREDIT", "30", "thưởng sự kiện", 200);
        adjust(admin, player.id(), "DEBIT", "10", "thu hồi một phần", 200);

        UUID walletId = walletRepository.findByUserId(player.id()).orElseThrow().getId();

        // Bất biến đối soát: tổng ledger PHẢI khớp số dư ví.
        BigDecimal ledgerNet = transactionRepository.sumNetByWallet().stream()
                .filter(row -> walletId.equals(row[0]))
                .map(row -> (BigDecimal) row[1])
                .findFirst()
                .orElseThrow();
        BigDecimal balance = walletRepository.findByUserId(player.id()).orElseThrow().getBalance();

        assertThat(ledgerNet)
                .as("SUM(credit)-SUM(debit) phải khớp wallets.balance sau khi admin điều chỉnh")
                .isEqualByComparingTo(balance);
        assertThat(balance).isEqualByComparingTo("120");
    }

    @Test
    void ledgerViewFiltersAdjustmentRowsOnly() throws Exception {
        Player player = fundedPlayer();
        String admin = adminBearer();

        adjust(admin, player.id(), "CREDIT", "30", "thưởng sự kiện", 200);
        adjust(admin, player.id(), "DEBIT", "10", "thu hồi một phần", 200);

        // Lọc ADJUSTMENT -> đúng 2 dòng admin vừa tạo, KHÔNG lẫn dòng DEPOSIT.
        MvcResult filtered = mockMvc.perform(get("/api/v1/admin/users/" + player.id() + "/wallet/transactions")
                        .header("Authorization", admin)
                        .param("refType", "ADJUSTMENT"))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode body = objectMapper.readTree(filtered.getResponse().getContentAsString());
        assertThat(body.get("totalElements").asLong()).isEqualTo(2);
        body.get("content").forEach(row ->
                assertThat(row.get("refType").asText()).isEqualTo("ADJUSTMENT"));

        // Không lọc -> có cả dòng nạp tiền ban đầu.
        MvcResult all = mockMvc.perform(get("/api/v1/admin/users/" + player.id() + "/wallet/transactions")
                        .header("Authorization", admin))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(objectMapper.readTree(all.getResponse().getContentAsString())
                .get("totalElements").asLong()).isEqualTo(3);
    }

    @Test
    void adminCanReadWalletOfUserWithoutWallet() throws Exception {
        // User vừa đăng ký, chưa nạp -> chưa chắc có row ví. Không được 500.
        String username = unique("nowallet");
        registerAndLogin(username);
        UUID userId = userRepository.findByUsername(username).orElseThrow().getId();
        String admin = adminBearer();

        mockMvc.perform(get("/api/v1/admin/users/" + userId + "/wallet")
                        .header("Authorization", admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value("0.00000000"));

        mockMvc.perform(get("/api/v1/admin/users/" + userId + "/wallet/transactions")
                        .header("Authorization", admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
    }
}
