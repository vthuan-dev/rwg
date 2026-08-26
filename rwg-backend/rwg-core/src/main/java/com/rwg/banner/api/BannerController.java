package com.rwg.banner.api;

import com.rwg.banner.dto.BannerResponse;
import com.rwg.banner.service.BannerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Controller phục vụ hiển thị Banner Trang chủ cho Người chơi (User).
 */
@RestController
@RequestMapping("/api/v1/banners")
@Tag(name = "Banner", description = "Xem banner video/ảnh quảng cáo trang chủ người chơi")
public class BannerController {

    private final BannerService bannerService;

    public BannerController(BannerService bannerService) {
        this.bannerService = bannerService;
    }

    @GetMapping("/active")
    @Operation(summary = "Danh sách Banner Video / Ảnh đang hiển thị tại Trang chủ")
    public List<BannerResponse> activeBanners() {
        return bannerService.listActiveBanners();
    }

    /**
     * Ảnh khuyến mãi gửi kèm lời chào trong khung Trò chuyện trực tiếp.
     *
     * TRẢ 204 KHI CHƯA CẤU HÌNH, không phải 404: "chưa ai đặt ảnh nào" là trạng thái
     * hợp lệ và thường gặp, còn 404 trông như đường dẫn sai hoặc hệ thống lỗi — frontend
     * sẽ ghi cảnh báo vào console mỗi lần khách mở chat. Với 204 thì frontend im lặng
     * dùng ảnh dự phòng đóng kèm trong bản build.
     *
     * KHÔNG cần xác thực (xem {@code SecurityConfig}): đây là tài liệu quảng bá gửi cho
     * mọi khách, khác hoàn toàn ảnh đính kèm chat — thứ đó là biên lai và giấy tờ cá
     * nhân nên vẫn nằm sau {@code /api/v1/chat/attachments/} có kiểm tra quyền.
     */
    @GetMapping("/chat-promo")
    @Operation(summary = "Ảnh khuyến mãi hiện hành của khung chat hỗ trợ")
    public ResponseEntity<BannerResponse> chatPromo() {
        return bannerService.chatPromo()
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }
}
