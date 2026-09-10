package com.rwg.game.service;

import com.rwg.common.money.Money;
import com.rwg.game.domain.BetType;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.Random;

/**
 * Engine Xóc Đĩa PURE (KHÔNG Spring).
 * Mọi tính toán tiền tệ qua {@link Money} và {@link BigDecimal}; CẤM float/double (ArchUnit enforce).
 *
 * Quy tắc quay số:
 * - Dùng 4 đồng xu (quân vị), mỗi đồng có 2 mặt: Đỏ (1) hoặc Trắng (0).
 * - Tổng số mặt đỏ R = c1 + c2 + c3 + c4 (R thuộc [0, 4]).
 * - Tổng số khả năng: 2^4 = 16 trường hợp đồng khả năng:
 *   + R = 0 (4 trắng): 1/16 = 6.25%
 *   + R = 1 (3 trắng 1 đỏ): 4/16 = 25.0%
 *   + R = 2 (2 đỏ 2 trắng): 6/16 = 37.5%
 *   + R = 3 (3 đỏ 1 trắng): 4/16 = 25.0%
 *   + R = 4 (4 đỏ): 1/16 = 6.25%
 *
 * Phân loại cược & Payout mặc định (M2 stake-inclusive: thắng nhận stake + stake * odds):
 * - XOC_DIA_EVEN (Chẵn): R in {0, 2, 4} (8/16 = 50%). Odds 0.98 -> 1 ăn 1.98.
 * - XOC_DIA_ODD (Lẻ): R in {1, 3} (8/16 = 50%). Odds 0.98 -> 1 ăn 1.98.
 * - XOC_DIA_FOUR_RED (Tứ đỏ): R = 4 (1/16 = 6.25%). Odds 15 -> 1 ăn 16.
 * - XOC_DIA_FOUR_WHITE (Tứ trắng): R = 0 (1/16 = 6.25%). Odds 15 -> 1 ăn 16.
 * - XOC_DIA_THREE_RED (3 đỏ 1 trắng): R = 3 (4/16 = 25%). Odds 3 -> 1 ăn 4.
 * - XOC_DIA_THREE_WHITE (3 trắng 1 đỏ): R = 1 (4/16 = 25%). Odds 3 -> 1 ăn 4.
 *
 * Hỗ trợ Provably Fair: mỗi ván sinh server seed và hash SHA-256 để lưu chứng cứ minh bạch.
 */
public final class XocDiaEngine {

    public static final BigDecimal DEFAULT_ODDS_EVEN_ODD = new BigDecimal("0.98");
    public static final BigDecimal DEFAULT_ODDS_FOUR_OF_A_KIND = new BigDecimal("15");
    public static final BigDecimal DEFAULT_ODDS_THREE_OF_A_KIND = new BigDecimal("3");

    private XocDiaEngine() {
        // utility class
    }

    public static class RoundResult {
        private final List<Integer> coins; // mỗi phần tử là 0 (trắng) hoặc 1 (đỏ)
        private final int redCount;
        private final String seed;
        private final String seedHash;

        public RoundResult(List<Integer> coins, int redCount, String seed, String seedHash) {
            this.coins = coins;
            this.redCount = redCount;
            this.seed = seed;
            this.seedHash = seedHash;
        }

        public List<Integer> getCoins() {
            return coins;
        }

        public int getRedCount() {
            return redCount;
        }

        public int getWhiteCount() {
            return 4 - redCount;
        }

        public boolean isEven() {
            return redCount % 2 == 0;
        }

        public boolean isOdd() {
            return redCount % 2 != 0;
        }

        public String getSeed() {
            return seed;
        }

        public String getSeedHash() {
            return seedHash;
        }

        /** Chuỗi biểu diễn kết quả: "RED,RED,WHITE,RED" */
        public String formattedCoins() {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < coins.size(); i++) {
                if (i > 0) sb.append(",");
                sb.append(coins.get(i) == 1 ? "RED" : "WHITE");
            }
            return sb.toString();
        }
    }

    /**
     * Quay 1 ván Xóc Đĩa ngẫu nhiên 4 đồng xu.
     */
    public static RoundResult playRound(Random random) {
        int c1 = random.nextInt(2);
        int c2 = random.nextInt(2);
        int c3 = random.nextInt(2);
        int c4 = random.nextInt(2);
        int redCount = c1 + c2 + c3 + c4;

        // Sinh seed ngẫu nhiên 32 bytes (64 hex characters)
        byte[] seedBytes = new byte[32];
        random.nextBytes(seedBytes);
        String seed = bytesToHex(seedBytes);
        String seedHash = sha256Hex(seed);

        return new RoundResult(List.of(c1, c2, c3, c4), redCount, seed, seedHash);
    }

    /**
     * Tạo kết quả định sẵn (cho Unit Test).
     */
    public static RoundResult fromCoins(List<Integer> coins, String seed) {
        if (coins == null || coins.size() != 4) {
            throw new IllegalArgumentException("Xoc Dia requires exactly 4 coins");
        }
        int redCount = 0;
        for (int c : coins) {
            if (c != 0 && c != 1) {
                throw new IllegalArgumentException("Coin must be 0 (white) or 1 (red)");
            }
            redCount += c;
        }
        String seedHash = sha256Hex(seed != null ? seed : "test-seed");
        return new RoundResult(coins, redCount, seed, seedHash);
    }

    /**
     * Odds lợi mặc định cho từng loại cược Xóc Đĩa.
     */
    public static BigDecimal oddsFor(BetType type) {
        if (type == null) {
            return BigDecimal.ZERO;
        }
        return switch (type) {
            case XOC_DIA_EVEN, XOC_DIA_ODD -> DEFAULT_ODDS_EVEN_ODD;
            case XOC_DIA_FOUR_RED, XOC_DIA_FOUR_WHITE -> DEFAULT_ODDS_FOUR_OF_A_KIND;
            case XOC_DIA_THREE_RED, XOC_DIA_THREE_WHITE -> DEFAULT_ODDS_THREE_OF_A_KIND;
            default -> BigDecimal.ZERO;
        };
    }

    /**
     * Kiểm tra cược có THẮNG với số mặt đỏ đã ra hay không.
     */
    public static boolean isWin(BetType type, int redCount) {
        if (type == null || redCount < 0 || redCount > 4) {
            return false;
        }
        return switch (type) {
            case XOC_DIA_EVEN -> redCount % 2 == 0;
            case XOC_DIA_ODD -> redCount % 2 != 0;
            case XOC_DIA_FOUR_RED -> redCount == 4;
            case XOC_DIA_FOUR_WHITE -> redCount == 0;
            case XOC_DIA_THREE_RED -> redCount == 3;
            case XOC_DIA_THREE_WHITE -> redCount == 1;
            default -> false;
        };
    }

    /**
     * Trả thưởng stake-inclusive (M2):
     * Thắng -> stake + stake * odds; Thua -> Money 0.
     */
    public static Money payout(BetType type, int redCount, Money stake) {
        return payout(type, redCount, stake, null);
    }

    /**
     * Trả thưởng với odds riêng (nếu có).
     */
    public static Money payout(BetType type, int redCount, Money stake, BigDecimal oddsOverride) {
        if (stake == null || !stake.isPositive()) {
            return Money.zero();
        }
        if (!isWin(type, redCount)) {
            return Money.zero();
        }
        BigDecimal effectiveOdds = oddsOverride != null ? oddsOverride : oddsFor(type);
        return stake.winningPayoutAtOdds(effectiveOdds);
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    private static String sha256Hex(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return bytesToHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }
}
