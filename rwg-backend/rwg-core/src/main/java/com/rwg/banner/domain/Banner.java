package com.rwg.banner.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Entity lưu thông tin Banner / Video quảng cáo tại Trang chủ.
 */
@Entity
@Table(name = "banners")
public class Banner {

    @Id
    @Column(name = "id", length = 36, nullable = false, updatable = false)
    private String id;

    @Column(name = "title", nullable = false)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(name = "media_type", length = 20, nullable = false)
    private BannerMediaType mediaType;

    @Column(name = "media_url", length = 512, nullable = false)
    private String mediaUrl;

    @Column(name = "link_url", length = 512)
    private String linkUrl;

    @Column(name = "is_active", nullable = false)
    private boolean isActive = true;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder = 0;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Banner() {
        // JPA Required
    }

    public Banner(String title, BannerMediaType mediaType, String mediaUrl, String linkUrl, int sortOrder) {
        this.id = UUID.randomUUID().toString();
        this.title = Objects.requireNonNull(title, "title");
        this.mediaType = Objects.requireNonNull(mediaType, "mediaType");
        this.mediaUrl = Objects.requireNonNull(mediaUrl, "mediaUrl");
        this.linkUrl = linkUrl;
        this.sortOrder = sortOrder;
        this.isActive = true;
    }

    @PrePersist
    protected void onCreate() {
        if (id == null) {
            id = UUID.randomUUID().toString();
        }
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }

    public String getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public BannerMediaType getMediaType() {
        return mediaType;
    }

    public String getMediaUrl() {
        return mediaUrl;
    }

    public String getLinkUrl() {
        return linkUrl;
    }

    public void setLinkUrl(String linkUrl) {
        this.linkUrl = linkUrl;
    }

    public boolean isActive() {
        return isActive;
    }

    public void setActive(boolean active) {
        isActive = active;
    }

    public int getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(int sortOrder) {
        this.sortOrder = sortOrder;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
