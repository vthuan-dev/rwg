package com.rwg.banner.api;

import com.rwg.banner.domain.BannerPlacement;
import com.rwg.banner.dto.BannerLimitsResponse;
import com.rwg.banner.dto.BannerResponse;
import com.rwg.banner.dto.UpdateBannerStatusRequest;
import com.rwg.banner.service.BannerService;
import com.rwg.common.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * Controller Admin quản lý Banner Video/Ảnh quảng cáo Trang chủ.
 */
@RestController
@RequestMapping("/api/v1/admin/banners")
@Tag(name = "Admin Banner", description = "Tải lên, cấu hình và quản lý banner video/ảnh trang chủ")
@SecurityRequirement(name = "bearerAuth")
public class AdminBannerController {

    private final BannerService bannerService;

    public AdminBannerController(BannerService bannerService) {
        this.bannerService = bannerService;
    }

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Tải tệp Video (MP4/WebM) hoặc Ảnh (PNG/JPG/WebP) làm banner mới")
    public BannerResponse uploadBanner(
            @RequestPart("file") MultipartFile file,
            // MẶC ĐỊNH HOME_CAROUSEL để không phá client cũ: trước khi có khu ảnh chat thì
            // mọi lần tải lên đều là banner trang chủ.
            @RequestParam(value = "placement", required = false, defaultValue = "HOME_CAROUSEL")
            BannerPlacement placement,
            // TUỲ CHỌN: biểu mẫu ở khu quản trị chỉ còn tệp + thứ tự. Để trống thì
            // BannerService suy tiêu đề từ tên tệp (cột title là NOT NULL).
            @RequestParam(value = "title", required = false) String title,
            @RequestParam(value = "linkUrl", required = false) String linkUrl,
            @RequestParam(value = "sortOrder", required = false, defaultValue = "0") Integer sortOrder
    ) {
        return bannerService.createBanner(file, placement, title, linkUrl, sortOrder);
    }

    /**
     * Giịi hạn hiện hành để khu quản trị hiển thị và kiểm trước.
     *
     * KHÔNG để frontend gán cứng số 4: nếu gán cứng thì khi đổi {@code rwg.media.banner-max-count}
     * ở backend, frontend vẫn tắt nút theo con số cũ — hai nguồn sự thật cho cùng một
     * giới hạn, và người vận hành thấy nút bị tắt mà server vẫn nhận thêm.
     *
     * Trần KHÁC NHAU theo khu, nên phải hỏi theo {@code placement}.
     */
    @GetMapping("/limits")
    @Operation(summary = "Giới hạn số lượng và dung lượng banner của một khu")
    public BannerLimitsResponse limits(
            @RequestParam(value = "placement", required = false, defaultValue = "HOME_CAROUSEL")
            BannerPlacement placement
    ) {
        return bannerService.limits(placement);
    }

    @GetMapping
    @Operation(summary = "Tra cứu danh sách banner của một khu (phân trang)")
    public PageResponse<BannerResponse> listBanners(
            @RequestParam(value = "placement", required = false, defaultValue = "HOME_CAROUSEL")
            BannerPlacement placement,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        Pageable pageable = PageRequest.of(page, size);
        return bannerService.listAllBanners(placement, pageable);
    }

    @PatchMapping("/{id}/status")
    @Operation(summary = "Bật / Tắt trạng thái hiển thị của Banner")
    public BannerResponse updateStatus(
            @PathVariable("id") String id,
            @Valid @RequestBody UpdateBannerStatusRequest request
    ) {
        return bannerService.updateStatus(id, request.active());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Xoá banner và xoá file media đĩa")
    public void deleteBanner(@PathVariable("id") String id) {
        bannerService.deleteBanner(id);
    }
}
