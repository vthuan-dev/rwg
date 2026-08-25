package com.rwg.affiliate.repository;

import com.rwg.affiliate.domain.ReferralCode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ReferralCodeRepository extends JpaRepository<ReferralCode, String> {

    /**
     * Xoá mã giới thiệu của một người.
     *
     * Chỉ dùng trên đường xóa hẳn một tài khoản SẠCH. Mã được giải phóng là có thể cấp lại cho
     * người khác — không sao với tài khoản chưa từng giới thiệu ai, nhưng sẽ làm sai quy gán
     * hoa hồng nếu áp cho tài khoản đã có tuyến dưới.
     */
    void deleteByUserId(UUID userId);

    Optional<ReferralCode> findByUserId(UUID userId);

    boolean existsByCode(String code);
}
