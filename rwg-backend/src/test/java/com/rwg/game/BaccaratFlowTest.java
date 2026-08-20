package com.rwg.game;

import com.rwg.common.money.Money;
import com.rwg.game.domain.Bet;
import com.rwg.game.domain.BetStatus;
import com.rwg.game.domain.BetType;
import com.rwg.game.domain.GameRound;
import com.rwg.game.domain.RoundStatus;
import com.rwg.game.repository.BetRepository;
import com.rwg.game.repository.GameRoundRepository;
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
class BaccaratFlowTest {

    private static final String TABLE_ID = "22222222-3333-4444-5555-666666666666";
    private static final String PASSWORD = "MatKhau@12345";

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    BetRepository betRepository;

    @Autowired
    GameRoundRepository roundRepository;

    private String unique(String prefix) {
        return prefix + UUID.randomUUID().toString().substring(0, 8);
    }

    private String registerLoginBearer(String username) throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"%s","email":"%s@example.com","password":"%s"}
                                """.formatted(username, username, PASSWORD)))
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

    private String betUntilAccepted(String bearer, String stake, BetType betType, int seqStart) throws Exception {
        int seq = seqStart;
        long deadline = System.currentTimeMillis() + 15_000;
        while (System.currentTimeMillis() < deadline) {
            MvcResult result = mockMvc.perform(post("/api/v1/games/tables/" + TABLE_ID + "/bets")
                            .header("Authorization", bearer)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"betType":"%s","selection":"","stake":"%s","seq":%d}
                                    """.formatted(betType.name(), stake, seq)))
                    .andReturn();
            if (result.getResponse().getStatus() == 201) {
                return result.getResponse().getContentAsString();
            }
            seq++;
            Thread.sleep(50);
        }
        throw new AssertionError("no bet accepted within 15s (BETTING_OPEN window never hit)");
    }

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
    void fullBaccaratLifecycleFlow() throws Exception {
        String bearer = registerLoginBearer(unique("bacc"));
        mockMvc.perform(post("/api/v1/wallet/deposits")
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":\"1000\"}"))
                .andExpect(status().isCreated());

        // Verify Baccarat table is seeded correctly
        mockMvc.perform(get("/api/v1/games/tables").header("Authorization", bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[1].id").value(TABLE_ID))
                .andExpect(jsonPath("$[1].gameType").value("BACCARAT"))
                .andExpect(jsonPath("$[1].nameI18n.vi").value("Baccarat Cao Cấp"));

        // Place a bet on PLAYER and BANKER and TIE
        BigDecimal balBefore = balance(bearer);
        assertThat(balBefore.compareTo(new BigDecimal("1000"))).isZero();

        String resJsonPlayer = betUntilAccepted(bearer, "10", BetType.PLAYER, 1);
        UUID betIdPlayer = UUID.fromString(objectMapper.readTree(resJsonPlayer).get("id").asText());

        String resJsonBanker = betUntilAccepted(bearer, "10", BetType.BANKER, 2);
        UUID betIdBanker = UUID.fromString(objectMapper.readTree(resJsonBanker).get("id").asText());

        BigDecimal balAfterBet = balance(bearer);
        // Both bets placed -> $10 + $10 = $20 deducted from wallet
        assertThat(balAfterBet.compareTo(new BigDecimal("980"))).isZero();

        // Await settlement
        Bet settledBetP = awaitBetStatus(betIdPlayer, BetStatus.SETTLED, 12_000);
        Bet settledBetB = awaitBetStatus(betIdBanker, BetStatus.SETTLED, 2_000);

        GameRound round = roundRepository.findFirstById(settledBetP.getRoundId()).orElseThrow();
        assertThat(round.getStatus()).isEqualTo(RoundStatus.SETTLED);
        assertThat(round.getBaccaratResult()).isNotNull();

        String result = round.getBaccaratResult();
        BigDecimal payoutP = settledBetP.getPayout();
        BigDecimal payoutB = settledBetB.getPayout();

        // Verify payout based on actual result
        if ("PLAYER".equals(result)) {
            // Player wins pays 1:1 -> $20
            assertThat(payoutP.compareTo(new BigDecimal("20"))).isZero();
            assertThat(payoutB.compareTo(BigDecimal.ZERO)).isZero();
        } else if ("BANKER".equals(result)) {
            // Banker wins pays 1.95:1 (commission deducted) -> $19.50
            assertThat(payoutP.compareTo(BigDecimal.ZERO)).isZero();
            assertThat(payoutB.compareTo(new BigDecimal("19.50"))).isZero();
        } else if ("TIE".equals(result)) {
            // Tie refunds both bets -> $10 refund each
            assertThat(payoutP.compareTo(new BigDecimal("10"))).isZero();
            assertThat(payoutB.compareTo(new BigDecimal("10"))).isZero();
        }

        // Final balance check: $980 + payoutP + payoutB
        BigDecimal finalBal = balance(bearer);
        BigDecimal expectedBal = new BigDecimal("980").add(payoutP).add(payoutB);
        assertThat(finalBal.compareTo(expectedBal)).isZero();
    }
}
