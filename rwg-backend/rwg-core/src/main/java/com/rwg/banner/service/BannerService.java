package com.rwg.banner.service;

import com.rwg.banner.domain.Banner;
import com.rwg.banner.domain.BannerMediaType;
import com.rwg.banner.domain.BannerPlacement;
import com.rwg.banner.domain.BannerRepository;
import com.rwg.banner.dto.BannerLimitsResponse;
import com.rwg.banner.dto.BannerResponse;
import com.rwg.common.ApiException;
import com.rwg.common.ErrorCode;
import com.rwg.common.PageResponse;
import com.rwg.config.MediaProperties;
import com.rwg.media.service.MediaStorageService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Service quản lý Banner Video/Ảnh quảng cáo Trang chủ và ảnh khuyến mãi khung chat.
 *
 * Hai khu dùng CHUNG lớp này vì mọi quy tắc lưu trữ đều giống nhau — kiểm chữ ký byte,
 * trần dung lượng, sinh tên UUID, xoá kèm tệp. Chỗ khác nhau duy nhất là
 * {@link BannerPlacement}, và nó được truyền tường minh vào từng phương thức chứ không
 * suy đoán từ loại tệp: một ảnh JPG có thể thuộc cả hai khu.
 */
@Service
public class BannerService {

    /**
     * Độ dài tối đa của tiêu đề, khứp cột {@code title VARCHAR(255)}.
     *
     * CẮT thay vì báo lỗi: tiêu đề giờ suy ra tự động từ tên tệp, nên một tên tệp dài
     * bất thường không phải lỗi của người vận hành và không nên chặn việc tải lên.
     */
    private static final int MAX_TITLE_LENGTH = 255;

    private final BannerRepository bannerRepository;
    private final MediaStorageService mediaStorageService;
    private final MediaProperties mediaProperties;

    public BannerService(BannerRepository bannerRepository,
                         MediaStorageService mediaStorageService,
                         MediaProperties mediaProperties) {
        this.bannerRepository = bannerRepository;
        this.mediaStorageService = mediaStorageService;
        this.mediaProperties = mediaProperties;
    }

    /** Lấy danh sách banner đang ACTIVE cho Trang chủ người chơi. */
    @Transactional(readOnly = true)
    public List<BannerResponse> listActiveBanners() {
        return bannerRepository
                .findByPlacementAndIsActiveTrueOrderBySortOrderAscCreatedAtDesc(BannerPlacement.HOME_CAROUSEL)
                .stream()
                .map(BannerResponse::from)
                .toList();
    }

    /**
     * Ảnh khuyến mãi hiện hành của khung chat.
     *
     * TRẢ VỀ ĐÚNG MỘT ảnh — bản ACTIVE đầu tiên theo thứ tự hiển thị — vì khung chat chỉ
     * gửi một ảnh. Chọn ở tầng server chứ không trả cả danh sách để frontend tự lọc: hai
     * nơi cùng quyết định "ảnh nào đang dùng" thì trang quản trị và khung chat sẽ hiện
     * hai ảnh khác nhau ngay lần đầu logic lệch.
     *
     * {@code Optional.empty()} khi chưa cấu hình ảnh nào. Controller đổi thành 204 để
     * frontend biết dùng ảnh dự phòng, thay vì nhận 404 trông như lỗi hệ thống.
     */
    @Transactional(readOnly = true)
    public Optional<BannerResponse> chatPromo() {
        return bannerRepository
                .findByPlacementAndIsActiveTrueOrderBySortOrderAscCreatedAtDesc(BannerPlacement.CHAT_PROMO)
                .stream()
                .findFirst()
                .map(BannerResponse::from);
    }

    /**
     * Giới hạn hiện hành + số banner đang có của MỘT khu.
     *
     * Đếm bằng {@code countByPlacement()} chứ không đếm từ danh sách đã tải về: danh sách
     * có phân trang nên trang đầu chỉ có 10 bản ghi, và với trần cao hơn 10 thì con số sẽ
     * lệch.
     */
    @Transactional(readOnly = true)
    public BannerLimitsResponse limits(BannerPlacement placement) {
        return new BannerLimitsResponse(
                maxCountFor(placement),
                bannerRepository.countByPlacement(placement),
                mediaProperties.bannerMaxImageBytes(),
                mediaProperties.bannerMaxVideoBytes()
        );
    }

    /** Tra cứu danh sách banner của một khu (dùng cho Admin). */
    @Transactional(readOnly = true)
    public PageResponse<BannerResponse> listAllBanners(BannerPlacement placement, Pageable pageable) {
        Page<Banner> page = bannerRepository.findByPlacementOrderBySortOrderAscCreatedAtDesc(placement, pageable);
        return PageResponse.from(page.map(BannerResponse::from));
    }

