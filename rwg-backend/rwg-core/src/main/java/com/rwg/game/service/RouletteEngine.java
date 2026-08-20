package com.rwg.game.service;

import com.rwg.common.money.Money;
import com.rwg.game.domain.BetType;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.Random;
import java.util.Set;

/**
 * Engine Roulette PURE (KHÔNG Spring) — European single-zero 0-36.
 * Mọi payout CHỈ qua {@link Money}; CẤM float/double (ArchUnit enforce package ..game..).
 *
 * Quy ước selection (chuẩn hóa, phân tách bằng "-"):
 * STRAIGHT "17" | SPLIT "17-20" (2 số kề nhau theo quy ước bàn RWG: ngang cùng hàng,
 * dọc CỘT GIỮA và 0-1/0-2/0-3 — khóa bởi bộ test RoulettePayoutsTest)
 * STREET "1" (số ĐẦU hàng: 1,4,...,34) | CORNER "1-2-4-5" (4 số vuông, ascending)
 * SIX_LINE "1" (số đầu: 1,7,...,31) | COLUMN/DOZEN "1"|"2"|"3"
 * Cược ngoài (RED/BLACK/ODD/EVEN/LOW/HIGH): selection rỗng.
 *
 * Quy ước trả thưởng M2 (DECISIONS.md): stake-inclusive —
 * thắng nhận stake + stake × odds qua {@link Money#winningPayoutAtOdds(BigDecimal)}.
 * KHÔNG copy pseudocode từ LOGIC-GAME-VA-HOA-HONG.md (tài liệu có lỗi).
 */
public final class RouletteEngine {

    /** Các số ĐỎ trên bàn Roulette European. */
    public static final Set<Integer> RED_NUMBERS = Set.of(
            1, 3, 5, 7, 9, 12, 14, 16, 18, 19, 21, 23, 25, 27, 30, 32, 34, 36);

    public static final int MIN_NUMBER = 0;
    public static final int MAX_NUMBER = 36;

    private RouletteEngine() {
        // utility class
    }

    /** Quay số 0-36. Prod truyền SecureRandom; test truyền Random seed cố định. */
    public static int spin(Random random) {
        return random.nextInt(MAX_NUMBER + 1);
    }

    /** Odds lời cho từng loại cược (35:1 -> 35). */
    public static BigDecimal oddsFor(BetType type) {
        return switch (type) {
            case STRAIGHT -> new BigDecimal("35");
            case SPLIT -> new BigDecimal("17");
            case STREET -> new BigDecimal("11");
            case CORNER -> new BigDecimal("8");
            case SIX_LINE -> new BigDecimal("5");
            case COLUMN, DOZEN -> new BigDecimal("2");
            case RED, BLACK, ODD, EVEN, LOW, HIGH -> BigDecimal.ONE;
            default -> BigDecimal.ZERO;
        };
    }

    /**
     * Payout stake-inclusive (M2): thắng -> stake + stake × odds; thua -> Money 0.
     * Selection bất hợp lệ hoặc số ngoài 0-36 -> coi như THUA (0) cho an toàn;
     * validation đúng/sai thuộc {@link #validSelection(BetType, String)} phía nhận cược.
     */
    public static Money payout(BetType type, String selection, int number, Money stake) {
        if (stake == null || !stake.isPositive()) {
            return Money.zero();
        }
        if (!isWin(type, selection, number)) {
            return Money.zero();
        }
        return stake.winningPayoutAtOdds(oddsFor(type));
    }

    /** Cược có THẮNG với số vừa quay? Số 0 chỉ thắng straight-0. */
    public static boolean isWin(BetType type, String selection, int number) {
        if (number < MIN_NUMBER || number > MAX_NUMBER) {
            return false;
        }
        return switch (type) {
            case STRAIGHT -> parseSingle(selection) == number;
            case SPLIT -> containsNumber(selection, number, 2);
            case STREET -> {
                int start = parseSingle(selection);
                yield start >= 1 && start % 3 == 1 && number >= start && number <= start + 2;
            }
            case CORNER -> containsNumber(selection, number, 4);
            case SIX_LINE -> {
                int start = parseSingle(selection);
                yield start >= 1 && (start - 1) % 6 == 0 && number >= start && number <= start + 5;
            }
            case COLUMN -> {
                int column = parseSingle(selection);
                yield number >= 1 && column >= 1 && column <= 3
                        && number % 3 == (column == 3 ? 0 : column);
            }
            case DOZEN -> {
                int dozen = parseSingle(selection);
                yield number >= 1 && dozen >= 1 && dozen <= 3
                        && number >= (dozen - 1) * 12 + 1 && number <= dozen * 12;
            }
            case RED -> RED_NUMBERS.contains(number);
            case BLACK -> number >= 1 && !RED_NUMBERS.contains(number);
            case ODD -> number >= 1 && number % 2 == 1;
            case EVEN -> number >= 1 && number % 2 == 0;
            case LOW -> number >= 1 && number <= 18;
            case HIGH -> number >= 19 && number <= 36;
            default -> false;
        };
    }

