package com.rwg.settings.api;

import com.rwg.common.web.ClientAddresses;
import com.rwg.config.SecurityConfig;
import com.rwg.game.domain.Bet;
import com.rwg.game.domain.GameTable;
import com.rwg.game.domain.GameTableStatus;
import com.rwg.game.domain.RoundStatus;
import com.rwg.game.repository.BetRepository;
import com.rwg.game.repository.GameRoundRepository;
import com.rwg.game.repository.GameTableRepository;
import com.rwg.identity.domain.User;
import com.rwg.identity.domain.UserStatus;
import com.rwg.identity.repository.UserRepository;
import com.rwg.presence.service.PresenceQueryService;
import com.rwg.settings.domain.AppSetting;
import com.rwg.settings.dto.AppSettingResponse;
import com.rwg.settings.dto.UpdateAppSettingRequest;
import com.rwg.settings.service.AppSettingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Sửa nội dung chữ hiện ra cho khách, từ khu quản trị.
 *
 * Phân quyền enforce tập trung trong {@code SecurityConfig}: PUT chỉ ADMIN. Nội dung này
 * hiện trước MỌI khách truy cập — cùng mức ảnh hưởng với banner trang chủ, nên cùng mức
 * hạn chế. KHÔNG rải {@code @PreAuthorize} ở đây.
 */
@RestController
@RequestMapping("/api/v1/admin/settings")
@Tag(name = "Admin", description = "Sửa nội dung chữ hiện cho người chơi")
public class AdminAppSettingController {

    private final AppSettingService service;
    private final BetRepository betRepository;
    private final GameRoundRepository roundRepository;
    private final GameTableRepository tableRepository;
    private final UserRepository userRepository;
    private final PresenceQueryService presenceQueryService;

    public AdminAppSettingController(AppSettingService service,
                                     BetRepository betRepository,
                                     GameRoundRepository roundRepository,
                                     GameTableRepository tableRepository,
                                     UserRepository userRepository,
                                     PresenceQueryService presenceQueryService) {
        this.service = service;
        this.betRepository = betRepository;
        this.roundRepository = roundRepository;
        this.tableRepository = tableRepository;
        this.userRepository = userRepository;
        this.presenceQueryService = presenceQueryService;
    }

    @GetMapping("/chat-promo-text")
    @Operation(summary = "Lời chào khuyến mãi khung chat: nội dung hiện tại + ai sửa lần cuối")
    public AppSettingResponse chatPromoText() {
        return service.get(AppSetting.CHAT_PROMO_TEXT);
    }

    /**
     * Lưu lời chào mới.
     *
     * PUT chứ không PATCH: toàn bộ giá trị bị thay, không có phần nào được giữ lại. PATCH
     * sẽ hàm ý một phép hợp nhất không tồn tại ở đây.
     */
    @PutMapping("/chat-promo-text")
    @Operation(summary = "Lưu lời chào khuyến mãi mới cho khung chat")
    public AppSettingResponse updateChatPromoText(@Valid @RequestBody UpdateAppSettingRequest request,
                                                 @AuthenticationPrincipal Jwt jwt,
                                                 HttpServletRequest httpRequest) {
        return service.update(AppSetting.CHAT_PROMO_TEXT, request.getValue(),
                UUID.fromString(jwt.getSubject()),
                jwt.getClaimAsString(SecurityConfig.USERNAME_CLAIM),
                ClientAddresses.clientIp(httpRequest));
    }

    @GetMapping("/xocdia-config")
    @Operation(summary = "Lấy cấu hình phòng Xóc Đĩa VIP (số bot và bot chat)")
    public Map<String, Object> getXocDiaConfig() {
        int botCount = 4;
        boolean botChatEnabled = true;

        try {
            AppSettingResponse countSetting = service.get(AppSetting.XOC_DIA_BOT_COUNT);
            if (countSetting != null && countSetting.value() != null) {
                int parsed = Integer.parseInt(countSetting.value().trim());
                if (parsed >= 1 && parsed <= 8) {
                    botCount = parsed;
                }
            }
        } catch (Exception ignored) {
        }

        try {
            AppSettingResponse chatSetting = service.get(AppSetting.XOC_DIA_BOT_CHAT_ENABLED);
            if (chatSetting != null && chatSetting.value() != null) {
                botChatEnabled = Boolean.parseBoolean(chatSetting.value().trim());
            }
        } catch (Exception ignored) {
        }

        return Map.of(
                "botCount", botCount,
                "botChatEnabled", botChatEnabled
        );
    }

