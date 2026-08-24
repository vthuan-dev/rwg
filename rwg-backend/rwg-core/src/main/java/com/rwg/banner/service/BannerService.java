package com.rwg.banner.service;

import com.rwg.banner.domain.Banner;
import com.rwg.banner.domain.BannerRepository;
import com.rwg.banner.dto.BannerResponse;
import com.rwg.common.ApiException;
import com.rwg.common.ErrorCode;
import com.rwg.common.PageResponse;
import com.rwg.media.service.MediaStorageService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * Service quản lý Banner Video/Ảnh quảng cáo Trang chủ.
 */
@Service
public class BannerService {

    private final BannerRepository bannerRepository;
    private final MediaStorageService mediaStorageService;

    public BannerService(BannerRepository bannerRepository, MediaStorageService mediaStorageService) {
        this.bannerRepository = bannerRepository;
        this.mediaStorageService = mediaStorageService;
    }

    /** Lấy danh sách banner đang ACTIVE cho Trang chủ người chơi. */
    @Transactional(readOnly = true)
    public List<BannerResponse> listActiveBanners() {
        return bannerRepository.findByIsActiveTrueOrderBySortOrderAscCreatedAtDesc()
                .stream()
                .map(BannerResponse::from)
                .toList();
    }

    /** Tra cứu toàn bộ danh sách banner (dùng cho Admin). */
    @Transactional(readOnly = true)
    public PageResponse<BannerResponse> listAllBanners(Pageable pageable) {
        Page<Banner> page = bannerRepository.findAllByOrderBySortOrderAscCreatedAtDesc(pageable);
        return PageResponse.from(page.map(BannerResponse::from));
    }

    /** Admin upload file media và tạo banner mới. */
    @Transactional
    public BannerResponse createBanner(MultipartFile file, String title, String linkUrl, Integer sortOrder) {
        String titleTrimmed = title != null ? title.trim() : "";
        if (titleTrimmed.isBlank()) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "Tiêu đề banner không được để rỗng");
        }

        MediaStorageService.StoredMediaResult storedMedia = mediaStorageService.store(file);
        int order = sortOrder != null ? sortOrder : 0;

        Banner banner = new Banner(
                titleTrimmed,
                storedMedia.mediaType(),
                storedMedia.publicUrl(),
                linkUrl != null ? linkUrl.trim() : null,
                order
        );

        Banner saved = bannerRepository.save(banner);
        return BannerResponse.from(saved);
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
