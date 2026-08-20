package com.rwg.game;

import com.rwg.common.money.Money;
import com.rwg.game.domain.BetType;
import com.rwg.game.service.RouletteEngine;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Mô phỏng 1000 round Roulette (engine PURE, seed cố định) để khóa tính đúng đắn
 * tiền tệ: tổng ledger (debit stake / credit payout) khớp CÔNG THỨC Money với
 * sai khác 0 (assert bằng BigDecimal.compareTo, KHÔNG dùng equals — khác scale).
 * Mọi tính toán tiền CHỈ qua Money (scale 8 HALF_UP), cấm float/double.
 */
class RouletteSimulationTest {

    private static final int ROUNDS = 1000;
    private static final long SEED = 20260820L;

    /** Sinh selection hợp lệ theo loại cược từ Random có seed. */
    private static String randomSelection(BetType type, Random r) {
        return switch (type) {
            case STRAIGHT -> String.valueOf(r.nextInt(37));
            case SPLIT -> {
                // Cặp kề hợp lệ theo quy ước bàn RWG (xem RouletteEngine): dọc cột giữa (n%3==2).
                int n = 2 + 3 * r.nextInt(11); // 2,5,...,32
                yield n + "-" + (n + 3);
            }
            case STREET -> String.valueOf(1 + 3 * r.nextInt(12));     // 1,4,...,34
            case CORNER -> {
                int n;
                do {
                    n = 1 + r.nextInt(32); // 1..32
                } while (n % 3 == 0 || n % 3 == 2); // cần n%3==1 để {n,n+1,n+3,n+4} vuông hợp lệ
                yield n + "-" + (n + 1) + "-" + (n + 3) + "-" + (n + 4);
            }
            case SIX_LINE -> String.valueOf(1 + 6 * r.nextInt(6));    // 1,7,...,31
            case COLUMN -> String.valueOf(1 + r.nextInt(3));
            case DOZEN -> String.valueOf(1 + r.nextInt(3));
            default -> "";
        };
    }

    @Test
    void simulate1000RoundsLedgerMatchesFormulaExactly() {
        Random r = new Random(SEED);
        Money initial = Money.of("1000000");

        Money balance = initial;
        Money totalStaked = Money.zero();
        Money totalPayout = Money.zero();
        List<Money> ledger = new ArrayList<>(); // + payout thắng, - stake mỗi cược
        int totalBets = 0;
        int totalWins = 0;

        for (int round = 0; round < ROUNDS; round++) {
            int number = RouletteEngine.spin(r);
            int betCount = 1 + r.nextInt(5); // 1..5 cược mỗi round
            for (int i = 0; i < betCount; i++) {
                BetType type = BetType.values()[r.nextInt(BetType.values().length)];
                String selection = randomSelection(type, r);
                // Stake nguyên $1..$500 (giữ đơn giản, scale 8 vẫn áp dụng qua Money).
                Money stake = Money.of(BigDecimal.valueOf(1 + r.nextInt(500)));
                totalBets++;

                // M1: debit NGAY khi đặt cược.
                balance = balance.subtract(stake);
                totalStaked = totalStaked.add(stake);
                ledger.add(stake.subtract(stake).subtract(stake)); // -stake ( Money không có negate)

                Money payout = RouletteEngine.payout(type, selection, number, stake);
                if (payout.isPositive()) {
                    totalWins++;
                    // M2 stake-inclusive: payout = stake + stake×odds.
                    Money expected = stake.winningPayoutAtOdds(RouletteEngine.oddsFor(type));
                    assertThat(payout.compareAmountTo(expected)).isZero();
                    balance = balance.add(payout);
                    totalPayout = totalPayout.add(payout);
                    ledger.add(payout);
                }
            }
        }

        // 1) Balance tuần tự == công thức tổng: initial - Σstake + Σpayout (compareTo = 0).
        Money expectedBalance = initial.subtract(totalStaked).add(totalPayout);
        assertThat(balance.compareAmountTo(expectedBalance))
                .as("balance %s phải khớp initial - Σstake + Σpayout = %s",
                        balance.amount(), expectedBalance.amount())
                .isZero();

        // 2) Tổng ledger (credit − debit) == balance − initial, sai khác 0.
        Money ledgerNet = Money.zero();
        for (Money entry : ledger) {
            ledgerNet = ledgerNet.add(entry);
        }
        assertThat(ledgerNet.compareAmountTo(balance.subtract(initial))).isZero();
        assertThat(ledgerNet.compareAmountTo(totalPayout.subtract(totalStaked))).isZero();

        // 3) Thống kê tối thiểu để chắc mô phỏng thực sự chạy (seed cố định).
        assertThat(totalBets).isGreaterThan(ROUNDS);
        assertThat(totalWins).isGreaterThan(0);
        assertThat(RouletteEngine.spin(new Random(SEED)))
                .as("seed cố định -> round đầu luôn cùng số (deterministic)")
                .isEqualTo(RouletteEngine.spin(new Random(SEED)));
    }
}
