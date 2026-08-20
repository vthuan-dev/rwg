package com.rwg.game;

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
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
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
class Kl28FlowTest {

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

    private String betUntilAccepted(String bearer, String tableId, String stake, BetType betType, int seqStart) throws Exception {
        int seq = seqStart;
        long deadline = System.currentTimeMillis() + 15_000;
        while (System.currentTimeMillis() < deadline) {
            MvcResult result = mockMvc.perform(post("/api/v1/games/tables/" + tableId + "/bets")
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

    private void runFlowForTable(String tableId, String gameType, String viName) throws Exception {
        String bearer = registerLoginBearer(unique(gameType.toLowerCase()));
        mockMvc.perform(post("/api/v1/wallet/deposits")
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":\"1000\"}"))
                .andExpect(status().isCreated());

        // Verify table is seeded correctly
        mockMvc.perform(get("/api/v1/games/tables").header("Authorization", bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id=='" + tableId + "')].gameType").value(gameType))
                .andExpect(jsonPath("$[?(@.id=='" + tableId + "')].nameI18n.vi").value(viName));

        // Place a bet on KL28_BIG
        BigDecimal balBefore = balance(bearer);
        assertThat(balBefore.compareTo(new BigDecimal("1000"))).isZero();

        String resJson = betUntilAccepted(bearer, tableId, "10", BetType.KL28_BIG, 1);
        UUID betId = UUID.fromString(objectMapper.readTree(resJson).get("id").asText());

        BigDecimal balAfterBet = balance(bearer);
        assertThat(balAfterBet.compareTo(new BigDecimal("990"))).isZero();

        // Await settlement
        Bet settledBet = awaitBetStatus(betId, BetStatus.SETTLED, 12_000);

        GameRound round = roundRepository.findFirstById(settledBet.getRoundId()).orElseThrow();
        assertThat(round.getStatus()).isEqualTo(RoundStatus.SETTLED);
        assertThat(round.getKl28Sum()).isNotNull();

        int sum = round.getKl28Sum();
        boolean won = sum >= 14;
        BigDecimal expectedPayout = won ? new BigDecimal("19.80") : BigDecimal.ZERO;

        assertThat(settledBet.getPayout().compareTo(expectedPayout)).isZero();

        // Final balance check: $990 + payout
        BigDecimal finalBal = balance(bearer);
        BigDecimal expectedBal = new BigDecimal("990").add(expectedPayout);
        assertThat(finalBal.compareTo(expectedBal)).isZero();
    }

    @Test
    void testKoreanLucky28Lifecycle() throws Exception {
        runFlowForTable("33333333-4444-5555-6666-777777777777", "KL28", "Korean Lucky 28");
    }

    @Test
    void testLucky28Lifecycle() throws Exception {
        runFlowForTable("44444444-5555-6666-7777-888888888888", "LUCKY28", "Lucky 28");
    }

    @Test
    void testBritishLucky28Lifecycle() throws Exception {
        runFlowForTable("55555555-6666-7777-8888-999999999999", "BRITISH_LUCKY28", "British Lucky 28");
    }

    @Test
    void testTaiwanTimesLifecycle() throws Exception {
        runFlowForTable("66666666-7777-8888-9999-aaaaaaaaaaaa", "TAIWAN_TIMES", "Taiwan Times");
    }

    @Test
    void testLucky28HistoryAndExactSumBet() throws Exception {
        String bearer = registerLoginBearer(unique("history"));
        mockMvc.perform(post("/api/v1/wallet/deposits")
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":\"1000\"}"))
                .andExpect(status().isCreated());

        String tableId = "33333333-4444-5555-6666-777777777777";
        // Place exact number bet on "14"
        int seq = 1;
        String resJson = "";
        long deadline = System.currentTimeMillis() + 15_000;
        while (System.currentTimeMillis() < deadline) {
            MvcResult result = mockMvc.perform(post("/api/v1/games/tables/" + tableId + "/bets")
                            .header("Authorization", bearer)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"betType":"KL28_NUMBER","selection":"14","stake":"10","seq":%d}
                                    """.formatted(seq)))
                    .andReturn();
            if (result.getResponse().getStatus() == 201) {
                resJson = result.getResponse().getContentAsString();
                break;
            }
            seq++;
            Thread.sleep(50);
        }
        assertThat(resJson).isNotEmpty();
        UUID betId = UUID.fromString(objectMapper.readTree(resJson).get("id").asText());

        // Await settlement
        Bet settledBet = awaitBetStatus(betId, BetStatus.SETTLED, 12_000);

        GameRound round = roundRepository.findFirstById(settledBet.getRoundId()).orElseThrow();
        assertThat(round.getStatus()).isEqualTo(RoundStatus.SETTLED);
        assertThat(round.getKl28Sum()).isNotNull();

        int sum = round.getKl28Sum();
        boolean won = (sum == 14);
        // Profit odds for 14 is 12 (12:1), meaning total payout is 13x stake
        BigDecimal expectedPayout = won ? new BigDecimal("130.00") : BigDecimal.ZERO;
        assertThat(settledBet.getPayout().compareTo(expectedPayout)).isZero();

        // 1. Verify Paginated Drawing History (Lịch sử quay số)
        mockMvc.perform(get("/api/v1/games/tables/" + tableId + "/rounds")
                        .header("Authorization", bearer)
                        .param("page", "0")
                        .param("size", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content[0].roundId").value(round.getId().toString()))
                .andExpect(jsonPath("$.content[0].kl28Sum").value(sum))
                .andExpect(jsonPath("$.totalPages").isNumber())
                .andExpect(jsonPath("$.totalElements").isNumber());

        // 2. Verify Paginated Bet History (Lịch sử cược)
        mockMvc.perform(get("/api/v1/games/me/bets/history")
                        .header("Authorization", bearer)
                        .param("tableId", tableId)
                        .param("page", "0")
                        .param("size", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content[0].id").value(betId.toString()))
                .andExpect(jsonPath("$.content[0].betType").value("KL28_NUMBER"))
                .andExpect(jsonPath("$.content[0].selection").value("14"))
                .andExpect(jsonPath("$.content[0].status").value("SETTLED"))
                .andExpect(jsonPath("$.totalPages").isNumber())
                .andExpect(jsonPath("$.totalElements").isNumber());
    }
}
