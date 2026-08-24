package com.rwg.game.service;

import com.rwg.common.money.Money;
import com.rwg.game.domain.BetType;

import java.math.BigDecimal;
import java.util.List;
import java.util.Random;

/**
 * Engine Korean Lucky 28 (KL28) PURE (KHÔNG Spring).
 * Mọi tính toán tiền tệ qua {@link Money}; cấm float/double.
 *
 * Quy tắc quay số:
 * - Sinh 3 số ngẫu nhiên từ 0-9 (n1, n2, n3).
 * - Tổng điểm: S = n1 + n2 + n3. Tổng điểm nằm trong khoảng [0, 27].
 * - Kết quả hiển thị cho người chơi gồm danh sách 3 số (vd: "2,7,5") và tổng điểm (vd: 14).
 *
 * Các loại cược & Payout (odds 0.98:1, stake-inclusive):
 * - KL28_BIG: Thắng khi tổng điểm S >= 14. Payout = stake * 1.98.
 * - KL28_SMALL: Thắng khi tổng điểm S <= 13. Payout = stake * 1.98.
 * - KL28_SINGLE: Thắng khi tổng điểm S là số lẻ. Payout = stake * 1.98.
 * - KL28_DOUBLE: Thắng khi tổng điểm S là số chẵn. Payout = stake * 1.98.
 * - KL28_NUMBER: Thắng khi đoán trúng chính xác tổng điểm. Tỷ lệ thưởng theo bảng thống kê xác suất.
 */
public final class Kl28Engine {

    /** Odds lời (net payout odds) cho từng số từ 0 đến 27. */
    private static final BigDecimal[] NUMBER_ODDS = {
        new BigDecimal("999"),  // 0
        new BigDecimal("332"),  // 1
        new BigDecimal("165"),  // 2
        new BigDecimal("99"),   // 3
        new BigDecimal("65"),   // 4
        new BigDecimal("46"),   // 5
        new BigDecimal("34"),   // 6
        new BigDecimal("26"),   // 7
        new BigDecimal("21"),   // 8
        new BigDecimal("17"),   // 9
        new BigDecimal("14"),   // 10
        new BigDecimal("13"),   // 11
        new BigDecimal("12"),   // 12
        new BigDecimal("12"),   // 13
        new BigDecimal("12"),   // 14
        new BigDecimal("12"),   // 15
        new BigDecimal("13"),   // 16
        new BigDecimal("14"),   // 17
        new BigDecimal("17"),   // 18
        new BigDecimal("21"),   // 19
        new BigDecimal("26"),   // 20
        new BigDecimal("34"),   // 21
        new BigDecimal("46"),   // 22
        new BigDecimal("65"),   // 23
        new BigDecimal("99"),   // 24
        new BigDecimal("165"),  // 25
        new BigDecimal("332"),  // 26
        new BigDecimal("999")   // 27
    };

    private Kl28Engine() {
        // utility class
    }

    public static class RoundResult {
        private final List<Integer> numbers;
        private final int sum;

        public RoundResult(List<Integer> numbers, int sum) {
            this.numbers = numbers;
            this.sum = sum;
        }

        public List<Integer> getNumbers() { return numbers; }
        public int getSum() { return sum; }
    }

    /** Giả lập quay 1 ván Korean Lucky 28. */
    public static RoundResult playRound(Random random) {
        int n1 = random.nextInt(10);
        int n2 = random.nextInt(10);
        int n3 = random.nextInt(10);
        int sum = n1 + n2 + n3;
        return new RoundResult(List.of(n1, n2, n3), sum);
    }

    /**
     * Odds lợi MẶC ĐỊNH cho bốn cược kết hợp (Lớn, Nhỏ, Lẻ, Chẵn).
     *
     * 0.98 tương đương hệ số trả 1.98: cược 100 thắng nhận 198.
     */
    public static final BigDecimal DEFAULT_COMBINATION_ODDS = new BigDecimal("0.98");

