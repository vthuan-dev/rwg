package com.rwg.game.service;

import com.rwg.common.ApiException;
import com.rwg.common.ErrorCode;
import com.rwg.game.domain.BetType;
import com.rwg.game.domain.GameTable;
import com.rwg.game.domain.GameTableStatus;
import com.rwg.game.domain.UserGameOdds;
import com.rwg.game.dto.SetUserOddsRequest;
import com.rwg.game.dto.UserOddsOptionResponse;
import com.rwg.game.dto.UserOddsResponse;
import com.rwg.game.dto.UserTableOddsResponse;
import com.rwg.game.repository.GameTableRepository;
import com.rwg.game.repository.UserGameOddsRepository;
import com.rwg.identity.domain.User;
import com.rwg.identity.repository.UserRepository;
import com.rwg.identity.service.AuditTrailService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Quản trị tỷ lệ cược riêng theo người chơi.
 *
 * Mọi thao tác GHI đều ghi audit kèm giá trị cũ và mới. Đây là đường ảnh hưởng tiền chi
 * trả mà KHÔNG đi qua sổ điều chỉnh ví: nâng tỷ lệ của một tài khoản rồi để tài khoản đó
 * cược và thắng sẽ không xuất hiện trong bất kỳ báo cáo điều chỉnh ví nào. Vết audit là
 * cách duy nhất để phát hiện về sau.
 */
@Service
public class AdminUserOddsService {

    private final UserGameOddsRepository oddsRepository;
    private final GameTableRepository tableRepository;
    private final UserRepository userRepository;
    private final OddsResolver oddsResolver;
    private final AuditTrailService audit;

    public AdminUserOddsService(UserGameOddsRepository oddsRepository,
                                GameTableRepository tableRepository,
                                UserRepository userRepository,
                                OddsResolver oddsResolver,
                                AuditTrailService audit) {
        this.oddsRepository = oddsRepository;
        this.tableRepository = tableRepository;
        this.userRepository = userRepository;
        this.oddsResolver = oddsResolver;
        this.audit = audit;
    }

