package com.rwg.game;

import com.rwg.common.money.Money;
import com.rwg.game.domain.Bet;
import com.rwg.game.domain.BetStatus;
import com.rwg.game.domain.BetType;
import com.rwg.game.domain.GameRound;
import com.rwg.game.domain.RoundStatus;
import com.rwg.game.repository.BetRepository;
import com.rwg.game.repository.GameRoundRepository;
import com.rwg.game.service.RoundScheduler;
import com.rwg.game.service.RouletteEngine;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration test TRỌN vòng đời bàn Roulette (duration rút ngắn qua property):
 * mở (BETTING_OPEN) -> đặt cược TRỪ ví (M1) -> đóng -> spin -> result -> settle
 * credit ĐÚNG công thức stake-inclusive (M2); và hủy vòng -> VOIDED + REFUND.
 */
@SpringBootTest(properties = {
        "rwg.game.round.betting-open=PT3S",
        "rwg.game.round.betting-closed=PT0.1S",
        "rwg.game.round.spinning=PT0.1S",
        "rwg.game.round.result=PT0.1S",
        "rwg.game.round.settle=PT0.3S",
        "rwg.game.bet-placed-window=PT0.1S"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RoundLifecycleTest {

    private static final String TABLE_ID = "11111111-2222-3333-4444-555555555555";
    private static final String PASSWORD = "MatKhau@12345";

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    BetRepository betRepository;

    @Autowired
    GameRoundRepository roundRepository;

    @Autowired
    RoundScheduler roundScheduler;

    private String unique(String prefix) {
        return prefix + UUID.randomUUID().toString().substring(0, 8);
    }

    private String registerLoginBearer(String username) throws Exception {
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

    private BigDecimal balance(String bearer) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/wallet/me").header("Authorization", bearer))
                .andExpect(status().isOk())
                .andReturn();
        return new BigDecimal(objectMapper.readTree(result.getResponse().getContentAsString())
                .get("balance").asText());
    }

    /**
     * Đặt cược RED stake, retry seq tăng dần cho tới khi 201 (chờ cửa sổ BETTING_OPEN).
     * Trả về body của lần đặt thành công.
     */
    private String betUntilAccepted(String bearer, String stake, int seqStart) throws Exception {
        int seq = seqStart;
        long deadline = System.currentTimeMillis() + 15_000;
        while (System.currentTimeMillis() < deadline) {
            MvcResult result = mockMvc.perform(post("/api/v1/games/tables/" + TABLE_ID + "/bets")
                            .header("Authorization", bearer)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"betType":"RED","selection":"","stake":"%s","seq":%d}
                                    """.formatted(stake, seq)))
                    .andReturn();
            if (result.getResponse().getStatus() == 201) {
                return result.getResponse().getContentAsString();
            }
            seq++;
            Thread.sleep(50);
        }
        throw new AssertionError("no bet accepted within 15s (BETTING_OPEN window never hit)");
    }

    /** Chờ bet đạt trạng thái đích (SETTLED/VOIDED) trong timeoutMs. */
    private Bet awaitBetStatus(UUID betId, BetStatus wanted, long timeoutMs) throws Exception {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            Bet bet = betRepository.findFirstById(betId).orElse(null);
            if (bet != null && bet.getStatus() == wanted) {
                return bet;
            }
            Thread.sleep(50);
        }
        throw new AssertionError("bet " + betId + " did not reach " + wanted + " within " + timeoutMs + "ms");
    }

    @Test
    void fullRoundLifecycleDebitsOnBetAndCreditsFormulaOnSettle() throws Exception {
        String bearer = registerLoginBearer(unique("roul"));
        mockMvc.perform(post("/api/v1/wallet/deposits")
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":\"1000\"}"))
                .andExpect(status().isCreated());

        // Danh sách bàn có bàn Roulette seed với name_i18n đủ 4 ngôn ngữ.
        mockMvc.perform(get("/api/v1/games/tables").header("Authorization", bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(TABLE_ID))
                .andExpect(jsonPath("$[0].nameI18n.en").value("Roulette European"))
                .andExpect(jsonPath("$[0].nameI18n.vi").exists())
                .andExpect(jsonPath("$[0].nameI18n.zh").exists())
                .andExpect(jsonPath("$[0].nameI18n.ja").exists());

        String betBody = betUntilAccepted(bearer, "10", 1);
        String betId = objectMapper.readTree(betBody).get("id").asText();
        String roundId = objectMapper.readTree(betBody).get("roundId").asText();

        // M1: ví TRỪ ngay khi đặt cược ($1000 - $10).
        assertThat(balance(bearer)).isEqualByComparingTo("990");
        mockMvc.perform(get("/api/v1/games/me/bets?roundId=" + roundId).header("Authorization", bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("PENDING"));

        // Chờ vòng settle (spin -> result -> settle async).
        Bet settled = awaitBetStatus(UUID.fromString(betId), BetStatus.SETTLED, 15_000);
        GameRound round = roundRepository.findFirstById(UUID.fromString(roundId)).orElseThrow();
        assertThat(round.getStatus()).isEqualTo(RoundStatus.SETTLED);
        assertThat(round.getWinningNumber()).isBetween(0, 36);

        // M2 stake-inclusive: payout == stake + stake × odds đúng công thức Money.
        Money expectedPayout = RouletteEngine.payout(BetType.RED, settled.getSelection(),
                round.getWinningNumber(), Money.of(settled.getStake()));
        assertThat(settled.getPayout()).isEqualByComparingTo(expectedPayout.amount());
        BigDecimal expectedBalance = new BigDecimal("1000")
                .subtract(settled.getStake())
                .add(expectedPayout.amount());
        assertThat(balance(bearer)).isEqualByComparingTo(expectedBalance);
    }

    @Test
    void voidedRoundRefundsAllBets() throws Exception {
        String bearer = registerLoginBearer(unique("void"));
        mockMvc.perform(post("/api/v1/wallet/deposits")
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":\"1000\"}"))
                .andExpect(status().isCreated());

        String betBody = betUntilAccepted(bearer, "25", 1);
        String betId = objectMapper.readTree(betBody).get("id").asText();
        String roundId = objectMapper.readTree(betBody).get("roundId").asText();
        assertThat(balance(bearer)).isEqualByComparingTo("975");

        // Hủy vòng đang chạy: VOID + REFUND toàn bộ cược.
        roundScheduler.voidCurrentRound(UUID.fromString(TABLE_ID));

        Bet voided = awaitBetStatus(UUID.fromString(betId), BetStatus.VOIDED, 15_000);
        assertThat(voided.getPayout()).isEqualByComparingTo(BigDecimal.ZERO);
        GameRound round = roundRepository.findFirstById(UUID.fromString(roundId)).orElseThrow();
        assertThat(round.getStatus()).isEqualTo(RoundStatus.VOIDED);

        // Tiền cược về lại ví nguyên vẹn.
        assertThat(balance(bearer)).isEqualByComparingTo("1000");
    }

    @Test
    void betRejectedWhenRoundClosedOrInvalid() throws Exception {
        String bearer = registerLoginBearer(unique("rej"));
        mockMvc.perform(post("/api/v1/wallet/deposits")
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":\"100\"}"))
                .andExpect(status().isCreated());

        // Selection sai quy ước -> INVALID_BET_SELECTION (400), ví KHÔNG đổi.
        mockMvc.perform(post("/api/v1/games/tables/" + TABLE_ID + "/bets")
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"betType\":\"STRAIGHT\",\"selection\":\"37\",\"stake\":\"10\",\"seq\":1}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_BET_SELECTION"));

        // Stake dưới min của bàn -> validation i18n.
        mockMvc.perform(post("/api/v1/games/tables/" + TABLE_ID + "/bets")
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"betType\":\"RED\",\"selection\":\"\",\"stake\":\"0.5\",\"seq\":2}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        // Bàn không tồn tại -> 404 GAME_TABLE_NOT_FOUND.
        mockMvc.perform(post("/api/v1/games/tables/" + UUID.randomUUID() + "/bets")
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"betType\":\"RED\",\"selection\":\"\",\"stake\":\"10\",\"seq\":1}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("GAME_TABLE_NOT_FOUND"));

        assertThat(balance(bearer)).isEqualByComparingTo("100");
    }
}