    /**
     * Odds lợi mặc định khi đoán đúng tổng {@code sum}.
     *
     * Mở ra ngoài để tầng trên tra được mức chung mà không phải sao lại bảng 28 số.
     * Sao lại thì hai bản sẽ lệch nhau ngay lần đầu ai đó sửa một bên.
     *
     * @return odds lợi, hoặc 0 nếu {@code sum} ngoài 0-27
     */
    public static BigDecimal defaultNumberOdds(int sum) {
        if (sum < 0 || sum >= NUMBER_ODDS.length) {
            return BigDecimal.ZERO;
        }
        return NUMBER_ODDS[sum];
    }

    /**
     * Odds lợi mặc định cho một cược, biết trước lúc đặt.
     *
     * Với {@code KL28_NUMBER}, odds phụ thuộc số người chơi ĐOÁN chứ không phụ thuộc kết
     * quả, nên xác định được ngay khi nhận cược — điều kiện cần để chốt odds vào bản ghi.
     */
    public static BigDecimal defaultOddsFor(BetType type, String selection) {
        if (type == null) {
            return BigDecimal.ZERO;
        }
        return switch (type) {
            case KL28_BIG, KL28_SMALL, KL28_SINGLE, KL28_DOUBLE -> DEFAULT_COMBINATION_ODDS;
            case KL28_NUMBER -> {
                try {
                    yield defaultNumberOdds(Integer.parseInt(normalize(selection)));
                } catch (NumberFormatException e) {
                    yield BigDecimal.ZERO;
                }
            }
            default -> BigDecimal.ZERO;
        };
    }

    /** Tính tiền thắng/thua theo tỷ lệ odds mặc định của Lucky 28. */
    public static Money payout(BetType type, String selection, int sum, Money stake) {
        return payout(type, selection, sum, stake, null);
    }

    /**
     * Tính tiền thắng/thua với odds chỉ định.
     *
     * @param oddsOverride odds lợi đã chốt lúc đặt cược, hoặc null để dùng mức mặc định.
     *     Cược đặt trước khi có tính năng tỷ lệ riêng có cột này null, nên phải rơi về
     *     mặc định thay vì coi là odds 0 và trả trắng cho người thắng.
     */
    public static Money payout(BetType type, String selection, int sum, Money stake,
                               BigDecimal oddsOverride) {
        if (stake == null || !stake.isPositive()) {
            return Money.zero();
        }
        boolean won = switch (type) {
            case KL28_BIG -> sum >= 14;
            case KL28_SMALL -> sum <= 13;
            case KL28_SINGLE -> sum % 2 != 0;
            case KL28_DOUBLE -> sum % 2 == 0;
            case KL28_NUMBER -> {
                try {
                    yield Integer.parseInt(selection) == sum;
                } catch (NumberFormatException e) {
                    yield false;
                }
            }
            default -> false;
        };

        if (!won) {
            return Money.zero();
        }

        if (oddsOverride != null) {
            return stake.winningPayoutAtOdds(oddsOverride);
        }

        if (type == BetType.KL28_NUMBER) {
            BigDecimal odds = defaultNumberOdds(sum);
            return odds.signum() == 0 ? Money.zero() : stake.winningPayoutAtOdds(odds);
        }
        return stake.winningPayoutAtOdds(DEFAULT_COMBINATION_ODDS);
    }

    /** Chuẩn hóa selection (giữ nguyên cho KL28_NUMBER, trim). */
    public static String normalize(String selection) {
        return selection == null ? "" : selection.trim();
    }

    /** Hợp lệ nếu selection rỗng (với cược kết hợp) hoặc là số từ 0-27 (với cược đặc biệt). */
    public static boolean validSelection(BetType type, String selection) {
        if (type == null) {
            return false;
        }
        return switch (type) {
            case KL28_BIG, KL28_SMALL, KL28_SINGLE, KL28_DOUBLE -> selection == null || selection.trim().isEmpty();
            case KL28_NUMBER -> {
                if (selection == null || selection.trim().isEmpty()) {
                    yield false;
                }
                try {
                    int val = Integer.parseInt(selection.trim());
                    yield val >= 0 && val <= 27;
                } catch (NumberFormatException e) {
                    yield false;
                }
            }
            default -> false;
        };
    }
}
