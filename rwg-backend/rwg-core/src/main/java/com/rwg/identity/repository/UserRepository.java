package com.rwg.identity.repository;

import com.rwg.identity.domain.User;
import com.rwg.identity.domain.UserStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByUsername(String username);

    Optional<User> findByEmailIgnoreCase(String email);

    boolean existsByUsernameIgnoreCase(String username);

    boolean existsByEmailIgnoreCase(String email);

    /**
     * Tìm kiếm user cho khu quản trị. Mỗi filter là OPTIONAL: truyền null để bỏ qua
     * (điều kiện {@code :param is null or ...}). keyword đã được service chuẩn hóa
     * thành lowercase kèm ký tự '%' trước khi gọi — repository KHÔNG tự thêm wildcard.
     *
     * {@code excludeClosed} = true ẩn tài khoản CLOSED ("đã xóa") khỏi kết quả mặc định.
     * Truyền false để hiện lại — chức năng "Được/Ẩn tài khoản đã xóa" trên giao diện.
     *
     * <h2>CHỈ TRẢ TÀI KHOẢN KHÁCH (PLAYER)</h2>
     * Điều kiện {@code u.role = PLAYER} là CỐ ĐỊNH, không phải filter tùy chọn: trang
     * "Quản lý Người dùng" là nơi xử lý khách, và các thao tác ở đó (khóa, đổi mật khẩu,
     * điều chỉnh ví, XÓA tài khoản) không được phép chạm vào tài khoản nhân sự chỉ vì
     * người vận hành bấm nhầm dòng. Lọc ở đây thay vì ở giao diện để ai gọi thẳng API
     * cũng không lấy được danh sách nhân sự.
     *
     * Hệ quả đã biết: danh sách này KHÔNG dùng để tìm tài khoản nhân sự. Việc phân quyền
     * vẫn làm qua {@code PATCH /api/v1/admin/users/{id}/role} với id biết trước.
     */
    @Query("select u from User u where "
            + "u.role = com.rwg.identity.domain.UserRole.PLAYER and "
            + "(:status is null or u.status = :status) and "
            + "(:excludeClosed = false or u.status <> com.rwg.identity.domain.UserStatus.CLOSED) and "
            + "(:keyword is null or lower(u.username) like :keyword "
            + "or lower(u.email) like :keyword)")
    Page<User> searchForAdmin(@Param("status") UserStatus status,
                              @Param("excludeClosed") boolean excludeClosed,
                              @Param("keyword") String keyword,
                              Pageable pageable);

    long countByStatus(UserStatus status);

    /** Đếm user đăng ký trong khoảng nửa mở [from, to) — dashboard admin. */
    @Query("select count(u) from User u where u.createdAt >= :from and u.createdAt < :to")
    long countRegisteredBetween(@Param("from") Instant from, @Param("to") Instant to);
}
