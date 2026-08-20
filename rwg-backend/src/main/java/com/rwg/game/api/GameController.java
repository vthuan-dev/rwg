package com.rwg.game.api;

import com.rwg.common.PageResponse;
import com.rwg.game.dto.BetRequest;
import com.rwg.game.dto.BetResponse;
import com.rwg.game.dto.GameTableResponse;
import com.rwg.game.dto.PlayerBetResponse;
import com.rwg.game.dto.RoundResponse;
import com.rwg.game.service.BetService;
import com.rwg.game.service.GameQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * API game Roulette (Phase c). Mọi endpoint yêu cầu JWT (SecurityConfig).
 * Realtime qua WebSocket STOMP /topic/game/table/{tableId} (WebSocketConfig).
 */
@RestController
@RequestMapping("/api/v1/games")
@Tag(name = "Game", description = "Bàn chơi, vòng chơi và đặt cược Roulette")
@SecurityRequirement(name = "bearerAuth")
public class GameController {

    private final GameQueryService gameQueryService;
    private final BetService betService;

    public GameController(GameQueryService gameQueryService, BetService betService) {
        this.gameQueryService = gameQueryService;
        this.betService = betService;
    }

    @GetMapping("/tables")
    @Operation(summary = "Danh sách bàn chơi ACTIVE (name_i18n đủ en/vi/zh/ja)")
    public List<GameTableResponse> tables() {
        return gameQueryService.listActiveTables();
    }

    @GetMapping("/tables/{id}/rounds/current")
    @Operation(summary = "Vòng OPEN hiện tại của bàn (kèm serverTime để client countdown)")
    public RoundResponse currentRound(@PathVariable("id") UUID tableId) {
        return gameQueryService.currentRound(tableId);
    }

    @PostMapping("/tables/{id}/bets")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Đặt cược (trừ ví ngay - M1; idempotent theo seq trong vòng)")
    public BetResponse placeBet(@PathVariable("id") UUID tableId,
                                @AuthenticationPrincipal Jwt jwt,
                                @Valid @RequestBody BetRequest request) {
        return betService.placeBet(tableId, UUID.fromString(jwt.getSubject()), request);
    }

    @GetMapping("/me/bets")
    @Operation(summary = "Cược của tôi trong một round")
    public List<PlayerBetResponse> myBets(@AuthenticationPrincipal Jwt jwt,
                                          @RequestParam UUID roundId) {
        return gameQueryService.myBets(UUID.fromString(jwt.getSubject()), roundId);
    }

    @GetMapping("/tables/{id}/rounds")
    @Operation(summary = "Lịch sử quay số có phân trang cho bàn cụ thể")
    public PageResponse<RoundResponse> roundsHistory(@PathVariable("id") UUID tableId,
                                            @RequestParam(defaultValue = "0") int page,
                                            @RequestParam(defaultValue = "10") int size) {
        return gameQueryService.listRoundsHistory(tableId, page, size);
    }

    @GetMapping("/me/bets/history")
    @Operation(summary = "Lịch sử cược của tôi có phân trang")
    public PageResponse<PlayerBetResponse> myBetsHistory(@AuthenticationPrincipal Jwt jwt,
                                                @RequestParam(required = false) UUID tableId,
                                                @RequestParam(defaultValue = "0") int page,
                                                @RequestParam(defaultValue = "10") int size) {
        return gameQueryService.myBetsHistory(UUID.fromString(jwt.getSubject()), tableId, page, size);
    }
}
