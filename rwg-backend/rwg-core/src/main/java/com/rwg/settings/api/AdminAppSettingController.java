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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
}
