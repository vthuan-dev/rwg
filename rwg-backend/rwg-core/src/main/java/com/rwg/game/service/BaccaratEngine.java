package com.rwg.game.service;

import com.rwg.common.money.Money;
import com.rwg.game.domain.BetType;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Random;

/**
 * Engine Baccarat PURE (KHÔNG Spring) — Luật Tableau bài thứ ba.
 * Mọi tính toán tiền tệ qua {@link Money}; CẤM float/double (ArchUnit).
 *
 * Quy ước trả thưởng Baccarat (DECISIONS.md):
 * - M2 stake-inclusive payout: Thắng = gốc + lời.
 * - M4 Tie payout chốt 8:1 (stake-inclusive: nhận tổng 9 lần cược).
 * - M6 Banker commission 5% trên tiền lời (Net payout nhận 1.95 lần cược).
 *   Sổ cái ghi nhận dòng debit riêng với loại ref_type = COMMISSION để truy vết.
 * - M7 Pair side-bets (Player/Banker Pair) tỷ lệ 11:1.
 * - Nếu kết quả ván là Hòa (Tie), cược đặt tại PLAYER hoặc BANKER được HOÀN TRẢ lại (payout = stake).
 */
public final class BaccaratEngine {

    private BaccaratEngine() {
        // utility class
    }

    public static class RoundResult {
        private final List<String> playerCards;
        private final List<String> bankerCards;
        private final int playerScore;
        private final int bankerScore;
        private final boolean playerPair;
        private final boolean bankerPair;
        private final String outcome; // "PLAYER", "BANKER", "TIE"

        public RoundResult(List<String> playerCards, List<String> bankerCards,
                           int playerScore, int bankerScore,
                           boolean playerPair, boolean bankerPair, String outcome) {
            this.playerCards = playerCards;
            this.bankerCards = bankerCards;
            this.playerScore = playerScore;
            this.bankerScore = bankerScore;
            this.playerPair = playerPair;
            this.bankerPair = bankerPair;
            this.outcome = outcome;
        }

        public List<String> getPlayerCards() { return playerCards; }
        public List<String> getBankerCards() { return bankerCards; }
        public int getPlayerScore() { return playerScore; }
        public int getBankerScore() { return bankerScore; }
        public boolean isPlayerPair() { return playerPair; }
        public boolean isBankerPair() { return bankerPair; }
        public String getOutcome() { return outcome; }
    }

    /** Sinh một shoe gồm 8 bộ bài (416 lá). */
    public static List<String> createShoe() {
        String[] suits = {"H", "D", "C", "S"};
        String[] ranks = {"A", "2", "3", "4", "5", "6", "7", "8", "9", "10", "J", "Q", "K"};
        List<String> shoe = new ArrayList<>(416);
        for (int deck = 0; deck < 8; deck++) {
            for (String suit : suits) {
                for (String rank : ranks) {
                    shoe.add(suit + rank);
                }
            }
        }
        return shoe;
    }

    /** Trả về giá trị của lá bài trong Baccarat (10/J/Q/K = 0, A = 1, còn lại theo số). */
    public static int getCardValue(String card) {
        if (card == null || card.length() < 2) {
            return 0;
        }
        String rank = card.substring(1);
        return switch (rank) {
            case "A" -> 1;
            case "10", "J", "Q", "K" -> 0;
            default -> {
                try {
                    yield Integer.parseInt(rank);
                } catch (NumberFormatException e) {
                    yield 0;
                }
            }
        };
    }

    /** Tính tổng điểm của danh sách lá bài (lấy chữ số hàng đơn vị). */
    public static int calculateScore(List<String> cards) {
        if (cards == null || cards.isEmpty()) {
            return 0;
        }
        int sum = 0;
        for (String card : cards) {
            sum += getCardValue(card);
        }
        return sum % 10;
    }

    /** Kiểm tra xem 2 lá bài đầu tiên của một cửa có tạo thành đôi không (cùng hạng). */
    public static boolean isPair(List<String> cards) {
        if (cards == null || cards.size() < 2) {
            return false;
        }
        String rank1 = cards.get(0).substring(1);
        String rank2 = cards.get(1).substring(1);
        return rank1.equals(rank2);
    }

    /** Luật rút lá thứ ba của Banker (Tableau). */
    public static boolean shouldBankerDraw(int bankerScore, int playerThirdCardValue) {
        if (bankerScore <= 2) {
            return true;
        }
        if (bankerScore == 3) {
            return playerThirdCardValue != 8;
        }
        if (bankerScore == 4) {
            return playerThirdCardValue >= 2 && playerThirdCardValue <= 7;
        }
        if (bankerScore == 5) {
            return playerThirdCardValue >= 4 && playerThirdCardValue <= 7;
        }
        if (bankerScore == 6) {
            return playerThirdCardValue == 6 || playerThirdCardValue == 7;
        }
        return false; // score >= 7 stands
    }