    @PutMapping("/xocdia-config")
    @Operation(summary = "Lưu cấu hình phòng Xóc Đĩa VIP (số bot và bot chat)")
    public Map<String, Object> updateXocDiaConfig(@RequestBody Map<String, Object> request,
                                                  @AuthenticationPrincipal Jwt jwt,
                                                  HttpServletRequest httpRequest) {
        UUID adminId;
        try {
            adminId = (jwt != null && jwt.getSubject() != null) ? UUID.fromString(jwt.getSubject()) : UUID.randomUUID();
        } catch (Exception e) {
            adminId = UUID.randomUUID();
        }
        String adminUsername = jwt != null && jwt.getClaimAsString(SecurityConfig.USERNAME_CLAIM) != null 
                ? jwt.getClaimAsString(SecurityConfig.USERNAME_CLAIM) 
                : "admin";
        String ip = ClientAddresses.clientIp(httpRequest);

        int botCount = 4;
        if (request != null && request.containsKey("botCount")) {
            try {
                int val = Integer.parseInt(String.valueOf(request.get("botCount")).trim());
                if (val >= 1 && val <= 8) {
                    botCount = val;
                }
            } catch (Exception ignored) {
            }
        }

        boolean botChatEnabled = true;
        if (request != null && request.containsKey("botChatEnabled")) {
            try {
                botChatEnabled = Boolean.parseBoolean(String.valueOf(request.get("botChatEnabled")).trim());
            } catch (Exception ignored) {
            }
        }

        service.update(AppSetting.XOC_DIA_BOT_COUNT, String.valueOf(botCount), adminId, adminUsername, ip);
        service.update(AppSetting.XOC_DIA_BOT_CHAT_ENABLED, String.valueOf(botChatEnabled), adminId, adminUsername, ip);

        return Map.of(
                "botCount", botCount,
                "botChatEnabled", botChatEnabled
        );
    }

    @GetMapping("/xocdia-jackpot")
    @Operation(summary = "Lấy cấu hình chi tiết tính năng Jackpot Xóc Đĩa")
    public Map<String, Object> getXocDiaJackpot() {
        return readJackpotConfigMap();
    }

