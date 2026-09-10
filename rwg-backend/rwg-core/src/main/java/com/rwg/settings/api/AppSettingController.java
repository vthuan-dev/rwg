package com.rwg.settings.api;

import com.rwg.settings.domain.AppSetting;
import com.rwg.settings.dto.AppSettingResponse;
import com.rwg.settings.service.AppSettingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.Map;

/**
 * Đường đọc công khai cho các đoạn chữ cấu hình mà khách nhìn thấy.
 *
 * <h2>MỖI KHOÁ MỘT ENDPOINT RIÊNG, KHÔNG PHẢI {@code /settings/{key}} CHUNG</h2>
 * Một endpoint nhận khoá tự do và mở công khai nghĩa là bất kỳ ai cũng đọc được mọi dòng
 * trong bảng cấu hình, kể cả những khoá thêm về sau mà không ai nhớ ra là chúng đang bị
 * phơi. Liệt kê từng khoá ra endpoint riêng khiến việc mở công khai một nội dung là một
 * quyết định tường minh, phải viết thêm mã mới làm được.
 */
@RestController
@RequestMapping("/api/v1/settings")
@Tag(name = "Settings", description = "Nội dung chữ do khu quản trị cấu hình")
public class AppSettingController {

    private final AppSettingService service;

    public AppSettingController(AppSettingService service) {
        this.service = service;
    }

    /**
     * Lời chào khuyến mãi hiện trong khung Trò chuyện trực tiếp.
     *
     * KHÔNG cần xác thực (xem {@code SecurityConfig}): đây là nội dung quảng bá gửi cho
     * mọi khách mở khung chat, cùng loại với {@code /banners/chat-promo}. Bắt đăng nhập
     * chỉ làm bong bóng chào xuất hiện trễ hơn phần còn lại của hội thoại.
     */
    @GetMapping("/chat-promo-text")
    @Operation(summary = "Lời chào khuyến mãi của khung chat hỗ trợ")
    public AppSettingResponse chatPromoText() {
        return service.get(AppSetting.CHAT_PROMO_TEXT);
    }

    /**
     * Tỷ giá quy đổi tiền tệ hiện hành (mặc định USD sang VND).
     *
     * Công khai cho người chơi và các sảnh game cần quy đổi số dư ví USD sang VND.
     */
    @GetMapping("/exchange-rate")
    @Operation(summary = "Tỷ giá quy đổi tiền tệ (USD sang VND)")
    public Map<String, Object> exchangeRate() {
        AppSettingResponse setting = null;
        try {
            setting = service.get(AppSetting.EXCHANGE_RATE_USD_VND);
        } catch (Exception ignored) {
        }

        BigDecimal rate = new BigDecimal("25000");
        if (setting != null && setting.value() != null && !setting.value().isBlank()) {
            try {
                rate = new BigDecimal(setting.value().trim());
            } catch (Exception ignored) {
            }
        }

        return Map.of(
                "baseCurrency", "USD",
                "targetCurrency", "VND",
                "rate", rate,
                "formattedRate", "1 USD = " + String.format(Locale.US, "%,d", rate.longValue()) + " VND"
        );
    }

    /**
     * Cấu hình phòng Xóc Đĩa VIP (số lượng bot tham gia bàn và trạng thái bot chat).
     */
    @GetMapping("/xocdia-config")
    @Operation(summary = "Cấu hình bàn chơi Xóc Đĩa (Số bot, bot chat)")
    public Map<String, Object> xocdiaConfig() {
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

    /**
     * Thông tin quỹ Jackpot Xóc Đĩa hiện tại cho người chơi.
     */
    @GetMapping("/xocdia-jackpot")
    @Operation(summary = "Dữ liệu Jackpot Xóc Đĩa công khai")
    public Map<String, Object> xocdiaJackpot() {
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
