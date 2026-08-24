package com.rwg.game.service;

import com.rwg.common.ApiException;
import com.rwg.common.ErrorCode;
import com.rwg.common.PageResponse;
import com.rwg.config.GameProperties;
import com.rwg.game.domain.Bet;
import com.rwg.game.domain.GameRound;
import com.rwg.game.domain.GameTable;
import com.rwg.game.domain.GameTableStatus;
import com.rwg.game.domain.RoundPhase;
import com.rwg.game.domain.RoundStatus;
import com.rwg.game.dto.GameTableResponse;
import com.rwg.game.dto.PlayerBetResponse;
import com.rwg.game.dto.RoundResponse;
import com.rwg.game.repository.BetRepository;
import com.rwg.game.repository.GameRoundRepository;
import com.rwg.game.repository.GameTableRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Truy vấn game (Phase c): danh sách bàn, vòng hiện tại, cược của user.
 * name_i18n lưu JSON {"en","vi","zh","ja"} được parse sang Map khi trả về.
 */
@Service
@Transactional(readOnly = true)
public class GameQueryService {

    private static final Logger log = LoggerFactory.getLogger(GameQueryService.class);

    private final GameTableRepository tableRepository;
    private final GameRoundRepository roundRepository;
    private final BetRepository betRepository;
    private final GameProperties gameProperties;
    private final ObjectMapper objectMapper;

    public GameQueryService(GameTableRepository tableRepository,
                            GameRoundRepository roundRepository,
                            BetRepository betRepository,
                            GameProperties gameProperties,
                            ObjectMapper objectMapper) {
        this.tableRepository = tableRepository;
        this.roundRepository = roundRepository;
        this.betRepository = betRepository;
        this.gameProperties = gameProperties;
        this.objectMapper = objectMapper;
    }

    public List<GameTableResponse> listActiveTables() {
        return tableRepository.findByStatus(GameTableStatus.ACTIVE).stream()
                .map(this::toTableResponse)
                .toList();
    }

    /** Vòng OPEN hiện tại của bàn; bàn/vòng không tồn tại -> lỗi i18n 404. */
    public RoundResponse currentRound(UUID tableId) {
        requireActiveTable(tableId);
        GameRound round = roundRepository
                .findFirstByTableIdAndStatusOrderByRoundSeqDesc(tableId, RoundStatus.OPEN)
                .orElseThrow(() -> new ApiException(ErrorCode.ROUND_NOT_FOUND));
        return toRoundResponse(round);
    }

    /** Cược của user trong một round (GET /api/v1/games/me/bets?roundId=). */
    public List<PlayerBetResponse> myBets(UUID userId, UUID roundId) {
        return betRepository.findByUserIdAndRoundId(userId, roundId).stream()
                .map(bet -> new PlayerBetResponse(bet.getId().toString(), bet.getRoundId().toString(),
                        bet.getTableId().toString(),
                        bet.getBetType().name(), bet.getSelection(), bet.getStake().toPlainString(),
                        bet.getStatus().name(), bet.getPayout().toPlainString(), bet.getCreatedAt()))
                .toList();
    }

