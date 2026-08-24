package com.rwg.payment.repository;

import com.rwg.payment.domain.PaymentOrder;
import com.rwg.payment.domain.PaymentOrderId;
import com.rwg.payment.domain.PaymentStatus;
import com.rwg.payment.domain.PaymentType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository lệnh thanh toán. id là UUID thực tế unique nên tra theo id (một phần của
 * PK composite) bằng findFirstById; webhook tra theo provider_txn_id.
 */
@Repository
public interface PaymentOrderRepository extends JpaRepository<PaymentOrder, PaymentOrderId> {

    Optional<PaymentOrder> findFirstById(UUID id);

    /**
     * Nạp NHIỀU lệnh theo mã trong một truy vấn.
     *
     * Cần cho hộp thư hỗ trợ: một trang lịch sử chat có thể chứa nhiều thẻ duyệt lệnh
     * rút, và tra từng thẻ là một lượt gọi DB cho mỗi thẻ — trên màn hình được mở lại
     * mỗi lần nhân sự chuyển luồng.
     *
     * Lọc theo {@code id} chứ không phải PK đầy đủ {@code (id, created_at)}: id là UUID
     * nên thực tế đã unique, và bên gọi chỉ có mã lệnh chứ không lưu kèm thời điểm tạo.
     */
    List<PaymentOrder> findByIdIn(Collection<UUID> ids);

    Optional<PaymentOrder> findFirstByProviderTxnId(String providerTxnId);

    Optional<PaymentOrder> findFirstByIdempotencyKey(String idempotencyKey);

