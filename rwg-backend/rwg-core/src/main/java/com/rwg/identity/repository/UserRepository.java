package com.rwg.identity.repository;

import com.rwg.identity.domain.User;
import com.rwg.identity.domain.UserRole;
import com.rwg.identity.domain.UserStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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
     */
    @Query("select u from User u where "
            + "(:status is null or u.status = :status) and "
            + "(:role is null or u.role = :role) and "
            + "(:keyword is null or lower(u.username) like :keyword "
            + "or lower(u.email) like :keyword)")
    Page<User> searchForAdmin(@Param("status") UserStatus status,
                              @Param("role") UserRole role,
                              @Param("keyword") String keyword,
                              Pageable pageable);

    long countByStatus(UserStatus status);
}