    /** Giả lập chơi 1 ván Baccarat sử dụng luật Tableau. */
    public static RoundResult playRound(Random random) {
        List<String> shoe = createShoe();
        Collections.shuffle(shoe, random);
        int shoeIdx = 0;

        List<String> playerCards = new ArrayList<>();
        List<String> bankerCards = new ArrayList<>();

        // Chia 2 lá đầu tiên cho Player và Banker
        playerCards.add(shoe.get(shoeIdx++));
        bankerCards.add(shoe.get(shoeIdx++));
        playerCards.add(shoe.get(shoeIdx++));
        bankerCards.add(shoe.get(shoeIdx++));

        int pScore = calculateScore(playerCards);
        int bScore = calculateScore(bankerCards);

        boolean playerPair = isPair(playerCards);
        boolean bankerPair = isPair(bankerCards);

        // Luật Natural: Một trong hai bên đạt 8 hoặc 9 -> Dừng ngay
        if (pScore >= 8 || bScore >= 8) {
            return buildResult(playerCards, bankerCards, pScore, bScore, playerPair, bankerPair);
        }

        // Xét lượt Player rút lá thứ 3
        boolean pDrew = false;
        String pThirdCard = null;
        if (pScore <= 5) {
            pThirdCard = shoe.get(shoeIdx++);
            playerCards.add(pThirdCard);
            pScore = calculateScore(playerCards);
            pDrew = true;
        }

        // Xét lượt Banker rút lá thứ 3
        boolean bDrew = false;
        if (!pDrew) {
            // Player dừng (6,7) -> Banker rút nếu điểm <= 5
            if (bScore <= 5) {
                bankerCards.add(shoe.get(shoeIdx++));
                bDrew = true;
            }
        } else {
            // Player đã rút lá thứ 3 -> Banker xét theo Tableau
            int pThirdVal = getCardValue(pThirdCard);
            if (shouldBankerDraw(bScore, pThirdVal)) {
                bankerCards.add(shoe.get(shoeIdx++));
                bDrew = true;
            }
        }

        if (bDrew) {
            bScore = calculateScore(bankerCards);
        }

        return buildResult(playerCards, bankerCards, pScore, bScore, playerPair, bankerPair);
    }

    private static RoundResult buildResult(List<String> playerCards, List<String> bankerCards,
                                           int playerScore, int bankerScore,
                                           boolean playerPair, boolean bankerPair) {
        String outcome;
        if (playerScore > bankerScore) {
            outcome = "PLAYER";
        } else if (bankerScore > playerScore) {
            outcome = "BANKER";
        } else {
            outcome = "TIE";
        }
        return new RoundResult(playerCards, bankerCards, playerScore, bankerScore, playerPair, bankerPair, outcome);
    }

    /**
     * Tỷ lệ hoa hồng cửa Nhà băng (DECISIONS.md M6): 5% trên TIỀN LỜI của ván thắng.
     *
     * Đặt ở engine, cạnh {@link #oddsFor}, vì hoa hồng là một phần của luật trả tiền cửa
     * Nhà băng. Trước đây con số này nằm rời trong {@code SettlementService}, tách khỏi chỗ
     * sinh ra odds — và đó chính là lý do hai bên lệch nhau mà không ai thấy: công thức cũ
     * trừ trên stake, chỉ trùng kết quả với M6 khi odds đúng bằng 1.
     */
    public static final BigDecimal BANKER_COMMISSION_RATE = new BigDecimal("0.05");

    /**
     * Odds lợi MẶC ĐỊNH cho từng loại cược Baccarat.
     *
     * BANKER trả về 1 (tức 1:1 đầy đủ) chứ không phải 0.95: hoa hồng được thu RIÊNG qua sổ
     * cái commission để truy vết được, xem {@link #commissionFor}. Con số ĐỂ HIỂN THỊ cho
     * người chơi là {@link #netOddsFor}, không phải hàm này.
     */
    public static BigDecimal oddsFor(BetType type) {
        if (type == null) {
            return BigDecimal.ZERO;
        }
        return switch (type) {
            case PLAYER, BANKER -> BigDecimal.ONE;
            case TIE -> new BigDecimal("8");
            case PLAYER_PAIR, BANKER_PAIR -> new BigDecimal("11");
            default -> BigDecimal.ZERO;
        };
    }

    /**
     * Loại cược này có bị thu hoa hồng không?
     *
     * Chỉ cửa Nhà băng. Tách thành hàm riêng để {@code TableOddsService} biết có nên gửi
     * {@code commissionRate} xuống giao diện hay không, mà không phải tự đoán lại luật.
     */
    public static boolean hasCommission(BetType type) {
        return type == BetType.BANKER;
    }