    /** Validate selection theo loại cược (nhận cược từ chối selection bất hợp lệ; null = bất hợp lệ). */
    public static boolean validSelection(BetType type, String selection) {
        if (type == null || selection == null) {
            return false;
        }
        String sel = selection.trim();
        try {
            return switch (type) {
                case STRAIGHT -> inRange(parseSingle(sel), 0, 36);
                case SPLIT -> validSplit(sel);
                case STREET -> {
                    int s = parseSingle(sel);
                    yield s >= 1 && s <= 34 && s % 3 == 1;
                }
                case CORNER -> validCorner(sel);
                case SIX_LINE -> {
                    int s = parseSingle(sel);
                    yield s >= 1 && s <= 31 && (s - 1) % 6 == 0;
                }
                case COLUMN, DOZEN -> {
                    int v = parseSingle(sel);
                    yield v >= 1 && v <= 3;
                }
                case RED, BLACK, ODD, EVEN, LOW, HIGH -> sel.isEmpty();
                default -> false;
            };
        } catch (NumberFormatException e) {
            return false;
        }
    }

    // ===== helpers =====

    /** Parse "17" -> 17; sai định dạng ném NumberFormatException. */
    private static int parseSingle(String selection) {
        return Integer.parseInt(selection.trim());
    }

    private static boolean inRange(int v, int lo, int hi) {
        return v >= lo && v <= hi;
    }

    /** Selection dạng danh sách "a-b-..." có chứa number và đủ expectedCount số? */
    private static boolean containsNumber(String selection, int number, int expectedCount) {
        if (selection == null) {
            return false;
        }
        String[] parts = selection.trim().split("-");
        if (parts.length != expectedCount) {
            return false;
        }
        try {
            for (String part : parts) {
                if (Integer.parseInt(part.trim()) == number) {
                    return true;
                }
            }
        } catch (NumberFormatException e) {
            return false;
        }
        return false;
    }

    /**
     * Split hợp lệ theo quy ước bàn RWG (khóa bởi RoulettePayoutsTest):
     * 2 số phân biệt — ngang cùng hàng (±1), dọc CỘT GIỮA (số nhỏ %3==2, vd 17-20),
     * hoặc 0 với 1/2/3. Vd "1-4" (dọc cột biên) KHÔNG hợp lệ theo quy ước này.
     */
    private static boolean validSplit(String sel) {
        String[] parts = sel.split("-");
        if (parts.length != 2) {
            return false;
        }
        int a = Integer.parseInt(parts[0].trim());
        int b = Integer.parseInt(parts[1].trim());
        if (a == b || !inRange(a, 0, 36) || !inRange(b, 0, 36)) {
            return false;
        }
        int lo = Math.min(a, b);
        int hi = Math.max(a, b);
        if (lo == 0) {
            return hi == 1 || hi == 2 || hi == 3; // split với số 0
        }
        if (hi - lo == 3) {
            return lo % 3 == 2; // dọc CHỈ ở cột giữa (quy ước bàn RWG)
        }
        // kề ngang: chênh 1 và cùng hàng (không vắt qua cột).
        return hi - lo == 1 && (lo - 1) / 3 == (hi - 1) / 3;
    }

    /** Corner hợp lệ: đúng 4 số {n, n+1, n+3, n+4} với n đầu ô vuông (n%3==1, n+4<=36). */
    private static boolean validCorner(String sel) {
        String[] parts = sel.split("-");
        if (parts.length != 4) {
            return false;
        }
        int[] nums = new int[4];
        for (int i = 0; i < 4; i++) {
            nums[i] = Integer.parseInt(parts[i].trim());
            if (!inRange(nums[i], 1, 36)) {
                return false;
            }
        }
        java.util.Arrays.sort(nums);
        int n = nums[0];
        return n % 3 == 1 && n + 4 <= 36
                && nums[1] == n + 1 && nums[2] == n + 3 && nums[3] == n + 4;
    }

    /** Chuẩn hóa selection (trim, lowercase) trước khi lưu — giữ nguyên dạng số. */
    public static String normalize(String selection) {
        return selection == null ? "" : selection.trim().toLowerCase(Locale.ROOT);
    }
}
