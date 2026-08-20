package com.rwg.game.service;

import com.rwg.common.ApiException;
import com.rwg.common.ErrorCode;
import com.rwg.common.PageResponse;
import com.rwg.game.domain.Bet;
import com.rwg.game.domain.GameRound;
import com.rwg.game.domain.GameTable;
import com.rwg.game.domain.GameTableStatus;
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
    private final ObjectMapper objectMapper;

    public GameQueryService(GameTableRepository tableRepository,
                            GameRoundRepository roundRepository,
                            BetRepository betRepository,
                            ObjectMapper objectMapper) {
        this.tableRepository = tableRepository;
        this.roundRepository = roundRepository;
        this.betRepository = betRepository;
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
        return new RoundResponse(round.getId().toString(), tableId.toString(),
                round.getRoundSeq(), round.getPhase().name(), round.getStatus().name(),
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
                Instant.now());
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
        return PageResponse.from(rounds.map(round -> new RoundResponse(
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
                Instant.now()
        )));
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
