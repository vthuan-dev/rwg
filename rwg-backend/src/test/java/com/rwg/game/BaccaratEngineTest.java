package com.rwg.game;

import com.rwg.common.money.Money;
import com.rwg.game.domain.BetType;
import com.rwg.game.service.BaccaratEngine;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

class BaccaratEngineTest {

    @Test
    void testCardValues() {
        assertThat(BaccaratEngine.getCardValue("HA")).isEqualTo(1);
        assertThat(BaccaratEngine.getCardValue("H2")).isEqualTo(2);
        assertThat(BaccaratEngine.getCardValue("H9")).isEqualTo(9);
        assertThat(BaccaratEngine.getCardValue("H10")).isEqualTo(0);
        assertThat(BaccaratEngine.getCardValue("HJ")).isEqualTo(0);
        assertThat(BaccaratEngine.getCardValue("HQ")).isEqualTo(0);
        assertThat(BaccaratEngine.getCardValue("HK")).isEqualTo(0);
        assertThat(BaccaratEngine.getCardValue("")).isEqualTo(0);
        assertThat(BaccaratEngine.getCardValue(null)).isEqualTo(0);
    }

    @Test
    void testCalculateScore() {
        assertThat(BaccaratEngine.calculateScore(List.of("HA", "H9"))).isEqualTo(0); // 1 + 9 = 10 % 10 = 0
        assertThat(BaccaratEngine.calculateScore(List.of("H2", "H3"))).isEqualTo(5); // 2 + 3 = 5
        assertThat(BaccaratEngine.calculateScore(List.of("S10", "DK", "C3"))).isEqualTo(3); // 0 + 0 + 3 = 3
    }

    @Test
    void testIsPair() {
        assertThat(BaccaratEngine.isPair(List.of("HA", "DA"))).isTrue();
        assertThat(BaccaratEngine.isPair(List.of("HA", "D2"))).isFalse();
        assertThat(BaccaratEngine.isPair(List.of("HA"))).isFalse();
    }

    @Test
    void testShouldBankerDraw() {
        // Banker score <= 2: draw
        assertThat(BaccaratEngine.shouldBankerDraw(2, 5)).isTrue();
        
        // Banker score 3: draw unless player third is 8
        assertThat(BaccaratEngine.shouldBankerDraw(3, 8)).isFalse();
        assertThat(BaccaratEngine.shouldBankerDraw(3, 7)).isTrue();

        // Banker score 4: draw if player third is 2-7
        assertThat(BaccaratEngine.shouldBankerDraw(4, 1)).isFalse();
        assertThat(BaccaratEngine.shouldBankerDraw(4, 2)).isTrue();
        assertThat(BaccaratEngine.shouldBankerDraw(4, 7)).isTrue();
        assertThat(BaccaratEngine.shouldBankerDraw(4, 8)).isFalse();

        // Banker score 5: draw if player third is 4-7
        assertThat(BaccaratEngine.shouldBankerDraw(5, 3)).isFalse();
        assertThat(BaccaratEngine.shouldBankerDraw(5, 4)).isTrue();
        assertThat(BaccaratEngine.shouldBankerDraw(5, 7)).isTrue();
        assertThat(BaccaratEngine.shouldBankerDraw(5, 8)).isFalse();

        // Banker score 6: draw if player third is 6-7
        assertThat(BaccaratEngine.shouldBankerDraw(6, 5)).isFalse();
        assertThat(BaccaratEngine.shouldBankerDraw(6, 6)).isTrue();
        assertThat(BaccaratEngine.shouldBankerDraw(6, 7)).isTrue();
        assertThat(BaccaratEngine.shouldBankerDraw(6, 8)).isFalse();

        // Banker score >= 7: stand
        assertThat(BaccaratEngine.shouldBankerDraw(7, 6)).isFalse();
    }

    @Test
    void testPayouts() {
        Money stake = Money.of("10");

        // Player win pays 1:1 -> total returned = 20
        Money pWin = BaccaratEngine.payout(BetType.PLAYER, "PLAYER", false, false, stake);
        assertThat(pWin.compareAmountTo(Money.of("20"))).isZero();

        // Banker win pays 1:1 -> total returned = 20 (commission is deducted separately at SettlementService)
        Money bWin = BaccaratEngine.payout(BetType.BANKER, "BANKER", false, false, stake);
        assertThat(bWin.compareAmountTo(Money.of("20"))).isZero();

        // Tie win pays 8:1 -> total returned = 90
        Money tWin = BaccaratEngine.payout(BetType.TIE, "TIE", false, false, stake);
        assertThat(tWin.compareAmountTo(Money.of("90"))).isZero();

        // Player/Banker bet on Tie results in refund (1:1 refund of stake)
        Money refundP = BaccaratEngine.payout(BetType.PLAYER, "TIE", false, false, stake);
        assertThat(refundP.compareAmountTo(stake)).isZero();
        Money refundB = BaccaratEngine.payout(BetType.BANKER, "TIE", false, false, stake);
        assertThat(refundB.compareAmountTo(stake)).isZero();

        // Player Pair win pays 11:1 -> total returned = 120
        Money ppWin = BaccaratEngine.payout(BetType.PLAYER_PAIR, "PLAYER", true, false, stake);
        assertThat(ppWin.compareAmountTo(Money.of("120"))).isZero();
    }
}
