package com.rwg.settings.api;

import com.rwg.settings.domain.AppSetting;
import com.rwg.settings.dto.AppSettingResponse;
import com.rwg.settings.service.AppSettingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
}