    /**
     * Hoa hồng phải thu của một cược, theo DECISIONS.md M6.
     *
     * Tính trên TIỀN LỜI (stake × odds), KHÔNG trên stake. Hai cách cho cùng kết quả khi
     * odds bằng 1 — mức chung của cửa Nhà băng — nên sai lệch bị che hoàn toàn cho tới khi
     * quản trị đặt tỷ lệ riêng. Ở odds 3, cược 100 lệch tới 10 đơn vị: hạ tỷ lệ thì người
     * chơi bị thu quá, nâng tỷ lệ thì nhà cái thu thiếu.
     *
     * Chỉ thu khi cửa Nhà băng THẮNG. Ván hoà hoàn cược nên không có tiền lời để thu, và
     * thu hoa hồng trên một khoản hoàn lại là lấy bớt tiền gốc của người chơi.
     *
     * @param odds odds lợi đã chốt lúc đặt cược, hoặc null để dùng mức chung
     * @return hoa hồng, hoặc 0 nếu cược này không phải chịu
     */
    public static Money commissionFor(BetType type, String outcome, Money stake, BigDecimal odds) {
        if (!hasCommission(type) || !"BANKER".equals(outcome)) {
            return Money.zero();
        }
        if (stake == null || !stake.isPositive()) {
            return Money.zero();
        }
        BigDecimal effectiveOdds = odds != null ? odds : oddsFor(type);
        if (effectiveOdds.signum() <= 0) {
            return Money.zero();
        }
        return stake.profitAtOdds(effectiveOdds).multiply(BANKER_COMMISSION_RATE);
    }

    /**
     * Odds lợi SAU hoa hồng — con số dùng để hiển thị cho người chơi.
     *
     * Người chơi cần thấy đúng số tiền mình nhận được. Cửa Nhà băng ở mức chung: odds lợi 1
     * nhưng thực nhận tương đương odds 0.95, tức hệ số 1.95 chứ không phải 2.
     *
     * @param odds odds lợi gộp, hoặc null để dùng mức chung
     */
    public static BigDecimal netOddsFor(BetType type, BigDecimal odds) {
        BigDecimal effectiveOdds = odds != null ? odds : oddsFor(type);
        if (!hasCommission(type)) {
            return effectiveOdds;
        }
        return effectiveOdds.multiply(BigDecimal.ONE.subtract(BANKER_COMMISSION_RATE));
    }

    /** Tính tiền thắng/thua hoặc hoàn cược theo quy ước stake-inclusive M2. */
    public static Money payout(BetType type, String outcome, boolean playerPair, boolean bankerPair, Money stake) {
        return payout(type, outcome, playerPair, bankerPair, stake, null);
    }

    /**
     * Payout với odds chỉ định.
     *
     * Trường hợp HOÀ hoàn cược cho PLAYER/BANKER không đi qua odds: hoàn lại đúng số tiền
     * đã đặt là quy tắc của ván hoà, không phải một mức trả thưởng, nên tỷ lệ riêng không
     * được phép làm người chơi nhận về nhiều hay ít hơn tiền gốc.
     *
     * @param oddsOverride odds lợi đã chốt lúc đặt cược, hoặc null để dùng {@link #oddsFor}
     */
    public static Money payout(BetType type, String outcome, boolean playerPair, boolean bankerPair,
                               Money stake, BigDecimal oddsOverride) {
        if (stake == null || !stake.isPositive()) {
            return Money.zero();
        }
        BigDecimal odds = oddsOverride != null ? oddsOverride : oddsFor(type);
        return switch (type) {
            case PLAYER -> {
                if ("PLAYER".equals(outcome)) {
                    yield stake.winningPayoutAtOdds(odds);
                } else if ("TIE".equals(outcome)) {
                    yield stake; // hoà thì hoàn cược
                } else {
                    yield Money.zero();
                }
            }
            case BANKER -> {
                if ("BANKER".equals(outcome)) {
                    // Trả 1:1 đầy đủ; hoa hồng 5% thu riêng qua sổ cái commission ở
                    // SettlementService, nên KHÔNG trừ tại đây.
                    yield stake.winningPayoutAtOdds(odds);
                } else if ("TIE".equals(outcome)) {
                    yield stake; // hoà thì hoàn cược
                } else {
                    yield Money.zero();
                }
            }
            case TIE -> "TIE".equals(outcome) ? stake.winningPayoutAtOdds(odds) : Money.zero();
            case PLAYER_PAIR -> playerPair ? stake.winningPayoutAtOdds(odds) : Money.zero();
            case BANKER_PAIR -> bankerPair ? stake.winningPayoutAtOdds(odds) : Money.zero();
            default -> Money.zero();
        };
    }

    /** Chuẩn hóa selection (rỗng đối với Baccarat). */
    public static String normalize(String selection) {
        return "";
    }

    /** Baccarat bet selection luôn hợp lệ nếu rỗng hoặc trống (vì BetType đã chỉ rõ cửa). */
    public static boolean validSelection(BetType type, String selection) {
        if (type == null) {
            return false;
        }
        return switch (type) {
            case PLAYER, BANKER, TIE, PLAYER_PAIR, BANKER_PAIR -> selection == null || selection.trim().isEmpty();
            default -> false;
        };
    }
}
