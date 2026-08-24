package com.rwg.game.service;

import com.rwg.common.ApiException;
import com.rwg.common.ErrorCode;
import com.rwg.common.money.Money;
import com.rwg.game.domain.Bet;
import com.rwg.game.domain.BetType;
import com.rwg.game.domain.GameRound;
import com.rwg.game.domain.GameTable;
import com.rwg.game.domain.GameTableStatus;
import com.rwg.game.domain.RoundPhase;
import com.rwg.game.domain.RoundStatus;
import com.rwg.game.dto.BetRequest;
import com.rwg.game.dto.BetResponse;
import com.rwg.game.repository.BetRepository;
import com.rwg.game.repository.GameRoundRepository;
import com.rwg.game.repository.GameTableRepository;
import com.rwg.wallet.domain.WalletRefType;
import com.rwg.wallet.service.WalletService;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/**
 * Nhận cược Roulette (Phase c, docs/round-lifecycle.md mục 3):
 * - Chỉ nhận khi vòng hiện tại phase BETTING_OPEN (lỗi i18n error.ROUND_BETTING_CLOSED).
 * - Validate loại cược / selection qua {@link RouletteEngine#validSelection} + hạn mức bàn.
 * - Trừ tiền NGAY qua {@link WalletService#debit} (M1, ref_type=BET) CÙNG key idempotency
 *   "BET:{roundId}:{userId}:{seq}" — guard DB của wallet chặn double-debit tuyệt đối.
 * - Idempotent: gọi lại cùng seq trả bet đã ghi, KHÔNG trừ tiền lần hai.
 */
@Service
public class BetService {

    /** Số lock dải để serialize check-then-insert theo idempotency key (MVP 1 instance). */
    private static final int LOCK_STRIPES = 64;

    private final GameTableRepository tableRepository;
    private final GameRoundRepository roundRepository;
    private final BetRepository betRepository;
    private final WalletService walletService;
    private final GameEventBroadcaster broadcaster;
    private final OddsResolver oddsResolver;
    private final Object[] lockStripes = new Object[LOCK_STRIPES];

    public BetService(GameTableRepository tableRepository,
                      GameRoundRepository roundRepository,
                      BetRepository betRepository,
                      WalletService walletService,
                      GameEventBroadcaster broadcaster,
                      OddsResolver oddsResolver) {
        this.tableRepository = tableRepository;
        this.roundRepository = roundRepository;
        this.betRepository = betRepository;
        this.walletService = walletService;
        this.broadcaster = broadcaster;
        this.oddsResolver = oddsResolver;
        for (int i = 0; i < LOCK_STRIPES; i++) {
            lockStripes[i] = new Object();
        }
    }

