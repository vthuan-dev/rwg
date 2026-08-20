package com.rwg.game;

import com.rwg.common.money.Money;
import com.rwg.game.domain.BetType;
import com.rwg.game.service.Kl28Engine;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

class Kl28SimulationTest {

    private static final int ROUNDS = 1000;
    private static final long SEED = 20260820L;

    @Test
    void simulate1000Kl28Rounds() {
        Random r = new Random(SEED);
        Money initial = Money.of("1000000");

        Money balance = initial;
        Money totalStaked = Money.zero();
        Money totalPayout = Money.zero();
        List<Money> ledger = new ArrayList<>();

        int totalBets = 0;
        int totalWins = 0;

        for (int round = 0; round < ROUNDS; round++) {
            Kl28Engine.RoundResult result = Kl28Engine.playRound(r);
            int betCount = 1 + r.nextInt(3);
            for (int i = 0; i < betCount; i++) {
                BetType type = switch (r.nextInt(4)) {
                    case 0 -> BetType.KL28_BIG;
                    case 1 -> BetType.KL28_SMALL;
                    case 2 -> BetType.KL28_SINGLE;
                    default -> BetType.KL28_DOUBLE;
                };

                Money stake = Money.of(BigDecimal.valueOf(10 + r.nextInt(500)));
                totalBets++;

                balance = balance.subtract(stake);
                totalStaked = totalStaked.add(stake);
                ledger.add(stake.subtract(stake).subtract(stake)); // -stake

                Money payout = Kl28Engine.payout(type, "", result.getSum(), stake);
                if (payout.isPositive()) {
                    totalWins++;
                    balance = balance.add(payout);
                    totalPayout = totalPayout.add(payout);
                    ledger.add(payout);
                }
            }
        }

        Money expectedBalance = initial.subtract(totalStaked).add(totalPayout);
        assertThat(balance.compareAmountTo(expectedBalance))
                .as("balance %s phải khớp initial - Σstake + Σpayout = %s",
                        balance.amount(), expectedBalance.amount())
                .isZero();

        Money ledgerNet = Money.zero();
        for (Money entry : ledger) {
            ledgerNet = ledgerNet.add(entry);
        }
        assertThat(ledgerNet.compareAmountTo(balance.subtract(initial))).isZero();

        assertThat(totalBets).isGreaterThan(ROUNDS);
        assertThat(totalWins).isGreaterThan(0);
    }
}
