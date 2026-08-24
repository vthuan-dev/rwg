package com.rwg.banner.domain;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Spring Data JPA Repository cho {@link Banner}.
 */
@Repository
public interface BannerRepository extends JpaRepository<Banner, String> {

    /** Lấy danh sách banner đang ACTIVE sắp xếp theo thứ tự ưu tiên hiển thị. */
    List<Banner> findByIsActiveTrueOrderBySortOrderAscCreatedAtDesc();

    /** Lấy tất cả banner có phân trang (dùng cho Admin). */
    Page<Banner> findAllByOrderBySortOrderAscCreatedAtDesc(Pageable pageable);
}