    /** Lịch sử quay số có phân trang cho bàn chơi cụ thể. */
    public PageResponse<RoundResponse> listRoundsHistory(UUID tableId, int page, int size) {
        requireActiveTable(tableId);
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "roundSeq"));
        Page<GameRound> rounds = roundRepository.findByTableIdAndStatusIn(
                tableId,
                List.of(RoundStatus.SETTLED, RoundStatus.VOIDED),
                pageable
        );
        return PageResponse.from(rounds.map(this::toRoundResponse));
    }

    /** Lịch sử cược có phân trang của người dùng. */
    public PageResponse<PlayerBetResponse> myBetsHistory(UUID userId, UUID tableId, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<Bet> bets;
        if (tableId != null) {
            bets = betRepository.findByUserIdAndTableId(userId, tableId, pageable);
        } else {
            bets = betRepository.findByUserId(userId, pageable);
        }
        return PageResponse.from(bets.map(bet -> new PlayerBetResponse(
                bet.getId().toString(),
                bet.getRoundId().toString(),
                bet.getTableId().toString(),
                bet.getBetType().name(),
                bet.getSelection(),
                bet.getStake().toPlainString(),
                bet.getStatus().name(),
                bet.getPayout().toPlainString(),
                bet.getCreatedAt()
        )));
    }

    /**
     * Dựng RoundResponse từ entity, kèm thời điểm pha hiện tại kết thúc.
     *
     * MỘT chỗ duy nhất để cả API vòng hiện tại và API lịch sử trả cùng hình dạng dữ
     * liệu — trước đây hai chỗ tự liệt kê 16 tham số giống nhau, thêm một trường là
     * phải sửa đúng hai nơi và rất dễ quên một.
     */
    private RoundResponse toRoundResponse(GameRound round) {
        return new RoundResponse(
                round.getId().toString(),
                round.getTableId().toString(),
                round.getRoundSeq(),
                round.getPhase().name(),
                round.getStatus().name(),
                round.getWinningNumber(),
                round.getBaccaratPlayerCards(),
                round.getBaccaratBankerCards(),
                round.getBaccaratPlayerScore(),
                round.getBaccaratBankerScore(),
                round.getBaccaratPlayerPair(),
                round.getBaccaratBankerPair(),
                round.getBaccaratResult(),
                round.getKl28Numbers(),
                round.getKl28Sum(),
                round.getCreatedAt(),
                phaseEndsAt(round),
                roundEndsAt(round),
                roundDuration().toSeconds(),
                Instant.now()
        );
    }

    /**
     * Thời điểm pha hiện tại kết thúc, hoặc null nếu vòng đã đóng.
     *
     * Suy ra từ {@code updated_at} cộng thời lượng pha thay vì thêm một cột mới:
     * {@link com.rwg.game.service.RoundScheduler} ghi {@code updated_at} ĐÚNG lúc
     * chuyển pha (xem {@code GameRoundRepository.updatePhase}), nên nó chính là mốc
     * bắt đầu pha. Cách này không cần migration và không thể lệch với lịch thật.
     *
     * Vòng đã SETTLED/VOIDED trả null: không có pha nào đang chạy để đếm, nếu vẫn
     * cộng thời lượng vào thì client sẽ vẽ một đồng hồ chạy cho vòng đã xong từ lâu.
     */
    private Instant phaseEndsAt(GameRound round) {
        if (round.getStatus() != RoundStatus.OPEN) {
            return null;
        }
        return round.getUpdatedAt().plus(phaseDuration(round.getPhase()));
    }

    /**
     * Thời điểm cả vòng kết thúc, hoặc null nếu vòng đã đóng.
     *
     * Tính từ {@code created_at} cộng độ dài vòng, chứ không cộng dựa trên pha hiện tại:
     * {@code created_at} là mốc bắt đầu vòng và không bao giờ đổi, nên con số trả về ỔN
     * ĐỊNH qua mọi lần gọi. Nếu cộng dựa trên pha hiện tại thì mỗi lần chuyển pha, sai số
     * vài chục mili giây của scheduler sẽ dịch mốc này và đồng hồ trên màn hình nhảy
     * ngược một nhịp.
     *
     * Đây là mốc ƯỚC TÍNH, sai số dưới một giây. Đủ chính xác cho một đồng hồ hiển thị
     * đến giây, nhưng KHÔNG được dùng để quyết định nghiệp vụ.
     */
    private Instant roundEndsAt(GameRound round) {
        if (round.getStatus() != RoundStatus.OPEN) {
            return null;
        }
        return round.getCreatedAt().plus(roundDuration());
    }

    /**
     * Độ dài một vòng, đo bằng khoảng cách giữa hai vòng liên tiếp.
     *
     * KHÔNG cộng {@code settle}: con số đó là HẠN CHỜ tối đa của
     * {@link RoundScheduler#runRound}, không phải thời gian ngủ. Scheduler gọi
     * {@code awaitSettlement} và đi tiếp NGAY khi thanh toán xong, thường dưới một giây,
     * chứ không chờ hết 5 giây. Cộng cả {@code settle} vào sẽ cho một mốc muộn hơn thực
     * tế 5 giây, và đồng hồ "ván tiếp theo" còn hiện 5 giây trong khi ván mới đã chạy.
     *
     * Bốn pha còn lại ĐỀU là {@code sleep} với đúng thời lượng cấu hình, nên tổng của
     * chúng khớp khoảng cách thật giữa hai vòng.
     */
    private Duration roundDuration() {
        GameProperties.Round d = gameProperties.round();
        return d.bettingOpen()
                .plus(d.bettingClosed())
                .plus(d.spinning())
                .plus(d.result());
    }

    /** Thời lượng cấu hình của một pha. */
    private Duration phaseDuration(RoundPhase phase) {
        GameProperties.Round d = gameProperties.round();
        return switch (phase) {
            case BETTING_OPEN -> d.bettingOpen();
            case BETTING_CLOSED -> d.bettingClosed();
            case SPINNING -> d.spinning();
            case RESULT -> d.result();
            case SETTLE -> d.settle();
        };
    }

    GameTable requireActiveTable(UUID tableId) {
        return tableRepository.findById(tableId)
                .filter(t -> t.getStatus() == GameTableStatus.ACTIVE)
                .orElseThrow(() -> new ApiException(ErrorCode.GAME_TABLE_NOT_FOUND));
    }

    private GameTableResponse toTableResponse(GameTable table) {
        return new GameTableResponse(table.getId().toString(), table.getGameType(),
                parseNameI18n(table.getNameI18n()), table.getStatus().name(),
                table.getMinBet().toPlainString(), table.getMaxBet().toPlainString(),
                table.getCurrency());
    }

    /**
     * Parse name_i18n (JSON object) sang Map locale->text.
     * H2 (MODE=MySQL) trả cột JSON về dạng JSON string literal (bọc dấu nháy kép,
     * escape nháy trong), MySQL trả text JSON thô -> xử lý cả hai dạng.
     */
    private Map<String, String> parseNameI18n(String json) {
        try {
            String candidate = json;
            if (candidate != null && candidate.startsWith("\"")) {
                candidate = objectMapper.readValue(candidate, String.class);
            }
            return objectMapper.readValue(candidate, new TypeReference<Map<String, String>>() { });
        } catch (RuntimeException malformedJson) {
            log.warn("game_tables.name_i18n invalid JSON: {}", json, malformedJson);
            return Map.of();
        }
    }
}