    /**
     * Admin tải tệp media lên và tạo banner mới.
     *
     * {@code title} là TUỲ CHỌN: để trống thì lấy tên tệp bỏ đuôi. Biểu mẫu tải lên ở
     * khu quản trị chỉ còn hai trường (tệp + thứ tự), mà cột {@code title} lại NOT NULL.
     * Tiêu đề này chỉ hiện trong danh sách quản trị và thuộc tính {@code aria-label} của
     * banner — người chơi không đọc thấy nó.
     */
    @Transactional
    public BannerResponse createBanner(MultipartFile file,
                                       BannerPlacement placement,
                                       String title,
                                       String linkUrl,
                                       Integer sortOrder) {
        // TRẦN SỐ LƯỢNG KIỂM TRƯỚC KHI GHI ĐĨA, không phải sau.
        //
        // {@code mediaStorageService.store} ghi tệp xuống đĩa NGAY khi được gọi. Nếu kiểm
        // trần sau đó thì tệp vượt trần vẫn nằm lại trên đĩa rồi mới báo lỗi, để lại một
        // tệp rác không có bản ghi nào trỏ tới và không ai biết để dọn.
        //
        // ĐẾM THEO KHU: xem chú thích {@code countByPlacement}.
        long existing = bannerRepository.countByPlacement(placement);
        int maxCount = maxCountFor(placement);
        if (existing >= maxCount) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR,
                    "Đã đạt số banner tối đa (" + maxCount + "). Xoá một banner cũ trước khi tải thêm.",
                    Map.of("maxCount", maxCount, "currentCount", existing),
                    "error.banner.max_count", String.valueOf(maxCount));
        }

        MediaStorageService.StoredMediaResult storedMedia = mediaStorageService.store(file);

        // KHUNG CHAT KHÔNG PHÁT ĐƯỢC VIDEO: bong bóng chat vẽ ảnh bằng thẻ <img>. Một
        // video tải vào khu này sẽ thành ô hỏng cho MỌI khách mở chat, và người vận hành
        // không thấy gì bất thường ở trang quản trị. Chặn ở đây thay vì chỉ lọc `accept`
        // ở trình duyệt — thuộc tính đó là gợi ý, không phải rào chắn.
        if (placement == BannerPlacement.CHAT_PROMO
                && storedMedia.mediaType() != BannerMediaType.IMAGE) {
            // Tệp đã ghi xuống đĩa ở dòng trên, phải dọn trước khi ném lỗi.
            mediaStorageService.deleteByPublicUrl(storedMedia.publicUrl());
            throw new ApiException(ErrorCode.VALIDATION_ERROR,
                    "Ảnh khuyến mãi khung chat chỉ nhận tệp ảnh, không nhận video", null,
                    "error.banner.chat_promo.image_only");
        }

        int order = sortOrder != null ? sortOrder : 0;

        Banner banner = new Banner(
                resolveTitle(title, file.getOriginalFilename()),
                placement,
                storedMedia.mediaType(),
                storedMedia.publicUrl(),
                linkUrl != null && !linkUrl.isBlank() ? linkUrl.trim() : null,
                order
        );

        Banner saved = bannerRepository.save(banner);
        return BannerResponse.from(saved);
    }

    /** Trần số lượng của khu tương ứng. */
    private int maxCountFor(BannerPlacement placement) {
        return placement == BannerPlacement.CHAT_PROMO
                ? mediaProperties.chatPromoMaxCount()
                : mediaProperties.bannerMaxCount();
    }

    /**
     * Tiêu đề do người dùng đặt, hoặc suy từ tên tệp nếu để trống.
     *
     * Luôn trả chuỗi KHÁC RỖNG vì cột {@code title} là NOT NULL. Trường hợp biên:
     * tệp tên {@code ".mp4"} (chỉ có đuôi) sẽ cho phần tên rỗng, lúc đó dùng "Banner".
     */
    private static String resolveTitle(String title, String originalFilename) {
        if (title != null && !title.isBlank()) {
            return truncate(title.trim());
        }

        String name = Objects.requireNonNullElse(originalFilename, "").trim();
        int dot = name.lastIndexOf('.');
        if (dot > 0) {
            name = name.substring(0, dot);
        }
        return name.isBlank() ? "Banner" : truncate(name);
    }

    private static String truncate(String value) {
        return value.length() <= MAX_TITLE_LENGTH ? value : value.substring(0, MAX_TITLE_LENGTH);
    }

    /** Admin bật / tắt trạng thái hiển thị của Banner. */
    @Transactional
    public BannerResponse updateStatus(String id, boolean active) {
        Banner banner = bannerRepository.findById(id)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Không tìm thấy banner với ID: " + id));
        banner.setActive(active);
        Banner saved = bannerRepository.save(banner);
        return BannerResponse.from(saved);
    }

    /** Admin xoá banner và xoá file media đĩa. */
    @Transactional
    public void deleteBanner(String id) {
        Banner banner = bannerRepository.findById(id)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Không tìm thấy banner với ID: " + id));
        String mediaUrl = banner.getMediaUrl();
        bannerRepository.delete(banner);
        mediaStorageService.deleteByPublicUrl(mediaUrl);
    }
}
