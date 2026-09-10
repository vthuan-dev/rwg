package com.rwg.game;

import com.rwg.common.money.Money;
import com.rwg.game.domain.BetType;
import com.rwg.game.service.XocDiaEngine;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class XocDiaSimulationTest {

    private static final long SEED = 20260910L;

    @Test
    void simulate1000XocDiaRounds_ledgerMatchesExactly() {
        Random r = new Random(SEED);
        Money initial = Money.of("1000000");

        Money balance = initial;
        Money totalStaked = Money.zero();
        Money totalPayout = Money.zero();

        int totalBets = 0;
        int totalWins = 0;

        for (int round = 0; round < 1000; round++) {
            XocDiaEngine.RoundResult result = XocDiaEngine.playRound(r);
            int betCount = 1 + r.nextInt(3);
            for (int i = 0; i < betCount; i++) {
                BetType type = switch (r.nextInt(6)) {
                    case 0 -> BetType.XOC_DIA_EVEN;
                    case 1 -> BetType.XOC_DIA_ODD;
                    case 2 -> BetType.XOC_DIA_FOUR_RED;
                    case 3 -> BetType.XOC_DIA_FOUR_WHITE;
                    case 4 -> BetType.XOC_DIA_THREE_RED;
                    default -> BetType.XOC_DIA_THREE_WHITE;
                };

                Money stake = Money.of(BigDecimal.valueOf(10 + r.nextInt(500)));
                totalBets++;

                balance = balance.subtract(stake);
                totalStaked = totalStaked.add(stake);

                Money payout = XocDiaEngine.payout(type, result.getRedCount(), stake);
                if (payout.isPositive()) {
                    totalWins++;
                    balance = balance.add(payout);
                    totalPayout = totalPayout.add(payout);
                }
            }
        }

        // Kiểm tra phương trình bảo toàn số dư: balance = initial - staked + payout
        Money expectedBalance = initial.subtract(totalStaked).add(totalPayout);
        assertThat(balance.amount()).isEqualByComparingTo(expectedBalance.amount());
        assertThat(totalBets).isGreaterThan(1000);
        assertThat(totalWins).isGreaterThan(0);
    }

    @Test
    void simulate100000Rounds_binomialDistributionAndRtpWithinTolerance() {
        Random r = new Random(SEED);
        final int rounds = 100000;
        int[] countByRed = new int[5];

        Money evenStaked = Money.zero();
        Money evenWon = Money.zero();
        Money stake = Money.of("10");

        for (int i = 0; i < rounds; i++) {
            XocDiaEngine.RoundResult res = XocDiaEngine.playRound(r);
            countByRed[res.getRedCount()]++;

            // Thử cược cửa Chẵn
            evenStaked = evenStaked.add(stake);
            Money p = XocDiaEngine.payout(BetType.XOC_DIA_EVEN, res.getRedCount(), stake);
            if (p.isPositive()) {
                evenWon = evenWon.add(p);
            }
        }

        // Tần suất lý thuyết:
        // R=0: 6.25%
        // R=1: 25.0%
        // R=2: 37.5%
        // R=3: 25.0%
        // R=4: 6.25%
        double freq0 = (double) countByRed[0] / rounds;
        double freq1 = (double) countByRed[1] / rounds;
        double freq2 = (double) countByRed[2] / rounds;
        double freq3 = (double) countByRed[3] / rounds;
        double freq4 = (double) countByRed[4] / rounds;

        assertThat(freq0).isCloseTo(0.0625, within(0.005));
        assertThat(freq1).isCloseTo(0.2500, within(0.008));
        assertThat(freq2).isCloseTo(0.3750, within(0.008));
        assertThat(freq3).isCloseTo(0.2500, within(0.008));
        assertThat(freq4).isCloseTo(0.0625, within(0.005));

        // RTP của Chẵn với odds 0.98:
        // P(win) = 50% -> RTP = 0.5 * (1 + 0.98) = 0.99 = 99.0%
        BigDecimal actualRtp = evenWon.amount().divide(evenStaked.amount(), 4, java.math.RoundingMode.HALF_UP);
        assertThat(actualRtp.doubleValue()).isCloseTo(0.99, within(0.01));
    }
}
