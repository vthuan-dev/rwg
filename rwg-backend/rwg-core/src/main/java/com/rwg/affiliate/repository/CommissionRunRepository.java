package com.rwg.affiliate.repository;

import com.rwg.affiliate.domain.CommissionRun;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Repository
public interface CommissionRunRepository extends JpaRepository<CommissionRun, UUID> {

    /**
     * Số lượt hoa hồng đã chạy cho một đại lý.
     *
     * Dùng khi quyết định có xóa hẳn được hay không. Một lượt hoa hồng là một khoản tiền đã
     * chi hoặc đang giữ, nên nó thuộc sổ sách tài chính.
     */
    long countByAgentId(UUID agentId);

    /** Xoá mọi lượt hoa hồng của một đại lý. Chỉ dùng cho tài khoản SẠCH. */
    void deleteByAgentId(UUID agentId);

    /**
     * Fast-path chống chi trùng. Chốt thật vẫn là
     * uq_commission_runs_agent_period_level ở tầng DB — hai job song song có thể
     * cùng vượt qua check này, nhưng chỉ 1 insert thành công.
     */
    boolean existsByAgentIdAndPeriodDateAndLevel(UUID agentId, LocalDate periodDate, short level);

    /** Lịch sử chi hoa hồng cho khu quản trị; mọi filter optional. */
    @Query("select c from CommissionRun c where "
            + "(:agentId is null or c.agentId = :agentId) and "
            + "c.periodDate >= :from and c.periodDate <= :to")
    Page<CommissionRun> searchForAdmin(@Param("agentId") UUID agentId,
                                       @Param("from") LocalDate from,
                                       @Param("to") LocalDate to,
                                       Pageable pageable);

    /** Tổng hoa hồng đã chi trong khoảng — phục vụ dashboard. */
    @Query("select coalesce(sum(c.amount), 0) from CommissionRun c "
            + "where c.periodDate >= :from and c.periodDate <= :to")
    BigDecimal sumAmountBetween(@Param("from") LocalDate from, @Param("to") LocalDate to);

    /**
     * Tổng hoa hồng một đại lý đã nhận TỪ ĐẦU — trang tổng quan của chính người chơi.
     * Không giới hạn khoảng ngày vì đây là con số luỹ kế trọn đời, và mỗi đại lý chỉ
     * có tối đa 2 chứng từ mỗi ngày (một cấp một dòng) nên phạm vi quét rất nhỏ.
     */
    @Query("select coalesce(sum(c.amount), 0) from CommissionRun c where c.agentId = :agentId")
    BigDecimal sumAmountByAgent(@Param("agentId") UUID agentId);
}
