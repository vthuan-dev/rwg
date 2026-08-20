package com.rwg.game;

import com.rwg.common.money.Money;
import com.rwg.game.domain.BetType;
import com.rwg.game.service.RouletteEngine;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test DỮ LIỆU THAM SỐ HÓA cho engine Roulette PURE (viết TRƯỚC engine theo TDD).
 * Phủ 100% loại cược; quy ước M2 stake-inclusive: payout = stake + stake × odds.
 * Số 0 làm THUA mọi cược ngoài straight-0.
 *
 * Quy ước selection:
 * STRAIGHT "17" | SPLIT "17-20" | STREET "1" (số đầu hàng) | CORNER "1-2-4-5"
 * SIX_LINE "1" (số đầu) | COLUMN/DOZEN "1"|"2"|"3" | ngoài (RED...) selection rỗng.
 */
class RoulettePayoutsTest {

    private static final Money STAKE = Money.of("10");

    /** (betType, selection hợp lệ, số THẮNG) — phủ đủ 13 loại cược. */
    static Stream<Arguments> winningCases() {
        return Stream.of(
                Arguments.of(BetType.STRAIGHT, "17", 17),
                Arguments.of(BetType.STRAIGHT, "0", 0),      // straight-0 THẮNG khi ra 0
                Arguments.of(BetType.SPLIT, "17-20", 17),
                Arguments.of(BetType.SPLIT, "17-20", 20),
                Arguments.of(BetType.SPLIT, "0-2", 2),       // split với 0
                Arguments.of(BetType.STREET, "1", 1),        // hàng 1-2-3
                Arguments.of(BetType.STREET, "1", 3),
                Arguments.of(BetType.STREET, "34", 36),      // hàng 34-35-36
                Arguments.of(BetType.CORNER, "1-2-4-5", 5),
                Arguments.of(BetType.CORNER, "1-2-4-5", 4),
                Arguments.of(BetType.SIX_LINE, "1", 6),      // 1..6
                Arguments.of(BetType.SIX_LINE, "31", 36),    // 31..36
                Arguments.of(BetType.COLUMN, "1", 34),       // cột 1: 1,4,...,34
                Arguments.of(BetType.COLUMN, "2", 35),
                Arguments.of(BetType.COLUMN, "3", 36),
                Arguments.of(BetType.DOZEN, "1", 12),        // tá 1: 1-12
                Arguments.of(BetType.DOZEN, "2", 13),        // tá 2: 13-24
                Arguments.of(BetType.DOZEN, "3", 36),        // tá 3: 25-36
                Arguments.of(BetType.RED, "", 32),
                Arguments.of(BetType.BLACK, "", 15),
                Arguments.of(BetType.ODD, "", 7),
                Arguments.of(BetType.EVEN, "", 24),
                Arguments.of(BetType.LOW, "", 1),
                Arguments.of(BetType.LOW, "", 18),
                Arguments.of(BetType.HIGH, "", 19),
                Arguments.of(BetType.HIGH, "", 36));
    }

    /** (betType, selection, số RA nhưng cược THUA) — phủ đủ 13 loại cược. */
    static Stream<Arguments> losingCases() {
        return Stream.of(
                Arguments.of(BetType.STRAIGHT, "17", 18),
                Arguments.of(BetType.SPLIT, "17-20", 21),
                Arguments.of(BetType.STREET, "1", 4),
                Arguments.of(BetType.CORNER, "1-2-4-5", 3),
                Arguments.of(BetType.SIX_LINE, "1", 7),
                Arguments.of(BetType.COLUMN, "1", 2),        // 2 thuộc cột 2
                Arguments.of(BetType.DOZEN, "1", 13),
                Arguments.of(BetType.RED, "", 2),            // 2 là đen
                Arguments.of(BetType.BLACK, "", 1),          // 1 là đỏ
                Arguments.of(BetType.ODD, "", 2),
                Arguments.of(BetType.EVEN, "", 3),
                Arguments.of(BetType.LOW, "", 19),
                Arguments.of(BetType.HIGH, "", 18));
    }

    @ParameterizedTest(name = "THẮNG {0} selection={1} number={2}")
    @MethodSource("winningCases")
    void winningBetPaysStakeInclusive(BetType type, String selection, int number) {
        Money payout = RouletteEngine.payout(type, selection, number, STAKE);
        // M2: stake + stake × odds (vd Straight $10 thắng -> $360).
        Money expected = STAKE.winningPayoutAtOdds(RouletteEngine.oddsFor(type));
        assertThat(payout.compareAmountTo(expected))
                .as("payout %s phải bằng stake + stake×odds = %s", payout.amount(), expected.amount())
                .isZero();
    }

    @ParameterizedTest(name = "THUA {0} selection={1} number={2}")
    @MethodSource("losingCases")
    void losingBetPaysZero(BetType type, String selection, int number) {
        assertThat(RouletteEngine.payout(type, selection, number, STAKE).isPositive()).isFalse();
    }