    @PutMapping("/xocdia-jackpot")
    @Operation(summary = "Cập nhật cấu hình Jackpot Xóc Đĩa")
    public Map<String, Object> updateXocDiaJackpot(@RequestBody Map<String, Object> request,
                                                  @AuthenticationPrincipal Jwt jwt,
                                                  HttpServletRequest httpRequest) {
        UUID adminId;
        try {
            adminId = (jwt != null && jwt.getSubject() != null) ? UUID.fromString(jwt.getSubject()) : UUID.randomUUID();
        } catch (Exception e) {
            adminId = UUID.randomUUID();
        }
        String adminUsername = jwt != null && jwt.getClaimAsString(SecurityConfig.USERNAME_CLAIM) != null 
                ? jwt.getClaimAsString(SecurityConfig.USERNAME_CLAIM) 
                : "admin";
        String ip = ClientAddresses.clientIp(httpRequest);

        if (request != null) {
            if (request.containsKey("pool")) {
                service.update(AppSetting.XOC_DIA_JACKPOT_POOL, String.valueOf(request.get("pool")), adminId, adminUsername, ip);
            }
            if (request.containsKey("minPool")) {
                service.update(AppSetting.XOC_DIA_JACKPOT_MIN_POOL, String.valueOf(request.get("minPool")), adminId, adminUsername, ip);
            }
            if (request.containsKey("triggerMode")) {
                service.update(AppSetting.XOC_DIA_JACKPOT_TRIGGER_MODE, String.valueOf(request.get("triggerMode")), adminId, adminUsername, ip);
            }
            if (request.containsKey("autoRate")) {
                service.update(AppSetting.XOC_DIA_JACKPOT_AUTO_RATE, String.valueOf(request.get("autoRate")), adminId, adminUsername, ip);
            }
            if (request.containsKey("targetDoor")) {
                service.update(AppSetting.XOC_DIA_JACKPOT_TARGET_DOOR, String.valueOf(request.get("targetDoor")), adminId, adminUsername, ip);
            }
            if (request.containsKey("winnerMode")) {
                service.update(AppSetting.XOC_DIA_JACKPOT_WINNER_MODE, String.valueOf(request.get("winnerMode")), adminId, adminUsername, ip);
            }
            if (request.containsKey("targetUser")) {
                service.update(AppSetting.XOC_DIA_JACKPOT_TARGET_USER, String.valueOf(request.get("targetUser")), adminId, adminUsername, ip);
            }
            if (request.containsKey("lastWon")) {
                service.update(AppSetting.XOC_DIA_JACKPOT_LAST_WON, String.valueOf(request.get("lastWon")), adminId, adminUsername, ip);
            }
            if (request.containsKey("threshold")) {
                service.update(AppSetting.XOC_DIA_JACKPOT_THRESHOLD, String.valueOf(request.get("threshold")), adminId, adminUsername, ip);
            }
            if (request.containsKey("winMode")) {
                service.update(AppSetting.XOC_DIA_JACKPOT_WIN_MODE, String.valueOf(request.get("winMode")), adminId, adminUsername, ip);
            }
            if (request.containsKey("winValue")) {
                service.update(AppSetting.XOC_DIA_JACKPOT_WIN_VALUE, String.valueOf(request.get("winValue")), adminId, adminUsername, ip);
            }
            if (request.containsKey("requireBet")) {
                service.update(AppSetting.XOC_DIA_JACKPOT_REQUIRE_BET, String.valueOf(request.get("requireBet")), adminId, adminUsername, ip);
            }
            if (request.containsKey("feeRate")) {
                service.update(AppSetting.XOC_DIA_JACKPOT_FEE_RATE, String.valueOf(request.get("feeRate")), adminId, adminUsername, ip);
            }
        }

        return readJackpotConfigMap();
    }

    @PostMapping("/xocdia-jackpot/force-trigger")
    @Operation(summary = "Ép nổ hũ ván tiếp theo")
    public Map<String, Object> forceJackpotTrigger(@RequestBody(required = false) Map<String, Object> request,
                                                   @AuthenticationPrincipal Jwt jwt,
                                                   HttpServletRequest httpRequest) {
        UUID adminId;
        try {
            adminId = (jwt != null && jwt.getSubject() != null) ? UUID.fromString(jwt.getSubject()) : UUID.randomUUID();
        } catch (Exception e) {
            adminId = UUID.randomUUID();
        }
        String adminUsername = jwt != null && jwt.getClaimAsString(SecurityConfig.USERNAME_CLAIM) != null 
                ? jwt.getClaimAsString(SecurityConfig.USERNAME_CLAIM) 
                : "admin";
        String ip = ClientAddresses.clientIp(httpRequest);

        service.update(AppSetting.XOC_DIA_JACKPOT_TRIGGER_MODE, "FORCE_NEXT_ROUND", adminId, adminUsername, ip);

        if (request != null) {
            if (request.containsKey("targetDoor")) {
                service.update(AppSetting.XOC_DIA_JACKPOT_TARGET_DOOR, String.valueOf(request.get("targetDoor")), adminId, adminUsername, ip);
            }
            if (request.containsKey("winnerMode")) {
                service.update(AppSetting.XOC_DIA_JACKPOT_WINNER_MODE, String.valueOf(request.get("winnerMode")), adminId, adminUsername, ip);
            }
            if (request.containsKey("targetUser")) {
                service.update(AppSetting.XOC_DIA_JACKPOT_TARGET_USER, String.valueOf(request.get("targetUser")), adminId, adminUsername, ip);
            }
            if (request.containsKey("winMode")) {
                service.update(AppSetting.XOC_DIA_JACKPOT_WIN_MODE, String.valueOf(request.get("winMode")), adminId, adminUsername, ip);
            }
            if (request.containsKey("winValue")) {
                service.update(AppSetting.XOC_DIA_JACKPOT_WIN_VALUE, String.valueOf(request.get("winValue")), adminId, adminUsername, ip);
            }
        }

        return readJackpotConfigMap();
    }

