package com.rwg.settings.api;

import com.rwg.common.web.ClientAddresses;
import com.rwg.config.SecurityConfig;
import com.rwg.settings.domain.AppSetting;
import com.rwg.settings.dto.AppSettingResponse;
import com.rwg.settings.dto.UpdateAppSettingRequest;
import com.rwg.settings.service.AppSettingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

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

    public AdminAppSettingController(AppSettingService service) {
        this.service = service;
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
        }

        return readJackpotConfigMap();
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

        return Map.of(
                "pool", Long.parseLong(pool.replaceAll("[^0-9]", "").isEmpty() ? "295320203" : pool.replaceAll("[^0-9]", "")),
                "minPool", Long.parseLong(minPool.replaceAll("[^0-9]", "").isEmpty() ? "100000000" : minPool.replaceAll("[^0-9]", "")),
                "triggerMode", triggerMode,
                "autoRate", Double.parseDouble(autoRate.replaceAll("[^0-9.]", "").isEmpty() ? "0.001" : autoRate.replaceAll("[^0-9.]", "")),
                "targetDoor", targetDoor,
                "winnerMode", winnerMode,
                "targetUser", targetUser,
                "lastWon", lastWon
        );
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