    @Test
    void zeroLosesAllBetsExceptStraightZero() {
        for (BetType type : BetType.values()) {
            if (type == BetType.STRAIGHT) {
                continue; // straight xử lý riêng bên dưới
            }
            // Chọn cửa KHÔNG chứa số 0 để kiểm "0 làm thua".
            String safe = switch (type) {
                case SPLIT -> "1-2";
                case STREET -> "4";
                case CORNER -> "1-2-4-5";
                case SIX_LINE -> "7";
                case COLUMN -> "1";
                case DOZEN -> "1";
                default -> "";
            };
            assertThat(RouletteEngine.isWin(type, safe, 0))
                    .as("Số 0 phải làm THUA cược %s (selection %s)", type, safe)
                    .isFalse();
        }
        // Straight-0 là cược DUY NHẤT thắng khi ra 0.
        assertThat(RouletteEngine.isWin(BetType.STRAIGHT, "0", 0)).isTrue();
        assertThat(RouletteEngine.isWin(BetType.STRAIGHT, "5", 0)).isFalse();
    }

    @Test
    void oddsCoverAll13BetTypes() {
        assertThat(RouletteEngine.oddsFor(BetType.STRAIGHT)).isEqualByComparingTo("35");
        assertThat(RouletteEngine.oddsFor(BetType.SPLIT)).isEqualByComparingTo("17");
        assertThat(RouletteEngine.oddsFor(BetType.STREET)).isEqualByComparingTo("11");
        assertThat(RouletteEngine.oddsFor(BetType.CORNER)).isEqualByComparingTo("8");
        assertThat(RouletteEngine.oddsFor(BetType.SIX_LINE)).isEqualByComparingTo("5");
        assertThat(RouletteEngine.oddsFor(BetType.COLUMN)).isEqualByComparingTo("2");
        assertThat(RouletteEngine.oddsFor(BetType.DOZEN)).isEqualByComparingTo("2");
        assertThat(RouletteEngine.oddsFor(BetType.RED)).isEqualByComparingTo("1");
        assertThat(RouletteEngine.oddsFor(BetType.BLACK)).isEqualByComparingTo("1");
        assertThat(RouletteEngine.oddsFor(BetType.ODD)).isEqualByComparingTo("1");
        assertThat(RouletteEngine.oddsFor(BetType.EVEN)).isEqualByComparingTo("1");
        assertThat(RouletteEngine.oddsFor(BetType.LOW)).isEqualByComparingTo("1");
        assertThat(RouletteEngine.oddsFor(BetType.HIGH)).isEqualByComparingTo("1");
    }

    @Test
    void selectionValidationAcceptsValidAndRejectsInvalid() {
        // Hợp lệ
        assertThat(RouletteEngine.validSelection(BetType.STRAIGHT, "0")).isTrue();
        assertThat(RouletteEngine.validSelection(BetType.STRAIGHT, "36")).isTrue();
        assertThat(RouletteEngine.validSelection(BetType.SPLIT, "0-1")).isTrue();
        assertThat(RouletteEngine.validSelection(BetType.SPLIT, "17-20")).isTrue(); // dọc
        assertThat(RouletteEngine.validSelection(BetType.SPLIT, "1-2")).isTrue();   // ngang
        assertThat(RouletteEngine.validSelection(BetType.STREET, "1")).isTrue();
        assertThat(RouletteEngine.validSelection(BetType.STREET, "34")).isTrue();
        assertThat(RouletteEngine.validSelection(BetType.CORNER, "1-2-4-5")).isTrue();
        assertThat(RouletteEngine.validSelection(BetType.SIX_LINE, "31")).isTrue();
        assertThat(RouletteEngine.validSelection(BetType.COLUMN, "3")).isTrue();
        assertThat(RouletteEngine.validSelection(BetType.DOZEN, "2")).isTrue();
        assertThat(RouletteEngine.validSelection(BetType.RED, "")).isTrue();

        // Bất hợp lệ
        assertThat(RouletteEngine.validSelection(BetType.STRAIGHT, "37")).isFalse();
        assertThat(RouletteEngine.validSelection(BetType.STRAIGHT, "-1")).isFalse();
        assertThat(RouletteEngine.validSelection(BetType.STRAIGHT, "abc")).isFalse();
        assertThat(RouletteEngine.validSelection(BetType.SPLIT, "1-4")).isFalse();   // không kề
        assertThat(RouletteEngine.validSelection(BetType.SPLIT, "1-3")).isFalse();
        assertThat(RouletteEngine.validSelection(BetType.SPLIT, "1-2-4")).isFalse(); // thiếu số
        assertThat(RouletteEngine.validSelection(BetType.STREET, "2")).isFalse();    // không đầu hàng
        assertThat(RouletteEngine.validSelection(BetType.STREET, "35")).isFalse();
        assertThat(RouletteEngine.validSelection(BetType.CORNER, "2-3-5-6")).isFalse();// vắt cột
        assertThat(RouletteEngine.validSelection(BetType.SIX_LINE, "2")).isFalse();
        assertThat(RouletteEngine.validSelection(BetType.COLUMN, "4")).isFalse();
        assertThat(RouletteEngine.validSelection(BetType.DOZEN, "0")).isFalse();
        assertThat(RouletteEngine.validSelection(BetType.RED, "x")).isFalse();
        assertThat(RouletteEngine.validSelection(BetType.RED, null)).isFalse();
    }

    @Test
    void spinStaysWithin0To36() {
        java.util.Random seeded = new java.util.Random(2026);
        for (int i = 0; i < 500; i++) {
            int n = RouletteEngine.spin(seeded);
            assertThat(n).isBetween(0, 36);
        }
    }
}