    /**
     * Danh sách người chơi đang có mặt trong phòng Xóc Đĩa, phục vụ dropdown chọn người nhận hũ.
     *
     * Phân loại:
     * - BETTING_NOW  : đang cược ở vòng OPEN hiện tại.
     * - RECENT_BETTOR: đã cược trong 3 vòng SETTLED gần nhất.
     * - SPECTATING   : online nhưng không cược gần đây.
     *
     * Loại trừ tài khoản BLOCKED / CLOSED.
     */
    @GetMapping("/xocdia-jackpot/room-players")
    @Operation(summary = "Danh sách người chơi trong phòng Xóc Đĩa (cho dropdown chọn người nhận hũ)")
    public List<Map<String, Object>> xocdiaRoomPlayers() {
        // 1. Tìm bàn XocDia đầu tiên đang ACTIVE
        Optional<GameTable> tableOpt = tableRepository.findAll().stream()
                .filter(t -> t.getStatus() == GameTableStatus.ACTIVE
                        && ("XOC_DIA".equalsIgnoreCase(t.getGameType())
                            || "XOCDIA".equalsIgnoreCase(t.getGameType())))
                .findFirst();
        if (tableOpt.isEmpty()) {
            return List.of();
        }
        UUID tableId = tableOpt.get().getId();

        // 2. Lấy userId đang cược ở vòng OPEN hiện tại
        Set<UUID> bettingNow = roundRepository
                .findFirstByTableIdAndStatusOrderByRoundSeqDesc(tableId, RoundStatus.OPEN)
                .map(round -> betRepository.findByRoundId(round.getId())
                        .stream().map(Bet::getUserId).collect(Collectors.toSet()))
                .orElse(Set.of());

        // 3. Lấy userId đã cược trong 50 vòng SETTLED gần nhất
        List<UUID> recentRoundIds = roundRepository
                .findByTableIdAndStatusIn(tableId,
                        List.of(RoundStatus.SETTLED),
                        PageRequest.of(0, 50, Sort.by(Sort.Direction.DESC, "roundSeq")))
                .stream().map(r -> r.getId()).toList();

        Set<UUID> recentBettors = recentRoundIds.stream()
                .flatMap(rid -> betRepository.findByRoundId(rid).stream().map(Bet::getUserId))
                .collect(Collectors.toSet());

        // 3b. Fallback theo thời gian: ai cược trong 10 phút gần đây trên bàn này
        Instant tenMinutesAgo = Instant.now().minusSeconds(600);
        List<Bet> recentTimeBets = betRepository.findByTableIdAndCreatedAtAfter(tableId, tenMinutesAgo);
        for (Bet b : recentTimeBets) {
            recentBettors.add(b.getUserId());
        }

        recentBettors.removeAll(bettingNow); // không trùng lên BETTING_NOW

        // 4. Kiểm tra presence cho tất cả candidates
        Set<UUID> allCandidates = new java.util.LinkedHashSet<>();
        allCandidates.addAll(bettingNow);
        allCandidates.addAll(recentBettors);

        Map<UUID, Instant> seenMap = presenceQueryService.lastSeen(allCandidates);

        // Thêm người SPECTATING: online nhưng chưa cược (chỉ khi có presence)
        // — bỏ qua vì không có danh sách subscribe room; chỉ trả BETTING_NOW + RECENT_BETTOR

        // 5. Dựng kết quả
        List<Map<String, Object>> result = new ArrayList<>();
        for (UUID uid : allCandidates) {
            Optional<User> userOpt = userRepository.findById(uid);
            if (userOpt.isEmpty()) continue;
            User u = userOpt.get();
            if (u.getStatus() == UserStatus.BANNED || u.getStatus() == UserStatus.CLOSED) continue;

            Instant lastSeen = seenMap.get(uid);
            boolean online = presenceQueryService.isOnline(lastSeen);
            String activityStatus = bettingNow.contains(uid) ? "BETTING_NOW" : "RECENT_BETTOR";

            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("userId", uid.toString());
            entry.put("username", u.getUsername());
            entry.put("online", online);
            entry.put("activityStatus", activityStatus);
            result.add(entry);
        }

        // Sắp xếp: BETTING_NOW trước, sau đó RECENT_BETTOR, rồi offline cuối
        result.sort((a, b) -> {
            int aScore = scoreEntry(a);
            int bScore = scoreEntry(b);
            return Integer.compare(bScore, aScore); // cao trước
        });

        return result;
    }

