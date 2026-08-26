package com.rwg.banner.dto;

import com.rwg.banner.domain.Banner;
import com.rwg.banner.domain.BannerMediaType;
import com.rwg.banner.domain.BannerPlacement;

import java.time.Instant;

/**
 * Payload thông tin Banner trả về cho Client/Admin.
 */
public record BannerResponse(
        String id,
        String title,
        BannerPlacement placement,
        BannerMediaType mediaType,
        String mediaUrl,
        String linkUrl,
        boolean isActive,
        int sortOrder,
        Instant createdAt
) {
    public static BannerResponse from(Banner banner) {
        return new BannerResponse(
                banner.getId(),
                banner.getTitle(),
                banner.getPlacement(),
                banner.getMediaType(),
                banner.getMediaUrl(),
                banner.getLinkUrl(),
                banner.isActive(),
                banner.getSortOrder(),
                banner.getCreatedAt()
        );
    }
}
