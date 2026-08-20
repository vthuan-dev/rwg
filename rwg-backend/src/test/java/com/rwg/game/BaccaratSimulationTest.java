package com.rwg.game;

import com.rwg.common.money.Money;
import com.rwg.game.domain.BetType;
import com.rwg.game.service.BaccaratEngine;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Mô phỏng 1000 round Baccarat để kiểm chứng tính đúng đắn của tiền tệ:
 * Tổng balance sau cùng phải khớp chính xác initial - Σstake + Σpayout - Σcommission.
 */
class BaccaratSimulationTest {

    private static final int ROUNDS = 1000;
    private static final long SEED = 20260820L;

    @Test
    void simulate1000BaccaratRounds() {
        Random r = new Random(SEED);
        Money initial = Money.of("1000000");

        Money balance = initial;
        Money totalStaked = Money.zero();
        Money totalPayout = Money.zero();
        Money totalCommission = Money.zero();
        List<Money> ledger = new ArrayList<>();

        int totalBets = 0;
        int totalWins = 0;

        for (int round = 0; round < ROUNDS; round++) {
            BaccaratEngine.RoundResult result = BaccaratEngine.playRound(r);
            
            // Đặt ngẫu nhiên từ 1 đến 3 cửa cược trong bàn
            int betCount = 1 + r.nextInt(3);
            for (int i = 0; i < betCount; i++) {
                BetType type = switch (r.nextInt(5)) {
                    case 0 -> BetType.PLAYER;
                    case 1 -> BetType.BANKER;
                    case 2 -> BetType.TIE;
                    case 3 -> BetType.PLAYER_PAIR;
                    default -> BetType.BANKER_PAIR;
                };

                Money stake = Money.of(BigDecimal.valueOf(10 + r.nextInt(500)));
                totalBets++;

                // Trừ tiền cược
                balance = balance.subtract(stake);
                totalStaked = totalStaked.add(stake);
                ledger.add(stake.subtract(stake).subtract(stake)); // -stake

                // Tính toán payout
                Money payout = BaccaratEngine.payout(type, result.getOutcome(),
                        result.isPlayerPair(), result.isBankerPair(), stake);

                // Tính toán commission 5% nếu thắng Banker
                Money commission = Money.zero();
                if (type == BetType.BANKER && "BANKER".equals(result.getOutcome())) {
                    commission = stake.multiply(new BigDecimal("0.05"));
                }

                if (payout.isPositive()) {
                    totalWins++;
                    balance = balance.add(payout);
                    totalPayout = totalPayout.add(payout);
                    ledger.add(payout);
                }

                if (commission.isPositive()) {
                    balance = balance.subtract(commission);
                    totalCommission = totalCommission.add(commission);
                    ledger.add(commission.subtract(commission).subtract(commission)); // -commission
                }
            }
        }

        // Kiểm tra balance cuối cùng
        Money expectedBalance = initial.subtract(totalStaked).add(totalPayout).subtract(totalCommission);
        assertThat(balance.compareAmountTo(expectedBalance))
                .as("balance %s phải khớp initial - Σstake + Σpayout - Σcommission = %s",
                        balance.amount(), expectedBalance.amount())
                .isZero();

        // Kiểm tra ledger khớp balance
        Money ledgerNet = Money.zero();
        for (Money entry : ledger) {
            ledgerNet = ledgerNet.add(entry);
        }
        assertThat(ledgerNet.compareAmountTo(balance.subtract(initial))).isZero();

        assertThat(totalBets).isGreaterThan(ROUNDS);
        assertThat(totalWins).isGreaterThan(0);
        assertThat(totalCommission.isPositive()).isTrue();
    }
}
