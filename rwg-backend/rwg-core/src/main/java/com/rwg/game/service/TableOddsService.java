package com.rwg.game.service;

import com.rwg.common.ApiException;
import com.rwg.common.ErrorCode;
import com.rwg.game.domain.BetType;
import com.rwg.game.domain.GameTable;
import com.rwg.game.domain.GameTableStatus;
import com.rwg.game.dto.BetTypeOddsResponse;
import com.rwg.game.dto.NumberOddsResponse;
import com.rwg.game.dto.TableOddsResponse;
import com.rwg.game.repository.GameTableRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Tỷ lệ cược hiệu lực để hiển thị cho người chơi.
 *
 * Tách khỏi {@link OddsResolver}: resolver trả lời "một cược này trả theo tỷ lệ nào", còn
 * service này trả lời "bàn này có những lựa chọn nào và mỗi lựa chọn tỷ lệ bao nhiêu".
 */
@Service
public class TableOddsService {

    /**
     * Các loại cược hiện trên lưới, theo loại game.
     *
     * KL28_NUMBER KHÔNG có trong danh sách: tỷ lệ của nó phụ thuộc số người chơi đoán
     * (từ 12 tới 999 tuỳ số), nên không xếp vào một lưới cố định được. Tab Special Code
     * cần một cách trả dữ liệu khác.
     */
    private static final List<BetType> KL28_TYPES = List.of(
            BetType.KL28_BIG, BetType.KL28_SMALL, BetType.KL28_SINGLE, BetType.KL28_DOUBLE);

    private static final List<BetType> ROULETTE_TYPES = List.of(
            BetType.RED, BetType.BLACK, BetType.ODD, BetType.EVEN, BetType.LOW, BetType.HIGH);

    private static final List<BetType> BACCARAT_TYPES = List.of(
            BetType.PLAYER, BetType.BANKER, BetType.TIE,
            BetType.PLAYER_PAIR, BetType.BANKER_PAIR);

    /**
     * Cặp hai chiều ĐIỀU CHỈNH ĐƯỢC của mỗi loại game.
     *
     * Mỗi cặp là hai kết quả loại trừ nhau và chia gần đôi không gian kết quả, nên đổi tỷ
     * lệ ở đây là đổi kỳ vọng của người chơi một cách có ý nghĩa. Thứ tự trong danh sách là
     * thứ tự hiện trên màn hình quản trị.
     */
    private static final List<BetType> KL28_ADJUSTABLE = List.of(
            BetType.KL28_BIG, BetType.KL28_SMALL);

    private static final List<BetType> ROULETTE_ADJUSTABLE = List.of(
            BetType.LOW, BetType.HIGH);

    /**
     * Baccarat không có cược lớn/nhỏ, nên dùng cặp tương đương gần nhất.
     *
     * {@code BANKER} bị thu 5% hoa hồng trên tiền lời (DECISIONS.md M6), nên hệ số thực nhận
     * thấp hơn con số người vận hành gõ vào. Phản hồi trả kèm {@code netMultiplier} để màn
     * hình hiện rõ khoảng chênh đó — xem {@code BaccaratEngine.netOddsFor}.
     */
    private static final List<BetType> BACCARAT_ADJUSTABLE = List.of(
            BetType.PLAYER, BetType.BANKER);

    /** Tổng nhỏ nhất và lớn nhất của Lucky 28: ba số 0-9 cộng lại. */
    private static final int MIN_SUM = 0;
    private static final int MAX_SUM = 27;

    private final GameTableRepository tableRepository;
    private final OddsResolver oddsResolver;

    public TableOddsService(GameTableRepository tableRepository, OddsResolver oddsResolver) {
        this.tableRepository = tableRepository;
        this.oddsResolver = oddsResolver;
    }

    /** Các loại cược của một bàn, rỗng nếu loại game không nhận ra. */
    public static List<BetType> betTypesFor(String gameType) {
        if (gameType == null) {
            return List.of();
        }
        return switch (gameType) {
            case "ROULETTE" -> ROULETTE_TYPES;
            case "BACCARAT" -> BACCARAT_TYPES;
            case "KL28", "LUCKY28", "BRITISH_LUCKY28", "TAIWAN_TIMES" -> KL28_TYPES;
            default -> List.of();
        };
    }

