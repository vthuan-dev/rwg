package com.rwg.banner.api;

import com.rwg.banner.dto.BannerResponse;
import com.rwg.banner.service.BannerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
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
}
