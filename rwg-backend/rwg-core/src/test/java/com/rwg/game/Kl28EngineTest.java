package com.rwg.game;

import com.rwg.common.money.Money;
import com.rwg.game.domain.BetType;
import com.rwg.game.service.Kl28Engine;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

class Kl28EngineTest {

    @Test
    void testPlayRound() {
        Random r = new Random(12345);
        for (int i = 0; i < 100; i++) {
            Kl28Engine.RoundResult res = Kl28Engine.playRound(r);
            assertThat(res.getNumbers()).hasSize(3);
            for (int n : res.getNumbers()) {
                assertThat(n).isBetween(0, 9);
            }
            int sum = res.getNumbers().stream().mapToInt(Integer::intValue).sum();
            assertThat(res.getSum()).isEqualTo(sum);
            assertThat(sum).isBetween(0, 27);
        }
    }

    @Test
    void testPayouts() {
        Money stake = Money.of("10");

        // KL28_BIG: wins if sum >= 14
        assertThat(Kl28Engine.payout(BetType.KL28_BIG, "", 14, stake).compareAmountTo(Money.of("19.8"))).isZero();
        assertThat(Kl28Engine.payout(BetType.KL28_BIG, "", 13, stake).compareAmountTo(Money.zero())).isZero();

        // KL28_SMALL: wins if sum <= 13
        assertThat(Kl28Engine.payout(BetType.KL28_SMALL, "", 13, stake).compareAmountTo(Money.of("19.8"))).isZero();
        assertThat(Kl28Engine.payout(BetType.KL28_SMALL, "", 14, stake).compareAmountTo(Money.zero())).isZero();

        // KL28_SINGLE: wins if odd sum
        assertThat(Kl28Engine.payout(BetType.KL28_SINGLE, "", 15, stake).compareAmountTo(Money.of("19.8"))).isZero();
        assertThat(Kl28Engine.payout(BetType.KL28_SINGLE, "", 14, stake).compareAmountTo(Money.zero())).isZero();

        // KL28_DOUBLE: wins if even sum
        assertThat(Kl28Engine.payout(BetType.KL28_DOUBLE, "", 14, stake).compareAmountTo(Money.of("19.8"))).isZero();
        assertThat(Kl28Engine.payout(BetType.KL28_DOUBLE, "", 15, stake).compareAmountTo(Money.zero())).isZero();

        // KL28_NUMBER: exact sum 0 wins 1000x total payout (odds 999:1)
        assertThat(Kl28Engine.payout(BetType.KL28_NUMBER, "0", 0, stake).compareAmountTo(Money.of("10000"))).isZero();
        assertThat(Kl28Engine.payout(BetType.KL28_NUMBER, "0", 1, stake).compareAmountTo(Money.zero())).isZero();

        // KL28_NUMBER: exact sum 14 wins 13x total payout (odds 12:1)
        assertThat(Kl28Engine.payout(BetType.KL28_NUMBER, "14", 14, stake).compareAmountTo(Money.of("130"))).isZero();
        assertThat(Kl28Engine.payout(BetType.KL28_NUMBER, "14", 15, stake).compareAmountTo(Money.zero())).isZero();
    }

    @Test
    void testSelections() {
        assertThat(Kl28Engine.validSelection(BetType.KL28_BIG, "")).isTrue();
        assertThat(Kl28Engine.validSelection(BetType.KL28_BIG, "123")).isFalse();
        assertThat(Kl28Engine.validSelection(BetType.STRAIGHT, "")).isFalse();

        // KL28_NUMBER selections
        assertThat(Kl28Engine.validSelection(BetType.KL28_NUMBER, "0")).isTrue();
        assertThat(Kl28Engine.validSelection(BetType.KL28_NUMBER, "27")).isTrue();
        assertThat(Kl28Engine.validSelection(BetType.KL28_NUMBER, "28")).isFalse();
        assertThat(Kl28Engine.validSelection(BetType.KL28_NUMBER, "-1")).isFalse();
        assertThat(Kl28Engine.validSelection(BetType.KL28_NUMBER, "abc")).isFalse();
        assertThat(Kl28Engine.validSelection(BetType.KL28_NUMBER, "")).isFalse();
    }
}
