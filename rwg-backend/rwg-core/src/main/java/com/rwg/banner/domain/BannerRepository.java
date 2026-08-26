package com.rwg.banner.domain;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Spring Data JPA Repository cho {@link Banner}.
 *
 * MỌI phương thức truy vấn ở đây đều BẮT BUỘC nhận {@link BannerPlacement}. Hai phương
 * thức cũ không có tham số đó đã bị XOÁ thay vì giữ lại cho tương thích: còn tồn tại
 * thì một lần gọi nhầm là ảnh khuyến mãi chat lọt lên carousel trang chủ, và trình biên
 * dịch không cản được. Xoá đi thì mọi nơi gọi sai sẽ báo lỗi biên dịch ngay — đó chính
 * là điều mong muốn.
 */
@Repository
public interface BannerRepository extends JpaRepository<Banner, String> {

    /** Banner đang ACTIVE của một khu, sắp theo thứ tự ưu tiên hiển thị. */
    List<Banner> findByPlacementAndIsActiveTrueOrderBySortOrderAscCreatedAtDesc(BannerPlacement placement);

    /** Toàn bộ banner của một khu, có phân trang (dùng cho Admin). */
    Page<Banner> findByPlacementOrderBySortOrderAscCreatedAtDesc(BannerPlacement placement, Pageable pageable);

    /**
     * Số banner hiện có của một khu.
     *
     * ĐẾM THEO KHU, không phải {@code count()} toàn bảng: trần sinh ra để giới hạn số
     * tệp của TỪNG khu. Đếm cả bảng thì tải đủ 4 banner trang chủ là hết chỗ cho ảnh
     * chat, dù hai thứ không liên quan gì đến nhau.
     *
     * Đếm CẢ banner đang tắt: tệp của banner tắt vẫn chiếm chỗ trên đĩa y như banner
     * đang bật, nên nếu chỉ đếm ACTIVE thì tắt hết đi là tải lên được vô hạn.
     */
    long countByPlacement(BannerPlacement placement);
}