    /**
     * Lệnh nạp/rút của MỘT người chơi, mới nhất trước — cho màn hình lịch sử của họ.
     *
     * VÌ SAO KHÔNG DÙNG LẠI {@link #searchForAdmin}: hàm đó BẮT BUỘC truyền {@code type}
     * vì khu quản trị tách riêng màn nạp và màn rút. Người chơi thì cần xem cả hai loại
     * lẫn nhau theo trình tự thời gian, giống một sổ giao dịch.
     */
    Page<PaymentOrder> findByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);

    /**
     * Như trên nhưng chỉ MỘT loại lệnh — cho hai màn lịch sử nạp và rút riêng.
     *
     * PHẢI lọc ở đây chứ không lọc sau khi lấy về: nếu lấy một trang 20 lệnh lẫn cả nạp và
     * rút rồi bỏ bớt ở phía client, mỗi trang sẽ còn số dòng khác nhau và tổng số trang
     * tính ra không còn đúng với những gì người dùng thấy.
     */
    Page<PaymentOrder> findByUserIdAndTypeOrderByCreatedAtDesc(
            UUID userId, PaymentType type, Pageable pageable);

    /** Tổng tiền đã rút trong ngày (loại trừ VOIDED) — phục vụ hạn mức rút theo ngày. */
    @Query("select coalesce(sum(o.amount), 0) from PaymentOrder o "
            + "where o.userId = :userId and o.type = :type and o.status <> :excluded "
            + "and o.createdAt >= :start")
    BigDecimal sumAmountSince(@Param("userId") UUID userId,
                              @Param("type") PaymentType type,
                              @Param("excluded") PaymentStatus excluded,
                              @Param("start") Instant start);

    /**
     * Chuyển trạng thái NGUYÊN TỬ bằng 1 câu UPDATE điều kiện (fix review C2):
     * {@code set status = :to where id = :id and status = :from}. Trả số dòng bị
     * ảnh hưởng (1 = thắng, 0 = lệnh không tồn tại HOẶC đã bị luồng khác chuyển).
     * 2 thao tác approve/reject song song chỉ đúng 1 thao tác thắng.
     */
    @Modifying(clearAutomatically = true)
    @Query("update PaymentOrder o set o.status = :to, o.updatedAt = :now "
            + "where o.id = :id and o.status = :from")
    int transitionStatus(@Param("id") UUID id,
                         @Param("from") PaymentStatus from,
                         @Param("to") PaymentStatus to,
                         @Param("now") Instant now);

    /**
     * Tra soát lệnh nạp/rút cho khu quản trị. type BẮT BUỘC (tách rõ màn deposit và
     * withdrawal); status/userId OPTIONAL (null = bỏ qua filter). Khoảng thời gian
     * nửa mở [from, to) để không đếm trùng biên khi phân trang theo ngày.
     */
    @Query("select o from PaymentOrder o where "
            + "o.type = :type and "
            + "(:status is null or o.status = :status) and "
            + "(:userId is null or o.userId = :userId) and "
            + "o.createdAt >= :from and o.createdAt < :to")
    Page<PaymentOrder> searchForAdmin(@Param("type") PaymentType type,
                                      @Param("status") PaymentStatus status,
                                      @Param("userId") UUID userId,
                                      @Param("from") Instant from,
                                      @Param("to") Instant to,
                                      Pageable pageable);

    /**
     * Như {@link #searchForAdmin} nhưng nhận NHIỀU trạng thái cùng lúc.
     *
     * Cần cho trang lịch sử rút tiền: nó phải hiện CẢ lệnh đã duyệt và đã từ chối trong một
     * bảng xếp theo thời gian. Gọi hai lần rồi trộn ở tầng trên sẽ làm phân trang sai — trang
     * 1 của mỗi truy vấn ghép lại không phải trang 1 của tập hợp chung.
     *
     * {@code statuses} PHẢI không rỗng. JPQL {@code in (:x)} với x null sinh SQL không hợp lệ,
     * nên nơi gọi truyền đủ mọi trạng thái khi không muốn lọc, thay vì truyền null.
     */
    @Query("select o from PaymentOrder o where "
            + "o.type = :type and "
            + "o.status in :statuses and "
            + "(:userId is null or o.userId = :userId) and "
            + "o.createdAt >= :from and o.createdAt < :to")
    Page<PaymentOrder> searchForAdminByStatuses(@Param("type") PaymentType type,
                                                @Param("statuses") Collection<PaymentStatus> statuses,
                                                @Param("userId") UUID userId,
                                                @Param("from") Instant from,
                                                @Param("to") Instant to,
                                                Pageable pageable);

    /** Đếm lệnh theo loại + trạng thái (badge "chờ duyệt" trên dashboard admin). */
    long countByTypeAndStatus(PaymentType type, PaymentStatus status);

    /** Tổng tiền của MỘT user theo loại + trạng thái — dùng cho màn chi tiết user. */
    @Query("select coalesce(sum(o.amount), 0) from PaymentOrder o "
            + "where o.userId = :userId and o.type = :type and o.status = :status")
    BigDecimal sumAmountByUserAndTypeAndStatus(@Param("userId") UUID userId,
                                               @Param("type") PaymentType type,
                                               @Param("status") PaymentStatus status);

    /** Tổng tiền TOÀN HỆ THỐNG theo loại + trạng thái trong khoảng — dùng cho báo cáo. */
    @Query("select coalesce(sum(o.amount), 0) from PaymentOrder o "
            + "where o.type = :type and o.status = :status "
            + "and o.createdAt >= :from and o.createdAt < :to")
    BigDecimal sumAmountByTypeAndStatus(@Param("type") PaymentType type,
                                        @Param("status") PaymentStatus status,
                                        @Param("from") Instant from,
                                        @Param("to") Instant to);

    /** Đếm lệnh của MỘT user theo loại + trạng thái (vd lệnh rút đang chờ duyệt). */
    long countByUserIdAndTypeAndStatus(UUID userId, PaymentType type, PaymentStatus status);

    /**
     * Tổng tiền của MỘT user theo loại + trạng thái TRONG MỘT KHOẢNG — sổ sách tháng.
     *
     * Khác {@link #sumAmountByUserAndTypeAndStatus} ở chỗ có giới hạn thời gian: hàm kia
     * tính toàn bộ lịch sử, dùng cho màn chi tiết người dùng.
     *
     * LƯU Ý VỀ TRẠNG THÁI HOÀN TẤT — KHÁC NHAU GIỮA NẠP VÀ RÚT:
     * nạp hoàn tất là {@code SUCCESS}, rút hoàn tất là {@code SETTLED}. Truyền sai sẽ ra
     * tổng bằng 0 mà KHÔNG có lỗi nào — không được đoán, xem
     * {@code AdminDashboardService}.
     *
     * Khoảng nửa mở {@code [from, to)}.
     */
    @Query("select coalesce(sum(o.amount), 0) from PaymentOrder o "
            + "where o.userId = :userId and o.type = :type and o.status = :status "
            + "and o.createdAt >= :from and o.createdAt < :to")
    BigDecimal sumAmountByUserTypeStatusInRange(@Param("userId") UUID userId,
                                                @Param("type") PaymentType type,
                                                @Param("status") PaymentStatus status,
                                                @Param("from") Instant from,
                                                @Param("to") Instant to);

    /** Một dòng tổng tiền theo từng người chơi. */
    interface PlayerAmount {
        UUID getUserId();
        BigDecimal getTotal();
    }

    /**
     * Tổng tiền theo loại + trạng thái, nhóm theo từng người chơi — bảng tổng quan sổ sách.
     *
     * Nhắc lại cái bẫy: nạp hoàn tất là {@code SUCCESS}, rút hoàn tất là
     * {@code SETTLED}. Truyền sai sẽ ra tổng bằng 0 mà không có lỗi nào.
     */
    @Query("select o.userId as userId, coalesce(sum(o.amount), 0) as total "
            + "from PaymentOrder o "
            + "where o.type = :type and o.status = :status "
            + "and o.createdAt >= :from and o.createdAt < :to "
            + "group by o.userId")
    List<PlayerAmount> sumByPlayerTypeStatus(@Param("type") PaymentType type,
                                             @Param("status") PaymentStatus status,
                                             @Param("from") Instant from,
                                             @Param("to") Instant to);
}
