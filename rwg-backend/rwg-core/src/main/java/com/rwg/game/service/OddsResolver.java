package com.rwg.game.service;

import com.rwg.game.domain.BetType;
import com.rwg.game.domain.GameTable;
import com.rwg.game.domain.UserGameOdds;
import com.rwg.game.repository.UserGameOddsRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Tra tỷ lệ cược HIỆU LỰC cho một người chơi ở một bàn.
 *
 * Đây là chỗ DUY NHẤT được phép quyết định một cược trả theo tỷ lệ nào. Nếu logic này
 * nằm rải rác ở nhiều nơi thì màn hình hiển thị một con số và lúc thanh toán tính một con
 * số khác — nghĩa là thu tiền theo một tỷ lệ rồi trả theo tỷ lệ khác.
 *
 * Không có bản ghi riêng thì rơi về mặc định của engine tương ứng.
 */
@Service
public class OddsResolver {

    /**
     * Biên an toàn quanh mức chung.
     *
     * Gõ nhầm 19.8 thay vì 1.98 sẽ trả gấp mười lần — với cược lớn là mất một khoản đáng
     * kể ngay ván đó, và không có cách nào lấy lại tiền đã trả. Chặn ở tầng nghiệp vụ chứ
     * không chỉ ở giao diện, vì API vẫn gọi trực tiếp được.
     */
    public static final BigDecimal MIN_RATIO = new BigDecimal("0.5");
    public static final BigDecimal MAX_RATIO = new BigDecimal("3");

    private final UserGameOddsRepository repo;

    public OddsResolver(UserGameOddsRepository repo) {
        this.repo = repo;
    }

    /**
     * Odds MẶC ĐỊNH của một loại cược, không xét tỷ lệ riêng.
     *
     * Ba engine mỗi cái một cách khai báo nên phải phân nhánh theo loại bàn. Đọc từ chính
     * engine chứ không sao lại con số: sao lại thì hai bản lệch nhau ngay lần đầu ai đó
     * sửa một bên, và bên lệch sẽ là bên hiển thị cho người chơi.
     */
    public BigDecimal defaultOdds(String gameType, BetType betType, String selection) {
        if (gameType == null || betType == null) {
            return BigDecimal.ZERO;
        }
        return switch (gameType) {
            case "ROULETTE" -> RouletteEngine.oddsFor(betType);
            case "BACCARAT" -> BaccaratEngine.oddsFor(betType);
            // Lucky 28 và ba biến thể dùng cùng một engine.
            case "KL28", "LUCKY28", "BRITISH_LUCKY28", "TAIWAN_TIMES" ->
                    Kl28Engine.defaultOddsFor(betType, selection);
            default -> BigDecimal.ZERO;
        };
    }

    /**
     * Odds hiệu lực cho MỘT cược.
     *
     * @return odds riêng nếu người chơi có, ngược lại mặc định engine
     */
    public BigDecimal effectiveOdds(UUID userId, GameTable table, BetType betType, String selection) {
        BigDecimal fallback = defaultOdds(table.getGameType(), betType, selection);
        return repo.findByUserIdAndTableIdAndBetType(userId, table.getId(), betType)
                .map(UserGameOdds::getOdds)
                .orElse(fallback);
    }

    /**
     * Mọi odds riêng của một người ở một bàn, dạng tra nhanh.
     *
     * Dùng cho thanh toán: lấy CẢ BÀN một lượt rồi tra trong bộ nhớ, thay vì mỗi cược một
     * truy vấn. Pha thanh toán chỉ có 5 giây và một ván có thể có hàng trăm cược.
     *
     * @return map rỗng nếu người chơi không có tỷ lệ riêng nào ở bàn này
     */
    public Map<BetType, BigDecimal> overridesFor(UUID userId, UUID tableId) {
        List<UserGameOdds> rows = repo.findByUserIdAndTableId(userId, tableId);
        if (rows.isEmpty()) {
            return Map.of();
        }
        Map<BetType, BigDecimal> out = new EnumMap<>(BetType.class);
        for (UserGameOdds row : rows) {
            out.put(row.getBetType(), row.getOdds());
        }
        return out;
    }

    /**
     * Odds riêng của NHIỀU người ở một bàn.
     *
     * Thanh toán một ván chạy qua cược của nhiều người khác nhau. Gọi
     * {@link #overridesFor} cho từng người sẽ thành N truy vấn; hàm này gộp thành một.
     *
     * Kiểm {@code existsAnyForTable} trước để bỏ qua hẳn phần này ở những bàn không ai có
     * tỷ lệ riêng — tức là phần lớn trường hợp trong thực tế.
     */
    public Map<UUID, Map<BetType, BigDecimal>> overridesForTable(UUID tableId, List<UUID> userIds) {
        if (userIds.isEmpty() || !repo.existsAnyForTable(tableId)) {
            return Map.of();
        }
        Map<UUID, Map<BetType, BigDecimal>> out = new HashMap<>();
        for (UUID userId : userIds) {
            Map<BetType, BigDecimal> rows = overridesFor(userId, tableId);
            if (!rows.isEmpty()) {
                out.put(userId, rows);
            }
        }
        return out;
    }

    /**
     * Tỷ lệ có nằm trong biên an toàn quanh mức chung?
     *
     * @param odds giá trị quản trị muốn đặt
     * @param defaultOdds mức chung của loại cược đó
     */
    public boolean withinSafeRange(BigDecimal odds, BigDecimal defaultOdds) {
        if (odds == null || odds.signum() <= 0) {
            return false;
        }
        // Mức chung bằng 0 nghĩa là loại cược không áp dụng cho bàn này, không có gì để so.
        if (defaultOdds == null || defaultOdds.signum() <= 0) {
            return false;
        }
        BigDecimal min = defaultOdds.multiply(MIN_RATIO);
        BigDecimal max = defaultOdds.multiply(MAX_RATIO);
        return odds.compareTo(min) >= 0 && odds.compareTo(max) <= 0;
    }
}