    /**
     * Bảng tỷ lệ đầy đủ của một người chơi.
     *
     * Trả MỌI bàn và MỌI loại cược kèm mức chung, không chỉ các dòng đã ghi đè: người vận
     * hành cần thấy mức chung để biết đang đổi từ đâu sang đâu.
     */
    @Transactional(readOnly = true)
    public UserOddsResponse oddsOf(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND));

        // Lấy TẤT CẢ bản ghi riêng của người này một lượt, rồi tra trong bộ nhớ. Mỗi bàn
        // một truy vấn sẽ thành 6 lượt đi lại chỉ để vẽ một hộp thoại.
        Map<UUID, Map<BetType, UserGameOdds>> byTable = new HashMap<>();
        for (UserGameOdds row : oddsRepository.findByUserIdOrderByTableIdAscBetTypeAsc(userId)) {
            byTable.computeIfAbsent(row.getTableId(), k -> new HashMap<>())
                    .put(row.getBetType(), row);
        }

        List<UserTableOddsResponse> tables = new ArrayList<>();

        // Sắp xếp TRƯỚC khi dựng phản hồi, không để nguyên thứ tự DB trả về.
        //
        // `findByStatus` không có ORDER BY nên thứ tự do MySQL quyết định và có thể đổi giữa
        // các lần chạy. Sắp ở đây để màn hình quản trị luôn cùng một thứ tự — người vận hành
        // nhớ vị trí theo trí nhớ cơ, thứ tự nhảy là nguồn của thao tác sai bàn.
        List<GameTable> ordered = new ArrayList<>(
                tableRepository.findByStatus(GameTableStatus.ACTIVE));
        ordered.sort(Comparator
                .comparingInt((GameTable gt) -> gameTypeRank(gt.getGameType()))
                .thenComparing(gt -> gt.getId().toString()));

        for (GameTable table : ordered) {
            // Chỉ cặp hai chiều chính, không phải mọi loại cược của bàn — xem
            // {@code adjustableBetTypesFor} để biết vì sao lại hẹp hơn lưới của người chơi.
            List<BetType> types = TableOddsService.adjustableBetTypesFor(table.getGameType());
            if (types.isEmpty()) {
                continue;
            }

            Map<BetType, UserGameOdds> overrides =
                    byTable.getOrDefault(table.getId(), Map.of());

            List<UserOddsOptionResponse> options = new ArrayList<>();
            for (BetType type : types) {
                BigDecimal defaultOdds = oddsResolver.defaultOdds(table.getGameType(), type, null);
                UserGameOdds row = overrides.get(type);
                BigDecimal effectiveOdds = row != null ? row.getOdds() : defaultOdds;

                // Hệ số THỰC NHẬN của người chơi. Ô nhập của quản trị dùng hệ số gộp
                // (odds + 1) cho MỌI bàn để đơn vị không đổi giữa các bàn, nhưng ở cửa có
                // hoa hồng thì con số gõ vào khác con số người chơi nhận — trả trường này
                // để màn hình nói rõ khoảng chênh thay vì để người vận hành tự suy.
                boolean baccarat = "BACCARAT".equals(table.getGameType());
                BigDecimal netOdds = baccarat
                        ? BaccaratEngine.netOddsFor(type, effectiveOdds)
                        : effectiveOdds;
                String commissionRate = baccarat && BaccaratEngine.hasCommission(type)
                        ? BaccaratEngine.BANKER_COMMISSION_RATE.toPlainString()
                        : null;

                options.add(new UserOddsOptionResponse(
                        type.name(),
                        defaultOdds.toPlainString(),
                        effectiveOdds.toPlainString(),
                        netOdds.add(BigDecimal.ONE).toPlainString(),
                        commissionRate,
                        row != null,
                        row != null ? row.getReason() : null,
                        row != null ? row.getUpdatedAt() : null));
            }

            tables.add(new UserTableOddsResponse(
                    table.getId().toString(),
                    table.getGameType(),
                    table.getNameI18n(),
                    options));
        }

        return new UserOddsResponse(userId.toString(), user.getUsername(), tables);
    }

    /**
     * Thứ tự hiện bàn trên màn hình quản trị. Số nhỏ lên trước.
     *
     * Bốn bàn họ Lucky 28 lên đầu vì đó là nơi điều chỉnh tỷ lệ nhiều nhất, và đặt chúng
     * cạnh nhau giúp so mức giữa bốn bàn cùng engine mà không phải cuộn qua Roulette với
     * Baccarat ở giữa.
     *
     * Loại game lạ nhận hạng lớn nhất để rơi xuống cuối thay vì lẫn vào giữa danh sách.
     */
    private static int gameTypeRank(String gameType) {
        if (gameType == null) {
            return Integer.MAX_VALUE;
        }
        return switch (gameType) {
            case "LUCKY28" -> 0;
            case "KL28" -> 1;
            case "BRITISH_LUCKY28" -> 2;
            case "TAIWAN_TIMES" -> 3;
            case "ROULETTE" -> 4;
            case "BACCARAT" -> 5;
            default -> Integer.MAX_VALUE;
        };
    }

    /**
     * Đặt hoặc đổi tỷ lệ riêng.
     *
     * Chặn ba thứ trước khi ghi: admin tự sửa cho mình, loại cược không thuộc bàn, và giá
     * trị ngoài biên an toàn.
     */
    @Transactional
    public void setOdds(UUID userId, UUID adminId, SetUserOddsRequest request, String ip) {
        requireNotSelfDealing(userId, adminId, ip);

        if (!userRepository.existsById(userId)) {
            throw new ApiException(ErrorCode.NOT_FOUND);
        }

        UUID tableId = parseUuid(request.tableId());
        GameTable table = tableRepository.findById(tableId)
                .filter(t -> t.getStatus() == GameTableStatus.ACTIVE)
                .orElseThrow(() -> new ApiException(ErrorCode.GAME_TABLE_NOT_FOUND));

        BetType betType = parseBetType(request.betType());

        // Loại cược phải THUỘC danh sách điều chỉnh được của bàn này. Thiếu kiểm tra thì
        // đặt được tỷ lệ RED cho bàn Lucky 28 — bản ghi đó vô hại nhưng sẽ hiện trong màn
        // hình quản trị như một dòng có hiệu lực, khiến người vận hành tin là đã cấu hình xong.
        //
        // Dùng danh sách HẸP (điều chỉnh được) chứ không phải toàn bộ loại cược: giới hạn ở
        // tầng nghiệp vụ, không chỉ ẩn bớt trên giao diện — API vẫn gọi trực tiếp được.
        if (!TableOddsService.adjustableBetTypesFor(table.getGameType()).contains(betType)) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, null, null,
                    "validation.admin.odds.bet_type.not_in_table");
        }

        BigDecimal odds = parseOdds(request.odds());
        BigDecimal defaultOdds = oddsResolver.defaultOdds(table.getGameType(), betType, null);

        if (!oddsResolver.withinSafeRange(odds, defaultOdds)) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, null, null,
                    "validation.admin.odds.out_of_range");
        }

        Optional<UserGameOdds> existing =
                oddsRepository.findByUserIdAndTableIdAndBetType(userId, tableId, betType);

        BigDecimal previous = existing.map(UserGameOdds::getOdds).orElse(defaultOdds);

        // Chuẩn hoá lý do trống thành null thay vì lưu chuỗi rỗng: cột nullable, và hai cách
        // biểu diễn cùng một ý nghĩa sẽ buộc mọi chỗ đọc phải kiểm cả hai.
        String reason = (request.reason() == null || request.reason().isBlank())
                ? null
                : request.reason().trim();

        if (existing.isPresent()) {
            existing.get().update(odds, reason, adminId);
        } else {
            oddsRepository.save(new UserGameOdds(
                    userId, tableId, betType, odds, reason, adminId));
        }

        // `Map.of` NÉM NullPointerException nếu giá trị null, mà `reason` giờ được phép null.
        // Dùng chuỗi rỗng trong bản ghi audit — audit là nhật ký đọc bằng mắt, một ô trống ở
        // đó rõ nghĩa hơn là để cả lệnh ghi audit sập và mất luôn dấu vết của thao tác.
        audit.record(adminId, null, AuditTrailService.ADMIN_USER_ODDS_CHANGED,
                "USER", userId.toString(),
                Map.of("tableId", tableId.toString(),
                        "gameType", table.getGameType(),
                        "betType", betType.name(),
                        "oddsBefore", previous.toPlainString(),
                        "oddsAfter", odds.toPlainString(),
                        "defaultOdds", defaultOdds.toPlainString(),
                        "reason", reason == null ? "" : reason), ip);
    }

    /** Xoá tỷ lệ riêng, đưa người chơi về mức chung. */
    @Transactional
    public void removeOdds(UUID userId, UUID adminId, UUID tableId, String rawBetType, String ip) {
        BetType betType = parseBetType(rawBetType);

        UserGameOdds row = oddsRepository
                .findByUserIdAndTableIdAndBetType(userId, tableId, betType)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND));

        BigDecimal removed = row.getOdds();
        oddsRepository.delete(row);

        audit.record(adminId, null, AuditTrailService.ADMIN_USER_ODDS_REMOVED,
                "USER", userId.toString(),
                Map.of("tableId", tableId.toString(),
                        "betType", betType.name(),
                        "oddsRemoved", removed.toPlainString()), ip);
    }

    // ===== các lớp chặn an toàn =====

    /**
     * Admin KHÔNG được đặt tỷ lệ cho chính mình.
     *
     * Cùng lý do với việc chặn tự điều chỉnh ví, và ở đây còn khó phát hiện hơn: tự nâng
     * tỷ lệ rồi tự đi cược không để lại dấu vết nào trong sổ điều chỉnh ví.
     *
     * Ghi audit TRƯỚC khi ném lỗi. `audit.record` chạy REQUIRES_NEW nên vết vẫn còn dù
     * giao dịch chính rollback vì ngoại lệ.
     */
    private void requireNotSelfDealing(UUID userId, UUID adminId, String ip) {
        if (!userId.equals(adminId)) {
            return;
        }
        audit.record(adminId, null, AuditTrailService.ADMIN_SELF_DEALING_BLOCKED,
                "USER", userId.toString(),
                Map.of("attempt", "USER_ODDS_CHANGE"), ip);
        throw new ApiException(ErrorCode.CANNOT_MODIFY_SELF);
    }

    private static UUID parseUuid(String raw) {
        try {
            return UUID.fromString(raw.trim());
        } catch (IllegalArgumentException e) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR);
        }
    }

    private static BetType parseBetType(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR);
        }
        try {
            return BetType.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR);
        }
    }

    /**
     * Đọc odds từ chuỗi.
     *
     * Qua `new BigDecimal(String)` chứ không `Double.parseDouble`: chuỗi "0.98" cho đúng
     * 0.98, còn qua double sẽ thành 0.9800000000000000266, và con số đó dùng để tính tiền
     * thật.
     */
    private static BigDecimal parseOdds(String raw) {
        try {
            BigDecimal value = new BigDecimal(raw.trim());
            if (value.signum() <= 0) {
                throw new ApiException(ErrorCode.VALIDATION_ERROR);
            }
            // Khớp DECIMAL(10,4) của cột. Không đặt scale thì giá trị 5 chữ số thập phân
            // bị cơ sở dữ liệu làm tròn âm thầm, và con số lưu khác con số admin gõ.
            return value.setScale(4, java.math.RoundingMode.HALF_UP);
        } catch (NumberFormatException e) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR);
        }
    }
}