    private int scoreEntry(Map<String, Object> e) {
        boolean online = Boolean.TRUE.equals(e.get("online"));
        boolean betting = "BETTING_NOW".equals(e.get("activityStatus"));
        if (betting && online) return 3;
        if (betting) return 2;
        if (online) return 1;
        return 0;
    }

    private Map<String, Object> readJackpotConfigMap() {
        String pool = getSettingValueOrDefault(AppSetting.XOC_DIA_JACKPOT_POOL, "295320203");
        String minPool = getSettingValueOrDefault(AppSetting.XOC_DIA_JACKPOT_MIN_POOL, "100000000");
        String triggerMode = getSettingValueOrDefault(AppSetting.XOC_DIA_JACKPOT_TRIGGER_MODE, "AUTO");
        String autoRate = getSettingValueOrDefault(AppSetting.XOC_DIA_JACKPOT_AUTO_RATE, "0.001");
        String targetDoor = getSettingValueOrDefault(AppSetting.XOC_DIA_JACKPOT_TARGET_DOOR, "RANDOM");
        String winnerMode = getSettingValueOrDefault(AppSetting.XOC_DIA_JACKPOT_WINNER_MODE, "ALL_BETTOR_SHARE");
        String targetUser = getSettingValueOrDefault(AppSetting.XOC_DIA_JACKPOT_TARGET_USER, "");
        String lastWon = getSettingValueOrDefault(AppSetting.XOC_DIA_JACKPOT_LAST_WON, "");
        String threshold = getSettingValueOrDefault(AppSetting.XOC_DIA_JACKPOT_THRESHOLD, "200000000");
        String winMode = getSettingValueOrDefault(AppSetting.XOC_DIA_JACKPOT_WIN_MODE, "FULL_POOL");
        String winValue = getSettingValueOrDefault(AppSetting.XOC_DIA_JACKPOT_WIN_VALUE, "100");
        String requireBet = getSettingValueOrDefault(AppSetting.XOC_DIA_JACKPOT_REQUIRE_BET, "false");
        String feeRate = getSettingValueOrDefault(AppSetting.XOC_DIA_JACKPOT_FEE_RATE, "0.01");

        java.util.Map<String, Object> out = new java.util.LinkedHashMap<>();
        out.put("pool", Long.parseLong(pool.replaceAll("[^0-9]", "").isEmpty() ? "295320203" : pool.replaceAll("[^0-9]", "")));
        out.put("minPool", Long.parseLong(minPool.replaceAll("[^0-9]", "").isEmpty() ? "100000000" : minPool.replaceAll("[^0-9]", "")));
        out.put("triggerMode", triggerMode);
        out.put("autoRate", Double.parseDouble(autoRate.replaceAll("[^0-9.]", "").isEmpty() ? "0.001" : autoRate.replaceAll("[^0-9.]", "")));
        out.put("targetDoor", targetDoor);
        out.put("winnerMode", winnerMode);
        out.put("targetUser", targetUser);
        out.put("lastWon", lastWon);
        out.put("threshold", Long.parseLong(threshold.replaceAll("[^0-9]", "").isEmpty() ? "200000000" : threshold.replaceAll("[^0-9]", "")));
        out.put("winMode", winMode);
        out.put("winValue", winValue);
        out.put("requireBet", requireBet);
        out.put("feeRate", feeRate);
        return out;
    }

    private String getSettingValueOrDefault(String key, String defaultValue) {
        try {
            AppSettingResponse res = service.get(key);
            if (res != null && res.value() != null && !res.value().isBlank()) {
                return res.value().trim();
            }
        } catch (Exception ignored) {
        }
        return defaultValue;
    }
}
