package com.rwg.game.api;

import com.rwg.common.web.ClientAddresses;
import com.rwg.config.SecurityConfig;
import com.rwg.game.domain.Bet;
import com.rwg.game.domain.GameRound;
import com.rwg.game.domain.GameTable;
import com.rwg.game.domain.RoundStatus;
import com.rwg.game.dto.GameTableResponse;
import com.rwg.game.dto.UpdateTableLimitsRequest;
import com.rwg.game.dto.UpdateTableStatusRequest;
import com.rwg.game.repository.BetRepository;
import com.rwg.game.repository.GameRoundRepository;
import com.rwg.game.repository.GameTableRepository;
import com.rwg.game.service.AdminGameService;
import com.rwg.identity.domain.User;
import com.rwg.identity.repository.UserRepository;
import com.rwg.settings.domain.AppSetting;
import com.rwg.settings.repository.AppSettingRepository;
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
import org.springframework.web.bind.annotation.PostMapping;
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
@Tag(name = "Admin - Game", description = "Bật/tắt bàn, hạn mức cược, soi cược và can thiệp kết quả")
@SecurityRequirement(name = "bearerAuth")
public class AdminGameController {

    private final AdminGameService service;
    private final GameRoundRepository roundRepository;
    private final BetRepository betRepository;
    private final UserRepository userRepository;
    private final GameTableRepository tableRepository;
    private final AppSettingRepository settingRepository;

    public AdminGameController(AdminGameService service,
                               GameRoundRepository roundRepository,
                               BetRepository betRepository,
                               UserRepository userRepository,
                               GameTableRepository tableRepository,
                               AppSettingRepository settingRepository) {
        this.service = service;
        this.roundRepository = roundRepository;
        this.betRepository = betRepository;
        this.userRepository = userRepository;
        this.tableRepository = tableRepository;
        this.settingRepository = settingRepository;
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
     * Cược đang diễn ra ở vòng hiện tại của bàn — cho admin xem ai đánh bên nào.
     * Trả về danh sách cược kèm username, loại cược tiếng Việt, số tiền, và cấu hình can thiệp hiện tại.
     */
    @GetMapping("/tables/{id}/live-bets")
    @Operation(summary = "Cược đang diễn ra ở vòng hiện tại (ai đánh bên nào, bao nhiêu)")
    public Map<String, Object> liveBets(@PathVariable("id") UUID tableId) {
        Optional<GameRound> roundOpt = roundRepository
                .findFirstByTableIdAndStatusOrderByRoundSeqDesc(tableId, RoundStatus.OPEN);
        if (roundOpt.isEmpty()) {
            roundOpt = roundRepository.findFirstByTableIdOrderByRoundSeqDesc(tableId);
        }

        String forceResult = settingRepository.findById(AppSetting.XOC_DIA_FORCE_RESULT)
                .map(AppSetting::getSettingValue).orElse("AUTO");
        String forceMode = settingRepository.findById(AppSetting.XOC_DIA_FORCE_MODE)
                .map(AppSetting::getSettingValue).orElse("ONCE");

        if (roundOpt.isEmpty()) {
            Map<String, Object> emptyRes = new LinkedHashMap<>();
            emptyRes.put("tableId", tableId.toString());
            emptyRes.put("roundId", "");
            emptyRes.put("roundSeq", 0);
            emptyRes.put("phase", "WAITING");
            emptyRes.put("totalBets", 0);
            emptyRes.put("doorTotals", Map.of());
            emptyRes.put("bets", List.of());
            emptyRes.put("forceResult", forceResult);
            emptyRes.put("forceMode", forceMode);
            return emptyRes;
        }

        GameRound round = roundOpt.get();
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
        result.put("tableId", tableId.toString());
        result.put("roundId", round.getId().toString());
        result.put("roundSeq", round.getRoundSeq());
        result.put("phase", round.getPhase().name());
        result.put("totalBets", bets.size());
        result.put("doorTotals", doorTotals);
        result.put("bets", betList);
        result.put("forceResult", forceResult);
        result.put("forceMode", forceMode);
        return result;
    }

    /**
     * Tự động tìm bàn Xóc Đĩa đang hoạt động và trả về cược trực tiếp.
     */
    @GetMapping("/xocdia/live-bets")
    @Operation(summary = "Xem cược trực tiếp bàn Xóc Đĩa VIP")
    public Map<String, Object> xocDiaLiveBets() {
        Optional<GameTable> xocDiaTable = tableRepository.findAll().stream()
                .filter(t -> "XOC_DIA".equalsIgnoreCase(t.getGameType()))
                .findFirst();

        if (xocDiaTable.isEmpty()) {
            return Map.of("error", "Không tìm thấy bàn Xóc Đĩa");
        }
        return liveBets(xocDiaTable.get().getId());
    }

    /**
     * Can thiệp / Ép kết quả Xóc Đĩa cho vòng tới.
     * forceResult: AUTO, EVEN, ODD, TWO_RED, FOUR_RED, FOUR_WHITE, THREE_RED, THREE_WHITE
     * forceMode: ONCE (1 vòng rồi tự về AUTO), CONTINUOUS (giữ nguyên)
     */
    @PostMapping("/xocdia/force-result")
    @Operation(summary = "Can thiệp / Ép kết quả Xóc Đĩa ván tới")
    public Map<String, Object> setXocDiaForceResult(@RequestBody Map<String, Object> request,
                                                    @AuthenticationPrincipal Jwt jwt,
                                                    HttpServletRequest httpRequest) {
        String adminUsername = jwt != null && jwt.getClaimAsString(SecurityConfig.USERNAME_CLAIM) != null
                ? jwt.getClaimAsString(SecurityConfig.USERNAME_CLAIM)
                : "admin";

        String forceResult = request != null && request.containsKey("forceResult")
                ? String.valueOf(request.get("forceResult")).trim()
                : "AUTO";
        String forceMode = request != null && request.containsKey("forceMode")
                ? String.valueOf(request.get("forceMode")).trim()
                : "ONCE";

        settingRepository.findById(AppSetting.XOC_DIA_FORCE_RESULT).ifPresentOrElse(
                v -> {
                    v.update(forceResult, adminUsername);
                    settingRepository.save(v);
                },
                () -> settingRepository.save(AppSetting.of(AppSetting.XOC_DIA_FORCE_RESULT, forceResult, adminUsername))
        );

        settingRepository.findById(AppSetting.XOC_DIA_FORCE_MODE).ifPresentOrElse(
                v -> {
                    v.update(forceMode, adminUsername);
                    settingRepository.save(v);
                },
                () -> settingRepository.save(AppSetting.of(AppSetting.XOC_DIA_FORCE_MODE, forceMode, adminUsername))
        );

        return Map.of(
                "success", true,
                "forceResult", forceResult,
                "forceMode", forceMode,
                "updatedBy", adminUsername
        );
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