    /** Đặt cược; idempotent theo seq trong vòng. Trả kết quả kèm số dư sau khi trừ. */
    public BetResponse placeBet(UUID tableId, UUID userId, BetRequest request) {
        GameTable table = tableRepository.findById(tableId)
                .filter(t -> t.getStatus() == GameTableStatus.ACTIVE)
                .orElseThrow(() -> new ApiException(ErrorCode.GAME_TABLE_NOT_FOUND));

        BetType betType = parseBetType(request.betType());
        Money stake = parseStake(request.stake());
        if (stake.amount().compareTo(table.getMinBet()) < 0) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, null, null, "validation.bet.stake.min");
        }
        if (stake.amount().compareTo(table.getMaxBet()) > 0) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, null, null, "validation.bet.stake.max");
        }
        String selection;
        if ("ROULETTE".equals(table.getGameType())) {
            selection = RouletteEngine.normalize(request.selection());
            if (!RouletteEngine.validSelection(betType, selection)) {
                throw new ApiException(ErrorCode.INVALID_BET_SELECTION);
            }
        } else if ("BACCARAT".equals(table.getGameType())) {
            selection = BaccaratEngine.normalize(request.selection());
            if (!BaccaratEngine.validSelection(betType, selection)) {
                throw new ApiException(ErrorCode.INVALID_BET_SELECTION);
            }
        } else if ("KL28".equals(table.getGameType())
                || "LUCKY28".equals(table.getGameType())
                || "BRITISH_LUCKY28".equals(table.getGameType())
                || "TAIWAN_TIMES".equals(table.getGameType())) {
            selection = Kl28Engine.normalize(request.selection());
            if (!Kl28Engine.validSelection(betType, selection)) {
                throw new ApiException(ErrorCode.INVALID_BET_SELECTION);
            }
        } else {
            throw new ApiException(ErrorCode.INVALID_REQUEST);
        }

        GameRound round = roundRepository
                .findFirstByTableIdAndStatusOrderByRoundSeqDesc(tableId, RoundStatus.OPEN)
                .orElseThrow(() -> new ApiException(ErrorCode.ROUND_NOT_FOUND));
        if (round.getPhase() != RoundPhase.BETTING_OPEN) {
            throw new ApiException(ErrorCode.ROUND_BETTING_CLOSED);
        }

        String idempotencyKey = "BET:" + round.getId() + ":" + userId + ":" + request.seq();

        // Fast-path (KHÔNG lock): lệnh này đã ghi sổ rồi.
        Optional<Bet> existing = betRepository.findFirstByIdempotencyKey(idempotencyKey);
        if (existing.isPresent()) {
            return toResponse(existing.get(), walletService.getBalance(userId));
        }

        // Serialize check-then-insert theo key (MVP 1 instance; guard DB của wallet
        // vẫn là chốt chặn cuối cho tiền kể cả khi 2 thread lọt qua lock khác stripe).
        synchronized (stripeFor(idempotencyKey)) {
            Optional<Bet> raced = betRepository.findFirstByIdempotencyKey(idempotencyKey);
            if (raced.isPresent()) {
                return toResponse(raced.get(), walletService.getBalance(userId));
            }

            // M1: trừ ví NGAY, cùng idempotency key với bet -> không bao giờ double-debit.
            // ref_id giới hạn VARCHAR(64): chỉ gắn roundId (idempotency_key giữ đủ key BET:*).
            Money balanceAfter = walletService.debit(userId, stake, WalletRefType.BET,
                    round.getId().toString(), idempotencyKey);

            // Chốt odds hiệu lực vào bản ghi cược. Thanh toán đọc tại đây chứ không tra lại
            // bảng tỷ lệ: người chơi đồng ý với con số họ thấy lúc đặt, nên đổi tỷ lệ sau đó
            // không được phép ảnh hưởng tới cược đã nhận.
            BigDecimal odds = oddsResolver.effectiveOdds(userId, table, betType, selection);

            Bet bet = betRepository.save(new Bet(round.getId(), tableId, userId,
                    betType, selection, stake.amount(), idempotencyKey, odds));
            broadcaster.recordBet(tableId, round.getId(), stake.amount());
            return toResponse(bet, balanceAfter);
        }
    }

    // ===== helpers =====

    private BetType parseBetType(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, null, null,
                    "validation.bet.bet_type.not_blank");
        }
        try {
            return BetType.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException unknownType) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, null, null,
                    "validation.bet.bet_type.invalid");
        }
    }

    /** Stake dạng STRING -> Money (CẤM float/double). Số âm/rác -> validation i18n. */
    private Money parseStake(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, null, null,
                    "validation.bet.stake.not_blank");
        }
        try {
            Money stake = Money.of(new BigDecimal(raw.trim()));
            if (!stake.isPositive()) {
                throw new ApiException(ErrorCode.VALIDATION_ERROR, null, null,
                        "validation.bet.stake.invalid");
            }
            return stake;
        } catch (NumberFormatException notANumber) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, null, null,
                    "validation.bet.stake.invalid");
        }
    }

    private Object stripeFor(String idempotencyKey) {
        return lockStripes[(idempotencyKey.hashCode() & Integer.MAX_VALUE) % LOCK_STRIPES];
    }

    private BetResponse toResponse(Bet bet, Money balance) {
        return new BetResponse(bet.getId().toString(), bet.getRoundId().toString(),
                bet.getBetType().name(), bet.getSelection(), bet.getStake().toPlainString(),
                bet.getStatus().name(), balance.amount().toPlainString());
    }
}