    /**
     * Các loại cược ĐIỀU CHỈNH ĐƯỢC tỷ lệ riêng theo người chơi.
     *
     * Hẹp hơn {@link #betTypesFor}: chỉ gồm CẶP HAI CHIỀU chính của mỗi loại game — lớn/nhỏ
     * ở Lucky 28, thấp/cao ở Roulette, người chơi/nhà băng ở Baccarat.
     *
     * Vì sao không cho chỉnh mọi loại cược: các cặp còn lại (lẻ/chẵn, đỏ/đen) phủ CÙNG một
     * không gian kết quả với cặp chính. Hạ tỷ lệ lớn/nhỏ mà để nguyên lẻ/chẵn thì người
     * chơi chỉ cần chuyển sang cặp kia là vô hiệu hoá việc điều chỉnh — nên thêm ô chỉnh
     * cho chúng chỉ tạo cảm giác kiểm soát mà không kiểm soát được gì.
     *
     * Danh sách này KHÔNG ảnh hưởng tới lưới cược của người chơi: người chơi vẫn thấy và
     * cược đủ mọi loại. Nó chỉ giới hạn phạm vi ghi đè của quản trị.
     */
    public static List<BetType> adjustableBetTypesFor(String gameType) {
        if (gameType == null) {
            return List.of();
        }
        return switch (gameType) {
            case "ROULETTE" -> ROULETTE_ADJUSTABLE;
            case "BACCARAT" -> BACCARAT_ADJUSTABLE;
            case "KL28", "LUCKY28", "BRITISH_LUCKY28", "TAIWAN_TIMES" -> KL28_ADJUSTABLE;
            default -> List.of();
        };
    }

    /**
     * Tỷ lệ hiệu lực của một người chơi ở một bàn.
     *
     * Lấy tỷ lệ riêng CẢ BÀN một lượt rồi tra trong bộ nhớ, thay vì mỗi loại cược một
     * truy vấn.
     */
    @Transactional(readOnly = true)
    public TableOddsResponse forUser(UUID userId, UUID tableId) {
        GameTable table = tableRepository.findById(tableId)
                .filter(t -> t.getStatus() == GameTableStatus.ACTIVE)
                .orElseThrow(() -> new ApiException(ErrorCode.GAME_TABLE_NOT_FOUND));

        Map<BetType, BigDecimal> overrides = oddsResolver.overridesFor(userId, tableId);

        List<BetTypeOddsResponse> options = new ArrayList<>();
        for (BetType type : betTypesFor(table.getGameType())) {
            // selection null: bốn cược kết hợp không cần selection, và KL28_NUMBER không
            // nằm trong lưới nên không có trường hợp cần selection ở đây.
            BigDecimal defaultOdds = oddsResolver.defaultOdds(table.getGameType(), type, null);
            BigDecimal odds = overrides.getOrDefault(type, defaultOdds);

            // Hệ số hiển thị phải bằng đúng số tiền người chơi nhận về. Của Nhà băng bị thu
            // 5% hoa hồng ngoài phần trả thưởng, nên odds lợi 1 cho ra 1.95 chứ không phải 2.
            // Trước đây chỗ này trả 2 và người chơi nhận 1.95.
            boolean baccarat = "BACCARAT".equals(table.getGameType());
            BigDecimal netOdds = baccarat ? BaccaratEngine.netOddsFor(type, odds) : odds;
            String commissionRate = baccarat && BaccaratEngine.hasCommission(type)
                    ? BaccaratEngine.BANKER_COMMISSION_RATE.toPlainString()
                    : null;

            options.add(new BetTypeOddsResponse(
                    type.name(),
                    odds.toPlainString(),
                    // Hệ số thực nhận = odds sau hoa hồng + 1 (gồm tiền gốc).
                    netOdds.add(BigDecimal.ONE).toPlainString(),
                    commissionRate,
                    // So bằng compareTo chứ không equals: BigDecimal("0.98") và
                    // BigDecimal("0.9800") KHÁC nhau theo equals vì lệch scale, mà cột
                    // DECIMAL(10,4) luôn trả về scale 4.
                    overrides.containsKey(type) && odds.compareTo(defaultOdds) != 0));
        }

        return new TableOddsResponse(
                table.getId().toString(),
                table.getGameType(),
                options,
                numberOddsFor(table.getGameType()));
    }

    /**
     * Tỷ lệ từng tổng 0-27, cho tab Special Code.
     *
     * Rỗng ở bàn không phải Lucky 28. Đọc từ {@link Kl28Engine} chứ không sao lại bảng 28
     * số: sao lại thì bản ở đây lệch với bản dùng để trả tiền ngay lần đầu ai đó sửa một
     * bên, và bên lệch chính là bên hiển thị cho người chơi.
     *
     * KHÔNG áp tỷ lệ riêng: {@code KL28_NUMBER} không nằm trong {@link #betTypesFor} nên
     * {@code AdminUserOddsService.setOdds} từ chối ghi tỷ lệ riêng cho nó. Trả mức chung ở
     * đây là đúng với con số thực dùng lúc thanh toán.
     */
    private List<NumberOddsResponse> numberOddsFor(String gameType) {
        if (!KL28_TYPES.equals(betTypesFor(gameType))) {
            return List.of();
        }
        List<NumberOddsResponse> out = new ArrayList<>(MAX_SUM - MIN_SUM + 1);
        for (int sum = MIN_SUM; sum <= MAX_SUM; sum++) {
            BigDecimal odds = Kl28Engine.defaultNumberOdds(sum);
            out.add(new NumberOddsResponse(
                    sum,
                    odds.toPlainString(),
                    odds.add(BigDecimal.ONE).toPlainString()));
        }
        return out;
    }
}
