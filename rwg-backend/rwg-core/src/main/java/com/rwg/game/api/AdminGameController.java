package com.rwg.game.api;

import com.rwg.common.web.ClientAddresses;
import com.rwg.game.domain.Bet;
import com.rwg.game.domain.GameRound;
import com.rwg.game.domain.RoundStatus;
import com.rwg.game.dto.GameTableResponse;
import com.rwg.game.dto.UpdateTableLimitsRequest;
import com.rwg.game.dto.UpdateTableStatusRequest;
import com.rwg.game.repository.BetRepository;
import com.rwg.game.repository.GameRoundRepository;
import com.rwg.game.service.AdminGameService;
import com.rwg.identity.domain.User;
import com.rwg.identity.repository.UserRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * API quản trị bàn chơi. Phân quyền enforce tập trung ở SecurityConfig.
 * Controller này bị loại khỏi rwg-user-app (xem RwgApplication.excludeFilters).
 */
@RestController
@RequestMapping("/api/v1/admin/games")
@Tag(name = "Admin - Game", description = "Bật/tắt bàn và hạn mức cược")
@SecurityRequirement(name = "bearerAuth")
public class AdminGameController {

    private final AdminGameService service;
    private final GameRoundRepository roundRepository;
    private final BetRepository betRepository;
    private final UserRepository userRepository;

    public AdminGameController(AdminGameService service,
                               GameRoundRepository roundRepository,
                               BetRepository betRepository,
                               UserRepository userRepository) {
        this.service = service;
        this.roundRepository = roundRepository;
        this.betRepository = betRepository;
        this.userRepository = userRepository;
    }

    @GetMapping("/tables")
    @Operation(summary = "Toàn bộ bàn chơi, GỒM CẢ bàn đã tắt")
    public List<GameTableResponse> tables() {
        return service.listAllTables();
    }

    @PatchMapping("/tables/{id}/status")
    @Operation(summary = "Bật/tắt bàn. Bàn tắt sẽ dừng ở cuối vòng đang chạy, không hủy vòng giữa dòng")
    public GameTableResponse changeStatus(@PathVariable("id") UUID tableId,
                                         @Valid @RequestBody UpdateTableStatusRequest request,
                                         @AuthenticationPrincipal Jwt jwt,
                                         HttpServletRequest httpRequest) {
        return service.changeStatus(tableId, request, UUID.fromString(jwt.getSubject()),
                ClientAddresses.clientIp(httpRequest));
    }

    @PatchMapping("/tables/{id}/limits")
    @Operation(summary = "Đổi hạn mức cược. Chỉ áp cho cược mới; cược đã đặt không bị ảnh hưởng")
    public GameTableResponse updateLimits(@PathVariable("id") UUID tableId,
                                          @Valid @RequestBody UpdateTableLimitsRequest request,
                                          @AuthenticationPrincipal Jwt jwt,
                                          HttpServletRequest httpRequest) {
        return service.updateLimits(tableId, request, UUID.fromString(jwt.getSubject()),
                ClientAddresses.clientIp(httpRequest));
    }

    /**
     * Cược đang diễn ra ở vòng OPEN hiện tại của bàn — cho admin xem ai đánh bên nào.
     * Trả về danh sách cược kèm username, loại cược tiếng Việt, số tiền.
     */
    @GetMapping("/tables/{id}/live-bets")
    @Operation(summary = "Cược đang diễn ra ở vòng hiện tại (ai đánh bên nào, bao nhiêu)")
    public Map<String, Object> liveBets(@PathVariable("id") UUID tableId) {
        Optional<GameRound> openRound = roundRepository
                .findFirstByTableIdAndStatusOrderByRoundSeqDesc(tableId, RoundStatus.OPEN);

        if (openRound.isEmpty()) {
            return Map.of("roundId", "", "roundSeq", 0, "bets", List.of(), "summary", Map.of());
        }

        GameRound round = openRound.get();
        List<Bet> bets = betRepository.findByRoundId(round.getId());

        // Tổng hợp theo cửa
        Map<String, BigDecimal> doorTotals = new LinkedHashMap<>();
        List<Map<String, Object>> betList = new ArrayList<>();

        for (Bet b : bets) {
            String doorLabel = vietnameseDoor(b.getBetType().name());
            doorTotals.merge(doorLabel, b.getStake(), BigDecimal::add);

            String username = userRepository.findById(b.getUserId())
                    .map(User::getUsername).orElse("?");

            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("username", username);
            entry.put("betType", b.getBetType().name());
            entry.put("doorLabel", doorLabel);
            entry.put("stake", b.getStake());
            entry.put("createdAt", b.getCreatedAt() != null ? b.getCreatedAt().toString() : "");
            betList.add(entry);
        }

        // Sắp xếp mới nhất trước
        betList.sort((a, b2) -> {
            String ta = (String) a.get("createdAt");
            String tb = (String) b2.get("createdAt");
            return tb.compareTo(ta);
        });

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("roundId", round.getId().toString());
        result.put("roundSeq", round.getRoundSeq());
        result.put("phase", round.getPhase().name());
        result.put("totalBets", bets.size());
        result.put("doorTotals", doorTotals);
        result.put("bets", betList);
        return result;
    }

    private String vietnameseDoor(String betType) {
        return switch (betType) {
            case "XOC_DIA_EVEN" -> "Chẵn";
            case "XOC_DIA_ODD" -> "Lẻ";
            case "XOC_DIA_FOUR_RED" -> "Tứ Quý Đỏ (4 đỏ)";
            case "XOC_DIA_FOUR_WHITE" -> "Tứ Quý Trắng (4 trắng)";
            case "XOC_DIA_THREE_RED" -> "3 Đỏ 1 Trắng";
            case "XOC_DIA_THREE_WHITE" -> "3 Trắng 1 Đỏ";
            default -> betType;
        };
    }
}
