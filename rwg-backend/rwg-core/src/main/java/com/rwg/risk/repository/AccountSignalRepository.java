package com.rwg.risk.repository;

import com.rwg.risk.domain.AccountSignal;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Repository dấu vết đăng ký. */
@Repository
public interface AccountSignalRepository extends JpaRepository<AccountSignal, UUID> {

    /**
     * Đếm dấu vết đăng ký của một người — dùng khi kiểm tra tài khoản có "sạch" không.
     *
     * Dấu vết thiết bị (device fingerprint, IP) là đầu mối điều tra khi có khiếu nại.
     * Tài khoản có dấu vết thì KHÔNG xóa hẳn — chỉ đóng (CLOSED) để sổ sách còn đó.
     */
    long countByUserId(UUID userId);

    /**
     * Xoá dấu vết đăng ký của một người.
     *
     * Chỉ dùng trên đường xóa hẳn một tài khoản SẠCH (chưa từng có giao dịch nào). Dấu vết
     * này dùng để đa tài khoản, nên với tài khoản có tiền thì phải giữ - đó là lúc nó có
     * giá trị nhất.
     */
    void deleteByUserId(UUID userId);

    /**
     * Các user khác cùng dấu vết thiết bị. Loại chính user đang xét ra ngay trong
     * truy vấn để nơi gọi không phải nhớ lọc.
     */
    @Query("select s from AccountSignal s "
            + "where s.deviceFingerprint = :fingerprint and s.userId <> :excludeUserId")
    List<AccountSignal> findOthersByDeviceFingerprint(@Param("fingerprint") String fingerprint,
                                                     @Param("excludeUserId") UUID excludeUserId);

    /**
     * Các user khác đăng ký từ cùng IP kể từ mốc thời gian.
     *
     * Có cửa sổ thời gian vì nếu đếm toàn bộ lịch sử thì một IP quán net dùng suốt
     * hai năm sẽ tích đủ tài khoản để mọi người đăng ký sau đó đều bị nghi.
     */
    @Query("select s from AccountSignal s "
            + "where s.registrationIp = :ip and s.userId <> :excludeUserId "
            + "and s.createdAt >= :since")
    List<AccountSignal> findOthersByIpSince(@Param("ip") String ip,
                                            @Param("excludeUserId") UUID excludeUserId,
                                            @Param("since") Instant since);
}
